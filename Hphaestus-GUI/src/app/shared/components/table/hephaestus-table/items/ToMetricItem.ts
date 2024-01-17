import {MetricItem} from "./MetricItem";
import {Metric} from "../../../../models/metrics/Metric";

export function toMetricItem(metrics: Metric[]): MetricItem[] {

    const result: MetricItem[] = []
    metrics.forEach(metric => {
        const metricItem = new MetricItem(metric.labels, false);
        result.push(metricItem);
    });
    return result;
}