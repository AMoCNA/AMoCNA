package com.hephaestus.businessdemo;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Histogram;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class MetricExporter {

    private int criticalFactor = 0;
    private final Histogram histogram;
    private final Random random = new Random();
    private final String pod;
    private final String namespace;

    public MetricExporter(CollectorRegistry registry, @Value("${pod}") String pod, @Value("${namespace}") String namespace) {
        this.pod = pod;
        this.namespace = namespace;
        histogram = Histogram.build()
                .help("critical metric")
                .name("critical_metric")
                .labelNames("pod", "namespace")
                .register(registry);
    }

    @Scheduled(fixedRate = 1000)
    private void runIncrementer() {
            criticalFactor += random.nextInt(5);
            System.out.println(criticalFactor);
            histogram.labels(pod, namespace).observe(criticalFactor);
    }
}
