package com.kubiki.common.logging;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Factory and utility class to instantiate MDC-propagating executors.
 */
public class MdcPropagatingExecutor {

    /**
     * Creates an ExecutorService that starts a new virtual thread for each task,
     * with automatic propagation of MDC (Mapped Diagnostic Context) locals from parent threads.
     */
    public static ExecutorService newVirtualThreadPerTaskExecutor() {
        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        return new MdcPropagatingExecutorService(delegate);
    }
}
