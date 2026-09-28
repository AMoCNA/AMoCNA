package com.kubiki.metis.sensor;

/**
 * Extension point for all Kubernetes sensor implementations.
 *
 * <p>To add a new sensor:
 * <ol>
 *   <li>Create a class that implements this interface.</li>
 *   <li>Annotate it with {@code @Component}.</li>
 *   <li>Inject {@link SensorEventPublisher} and use it to emit events.</li>
 * </ol>
 *
 * <p>Spring auto-collects all {@code @Component} implementations and the
 * {@link SensorOrchestrator} calls {@link #start()} / {@link #stop()} on each.
 * No other wiring is required.
 */
public interface KubernetesSensor {

    /**
     * Human-readable name used in logs.
     */
    String name();

    /**
     * Start watching Kubernetes resources and publishing events.
     * Called once after the application context is fully started.
     */
    void start();

    /**
     * Stop watching and release all resources (informers, threads, etc.).
     * Called on application shutdown.
     */
    void stop();
}
