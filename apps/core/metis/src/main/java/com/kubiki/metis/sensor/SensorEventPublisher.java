package com.kubiki.metis.sensor;

import com.google.protobuf.Timestamp;
import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.ingestion.SensorEventProcessor;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.ingestion.model.ProcessResult;
import com.kubiki.metis.notification.PalamedesNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buffers {@link SensorEvent}s produced by {@link KubernetesSensor} implementations
 * and flushes them as batches directly to the in-process {@link SensorEventProcessor}.
 *
 * <p>A batch is flushed when either:
 * <ul>
 *   <li>the buffer reaches {@code metis.sensor.batch-size} events, or</li>
 *   <li>{@code metis.sensor.flush-interval-ms} milliseconds have elapsed.</li>
 * </ul>
 */
@Component
public class SensorEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SensorEventPublisher.class);

    private static final int MAX_BUFFERED_EVENTS = 10_000;
    private static final int MAX_CONSECUTIVE_GRAPHDB_FAILURES = 100;

    private final SensorEventProcessor processor;
    private final PalamedesNotifier notifier;
    private final int batchSize;
    private final long flushIntervalMs;
    private final String sensorHostname;

    private final List<SensorEvent> buffer = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private ScheduledExecutorService scheduler;
    private int consecutiveGraphDbFailures;

    public SensorEventPublisher(SensorEventProcessor processor,
                                PalamedesNotifier notifier,
                                MetisProperties properties) {
        this.processor = processor;
        this.notifier = notifier;
        this.batchSize = properties.sensor().batchSize();
        this.flushIntervalMs = properties.sensor().flushIntervalMs();
        this.sensorHostname = resolveHostname();
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "metis-sensor";
        }
    }

    /**
     * Wraps a {@link SensorEvent.Builder} with the current timestamp.
     */
    public static SensorEvent withTimestamp(SensorEvent.Builder builder) {
        Instant now = Instant.now();
        return builder
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano())
                        .build())
                .build();
    }

    /**
     * Start the periodic flush scheduler. Called by {@link SensorOrchestrator}.
     */
    void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sensor-flush");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        log.info("SensorEventPublisher started [batchSize={}, flushIntervalMs={}]", batchSize, flushIntervalMs);
    }

    // -------------------------------------------------------------------------

    /**
     * Stop the scheduler and flush any remaining buffered events.
     */
    void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }
        flush();
        log.info("SensorEventPublisher stopped");
    }

    /**
     * Enqueue a sensor event. Triggers an immediate flush if the buffer is full.
     */
    public void publish(SensorEvent event) {
        boolean shouldFlush;
        lock.lock();
        try {
            buffer.add(event);
            shouldFlush = buffer.size() >= batchSize;
        } finally {
            lock.unlock();
        }
        if (shouldFlush) {
            flush();
        }
    }

    /**
     * Atomically swaps the buffer and processes the swapped batch outside the lock,
     * so producers (sensor threads) are never blocked during SPARQL writes.
     */
    private void flush() {
        List<SensorEvent> batch;
        lock.lock();
        try {
            if (buffer.isEmpty()) return;
            batch = new ArrayList<>(buffer);
            buffer.clear();
        } finally {
            lock.unlock();
        }

        // Lock released — network I/O happens here without blocking publishers.
        String correlationId = buildCorrelationId();
        try {
            ProcessResult result = processor.processBatch(batch, correlationId);

            if (result.graphDbFailed()) {
                handleGraphDbFailure(batch, correlationId);
                return;
            }

            consecutiveGraphDbFailures = 0;

            HandlerResult firstSuccess = result.firstSuccess();
            if (firstSuccess != null) {
                notifier.notify(
                        firstSuccess.resourceIri(),
                        firstSuccess.ontologyType(),
                        firstSuccess.changeKind(),
                        correlationId);
            }

            log.debug("Sensor batch flushed [correlationId={}, events={}, processed={}, failed={}]",
                    correlationId, batch.size(), result.processedCount(), result.failedCount());

            if (!result.failureMessages().isEmpty()) {
                log.warn("Sensor batch had failures [correlationId={}]: {}",
                        correlationId, String.join("; ", result.failureMessages()));
            }
        } catch (Exception e) {
            log.error("Failed to flush sensor batch [correlationId={}]: {}", correlationId, e.getMessage(), e);
        }
    }

    private void handleGraphDbFailure(List<SensorEvent> batch, String correlationId) {
        consecutiveGraphDbFailures++;
        if (consecutiveGraphDbFailures > MAX_CONSECUTIVE_GRAPHDB_FAILURES) {
            log.error(
                    "Dropping {} sensor events after {} consecutive GraphDB failures [correlationId={}]",
                    batch.size(), MAX_CONSECUTIVE_GRAPHDB_FAILURES, correlationId);
            return;
        }
        if (!requeueAtFront(batch)) {
            log.error(
                    "Dropping {} sensor events — buffer would exceed {} [correlationId={}]",
                    batch.size(), MAX_BUFFERED_EVENTS, correlationId);
            return;
        }
        log.warn("GraphDB unavailable — re-queued {} events for retry [correlationId={}, consecutiveFailures={}]",
                batch.size(), correlationId, consecutiveGraphDbFailures);
    }

    /**
     * Prepends failed events back to the buffer, preserving order.
     *
     * @return {@code false} if re-queue would exceed {@link #MAX_BUFFERED_EVENTS}
     */
    private boolean requeueAtFront(List<SensorEvent> batch) {
        lock.lock();
        try {
            if (buffer.size() + batch.size() > MAX_BUFFERED_EVENTS) {
                return false;
            }
            buffer.addAll(0, batch);
            return true;
        } finally {
            lock.unlock();
        }
    }

    private String buildCorrelationId() {
        // Format: <hostname>-<uuid-short> — guaranteed ≤ 128 chars
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String raw = sensorHostname + "-" + uuid;
        return raw.length() > 128 ? raw.substring(0, 128) : raw;
    }
}
