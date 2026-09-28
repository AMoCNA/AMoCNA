package com.kubiki.common.logging;

import org.slf4j.MDC;
import java.util.Map;

/**
 * A Runnable wrapper that captures the MDC context of the spawning thread and restores it inside the running thread.
 */
public class MdcRunnable implements Runnable {
    private final Runnable delegate;
    private final Map<String, String> contextMap;

    private MdcRunnable(Runnable delegate) {
        this.delegate = delegate;
        this.contextMap = MDC.getCopyOfContextMap();
    }

    public static Runnable wrap(Runnable runnable) {
        if (runnable == null) return null;
        if (runnable instanceof MdcRunnable) {
            return runnable;
        }
        return new MdcRunnable(runnable);
    }

    @Override
    public void run() {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        } else {
            MDC.clear();
        }
        try {
            delegate.run();
        } finally {
            if (previousContext != null) {
                MDC.setContextMap(previousContext);
            } else {
                MDC.clear();
            }
        }
    }
}
