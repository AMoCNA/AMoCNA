package com.kubiki.controller.sample.util;

import lombok.Getter;
import org.springframework.stereotype.Service;

@Getter
@Service
public class ActionCounterService {

    private int counter = 0;
    private int successCounter = 0;

    public synchronized void increment() {
        ++counter;
    }

    public synchronized void incrementSuccessCounter() {
        ++successCounter;
    }

    public synchronized int getAndClearSuccessCounter() {
        int res = successCounter;
        successCounter = 0;
        return res;
    }

    public synchronized int getAndClearCounter() {
        int res = counter;
        counter = 0;
        return res;
    }

}
