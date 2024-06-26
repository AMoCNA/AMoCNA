package com.kubiki.controller.controllerlib.utils;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@Log4j2
@RequiredArgsConstructor
public class TimedFutureCreator {
    private final static int POLLING_INTERVAL_PERCENT = 95;
    @Value("${actions.max.execution.time:3600}")
    private long maxExecutionTime;
    private final Executor asyncExecutor;
    private final PriorityBlockingQueue<TimedFuture<?>> queue = new PriorityBlockingQueue<>();

    @PostConstruct
    private void scheduleAsyncPollingTask() {
        CompletableFuture.runAsync(this::poolQueue);
    }

    public void runAsync(Runnable runnable) {
        Supplier<Void> wrappedRunnable = () -> {
            runnable.run();
            return null;
        };
        createFutureAndRun(wrappedRunnable, asyncExecutor);
    }

    public <T> TimedFuture<T> createFutureAndRun(Supplier<T> supplier, Executor executor) {
        AtomicReference<Thread> interruptThread = new AtomicReference<>();
        TimedFuture<T> future = new TimedFuture<>();
        queue.put(future);
        executor.execute(() -> {
            if (interruptThread.compareAndSet(null, Thread.currentThread())) try {
                future.setInterruptThread(interruptThread);
                future.complete(supplier.get());
            } catch (Throwable e) {
                log.error(e.getMessage(), e.getCause(), e.getStackTrace());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void poolQueue() {
        while (true) {
            try {
                TimedFuture<?> nextFuture = queue.peek();
                if (Objects.nonNull(nextFuture)) {
                    Date expectedFinishTimeLimit = new Date(nextFuture.getStartDate().getTime() + maxExecutionTime * 1000);
                    Date now = new Date();
                    if (expectedFinishTimeLimit.before(now)) {
                        queue.take();
                        if (!nextFuture.isDone()) {
                            log.warn("Cancelling future execution, desired finish time: {}", expectedFinishTimeLimit);
                            nextFuture.cancel(true);
                        }
                    } else {
                        Thread.sleep(expectedFinishTimeLimit.getTime() - now.getTime());
                    }
                } else {
                    Thread.sleep(maxExecutionTime  * 10 * POLLING_INTERVAL_PERCENT);
                }
            } catch (Exception e) {
                log.warn("An Exception occurred while polling futures: {}. " +
                        "The pooling will be resumed", e.getMessage());
            }
        }
    }
}
