package com.kubiki.palamedes.pipeline;

/**
 * Event used to wake up the {@link MapePipeline} immediately when the GraphDB state changes.
 */
public record EngineWakeupEvent(String reason) {
}
