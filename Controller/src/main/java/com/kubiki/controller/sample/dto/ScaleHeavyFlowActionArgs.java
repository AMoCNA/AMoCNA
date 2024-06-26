package com.kubiki.controller.sample.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScaleHeavyFlowActionArgs {
    int upstreamThreads;
    String upstreamService;
    double downstreamCpu;
    String downstreamPod;
    String downstreamNamespace;
}
