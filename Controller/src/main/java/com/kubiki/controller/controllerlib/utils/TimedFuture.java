package com.kubiki.controller.controllerlib.utils;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class TimedFuture<T> extends CompletableFuture<T> implements Comparable<TimedFuture<T>>{
    private final Date startDate;
    @Setter
    private AtomicReference<Thread> interruptThread;
    public TimedFuture() {
        super();
        startDate = new Date();
    }
    @Override
    public boolean cancel(boolean interruptIfRunning) {
        if (!interruptThread.compareAndSet(null, Thread.currentThread())
                && interruptIfRunning) {
            interruptThread.get().interrupt();
        }
        return super.cancel(interruptIfRunning);
    }

    @Override
    public int compareTo(TimedFuture<T> other) {
        return startDate.compareTo(other.getStartDate());
    }
}