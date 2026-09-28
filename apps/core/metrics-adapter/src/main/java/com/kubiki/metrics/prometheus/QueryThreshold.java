package com.kubiki.metrics.prometheus;

import lombok.Data;

@Data
public class QueryThreshold {
    private String name;
    private String query;
    private double threshold;
    private String anomalyTypeIri; // e.g. http://...CNEEOnt#CPUSaturatedState
}
