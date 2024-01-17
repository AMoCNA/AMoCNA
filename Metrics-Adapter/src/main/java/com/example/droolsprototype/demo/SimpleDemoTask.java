package com.example.droolsprototype.demo;

import com.example.droolsprototype.execution.ExecutionService;
import com.example.droolsprototype.services.PrometheusQueryService;
import org.kie.api.runtime.StatelessKieSession;

import java.util.TimerTask;

public abstract class SimpleDemoTask extends TimerTask {

    protected final PrometheusQueryService queryService;
    protected final StatelessKieSession kieSession;
    protected final ExecutionService executionService;

    protected SimpleDemoTask(PrometheusQueryService queryService, StatelessKieSession kieSession, ExecutionService executionService) {
        this.queryService = queryService;
        this.kieSession = kieSession;
        this.executionService = executionService;
    }

    public abstract void run();
}
