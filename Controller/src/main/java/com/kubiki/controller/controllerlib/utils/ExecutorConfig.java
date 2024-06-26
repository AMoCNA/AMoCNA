package com.kubiki.controller.controllerlib.utils;

import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@NoArgsConstructor
public class ExecutorConfig {
    @Value("${actions.simple-execution.threads:20}")
    private int simpleThreads;
    @Bean
    Executor asyncExecutor() {
       return Executors.newFixedThreadPool(simpleThreads);
    }
}
