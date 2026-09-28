package com.kubiki.metis.sensor;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.knowledge.GraphDbReadiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of all {@link KubernetesSensor} beans.
 *
 * <p>Starts sensors after the application context is fully ready (gRPC server up,
 * all beans initialized) and stops them cleanly on shutdown. If a sensor fails
 * to start (e.g. transient Kubernetes API outage at boot), it is retried with
 * exponential backoff up to {@link #MAX_RETRY_ATTEMPTS} attempts.
 *
 * <p>If {@code metis.sensor.enabled} is {@code false}, no sensors are started.
 */
@Component
public class SensorOrchestrator implements ApplicationListener<ApplicationReadyEvent>, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SensorOrchestrator.class);

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_RETRY_DELAY_S = 2;
    /**
     * Cap to prevent {@code 1L << shift} overflow if MAX_RETRY_ATTEMPTS is increased.
     */
    private static final int MAX_BACKOFF_SHIFT = 30;
    /**
     * Hard ceiling on a single retry delay regardless of attempt number.
     */
    private static final long MAX_RETRY_DELAY_S = 300;

    private final List<KubernetesSensor> sensors;
    private final SensorEventPublisher publisher;
    private final GraphDbReadiness graphDbReadiness;
    private final boolean enabled;

    private ScheduledExecutorService retryExecutor;
    private volatile boolean running = false;

    public SensorOrchestrator(List<KubernetesSensor> sensors,
                              SensorEventPublisher publisher,
                              GraphDbReadiness graphDbReadiness,
                              MetisProperties properties) {
        this.sensors = sensors;
        this.publisher = publisher;
        this.graphDbReadiness = graphDbReadiness;
        this.enabled = properties.sensor() != null && properties.sensor().enabled();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!enabled) {
            log.info("Kubernetes sensor layer is disabled (metis.sensor.enabled=false)");
            return;
        }
        if (sensors.isEmpty()) {
            log.info("No KubernetesSensor beans found — sensor layer idle");
            return;
        }

        log.info("Starting Kubernetes sensor layer [{} sensor(s)]", sensors.size());
        graphDbReadiness.awaitReady();
        publisher.startScheduler();

        retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sensor-retry");
            t.setDaemon(true);
            return t;
        });

        for (KubernetesSensor sensor : sensors) {
            attemptStart(sensor, 1);
        }

        running = true;
    }

    private void attemptStart(KubernetesSensor sensor, int attempt) {
        try {
            log.info("Starting sensor: {} (attempt {})", sensor.name(), attempt);
            sensor.start();
        } catch (Exception e) {
            if (attempt >= MAX_RETRY_ATTEMPTS) {
                log.error("Sensor '{}' failed to start after {} attempts — giving up: {}",
                        sensor.name(), MAX_RETRY_ATTEMPTS, e.getMessage(), e);
                return;
            }
            int shift = Math.min(attempt - 1, MAX_BACKOFF_SHIFT);
            long delay = Math.min(INITIAL_RETRY_DELAY_S * (1L << shift), MAX_RETRY_DELAY_S);
            log.warn("Sensor '{}' failed to start (attempt {}/{}) — retrying in {}s: {}",
                    sensor.name(), attempt, MAX_RETRY_ATTEMPTS, delay, e.getMessage());
            retryExecutor.schedule(() -> attemptStart(sensor, attempt + 1), delay, TimeUnit.SECONDS);
        }
    }

    // SmartLifecycle — called on context close / SIGTERM

    @Override
    public void start() {
        // Actual start happens in onApplicationEvent to ensure gRPC server is up first
    }

    @Override
    public void stop() {
        if (!running) return;
        log.info("Stopping Kubernetes sensor layer");

        if (retryExecutor != null) {
            retryExecutor.shutdownNow();
        }

        for (KubernetesSensor sensor : sensors) {
            try {
                log.info("Stopping sensor: {}", sensor.name());
                sensor.stop();
            } catch (Exception e) {
                log.error("Error stopping sensor '{}': {}", sensor.name(), e.getMessage(), e);
            }
        }

        publisher.stopScheduler();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // SmartLifecycle stops highest-phase beans first. We use a near-MAX phase so the
        // sensor layer is shut down before the gRPC server (default phase 0), preventing
        // events from being published into a half-torn-down pipeline.
        return Integer.MAX_VALUE - 1;
    }
}
