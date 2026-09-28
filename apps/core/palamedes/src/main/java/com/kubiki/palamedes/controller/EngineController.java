package com.kubiki.palamedes.controller;

import com.kubiki.palamedes.analyzer.AnomalyAgent;
import com.kubiki.palamedes.pipeline.MapePipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/engine")
@RequiredArgsConstructor
public class EngineController {

    private final AnomalyAgent anomalyAgent;
    private final MapePipeline mapePipeline;

    @PostMapping("/analyze")
    public String triggerAnalysis() {
        anomalyAgent.analyze();
        return "Anomaly analysis triggered";
    }

    @PostMapping("/run")
    public String triggerPipeline() {
        mapePipeline.run();
        return "MAPE Pipeline run triggered";
    }
}