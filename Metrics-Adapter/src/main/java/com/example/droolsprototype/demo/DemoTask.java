package com.example.droolsprototype.demo;

import com.example.droolsprototype.execution.ExecutionService;
import com.example.droolsprototype.services.PrometheusQueryService;
import io.github.hephaestusmetrics.model.metrics.Metric;
import org.kie.api.runtime.StatelessKieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Demo performing queries and firing mock rule engine rules
 */
@Component
public class DemoTask extends SimpleDemoTask {
    private final StringBuilder logBuilder;

    Logger logger = LoggerFactory.getLogger(DemoTask.class);

    public DemoTask(PrometheusQueryService queryService, StatelessKieSession kieSession, ExecutionService executionService) {
        super(queryService, kieSession, executionService);
        this.logBuilder = new StringBuilder();
    }

    public void run() {
        // sending requests for metrics
        List<Metric> queryResults = queryService.queryMetrics();
        if (!queryResults.isEmpty()) {

            // prepare list including results and executor
            List<Object> kieInput = Stream.concat(
                    queryResults.stream(), Stream.of(executionService))
                    .collect(Collectors.toList());

            // tell drools to evaluate all rules if any metric has been added

            logger.info("Running drools...");
            kieSession.execute(kieInput);
        }

    }

    public String getQueryLogs() {
        return logBuilder.toString();
    }
}
