package com.kubiki.controller.controllerlib.storage;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.concurrent.locks.ReentrantLock;

@Service
@NoArgsConstructor
@Deprecated
public class LockProvider {
    private static final int N = 10;
    private static final ReentrantLock[] locks = new ReentrantLock[N];

    @PostConstruct
    public void init() {
        for (int i = 0; i < N; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public void acquireLock(@Nonnull String key) {
        ReentrantLock lock = locks[key.hashCode() % N];
        lock.lock();
    }

    public void releaseLock(@Nonnull String key) {
        ReentrantLock lock = locks[key.hashCode() % N];
        lock.unlock();
    }
}
