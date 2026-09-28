package com.kubiki.common.logging;

import org.slf4j.MDC;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A Callable wrapper that captures the MDC context of the spawning thread and restores it inside the calling thread.
 */
public class MdcCallable<V> implements Callable<V> {
    private final Callable<V> delegate;
    private final Map<String, String> contextMap;

    private MdcCallable(Callable<V> delegate) {
        this.delegate = delegate;
        this.contextMap = MDC.getCopyOfContextMap();
    }

    public static <V> Callable<V> wrap(Callable<V> callable) {
        if (callable == null) return null;
        if (callable instanceof MdcCallable) {
            return callable;
        }
        return new MdcCallable<>(callable);
    }

    @Override
    public V call() throws Exception {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        } else {
            MDC.clear();
        }
        try {
            return delegate.call();
        } finally {
            if (previousContext != null) {
                MDC.setContextMap(previousContext);
            } else {
                MDC.clear();
            }
        }
    }
}
