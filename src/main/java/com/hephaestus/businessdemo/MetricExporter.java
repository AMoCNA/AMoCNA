package com.hephaestus.businessdemo;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class MetricExporter {
    private final Gauge gauge;
    private final Random random = new Random();
    private final String pod;
    private final String namespace;

    public MetricExporter(CollectorRegistry registry, @Value("${pod}") String pod, @Value("${namespace}") String namespace) {
        this.pod = pod;
        this.namespace = namespace;
        gauge = Gauge.build()
                .help("critical metric")
                .name("critical_metric")
                .labelNames("pod", "namespace")
                .register(registry);
    }

    @Scheduled(fixedRate = 1000)
    private void runIncrementer() {
            gauge.labels(pod, namespace).inc(random.nextInt(5));
    }
}
