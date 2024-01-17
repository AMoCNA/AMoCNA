# Hephaestus - Translator

This Repository contains the Translator of [Hephaestus Project](https://github.com/Hephaestus-Metrics).

Translator consists of representation of Prometheus queries results and Metrics written in Java 16.

## Maven Repository
Translator is available on remote Maven repository with the following group id:

`<groupId>io.github.hephaestus-metrics</groupId>`

## Brief descritpion
Metrics have been split into two categories each containing two subcategories.
* Simple metrics
  * Scalar metrics
  * String metrics
* Complex metrics
  * Vector metrics
  * Matrix metrics
 
The above are representations of result metrics of queries to Prometheus. Read [PromQL Expression language data types](https://prometheus.io/docs/prometheus/latest/querying/basics/) for details.

Metrics consist of:
* `lables` - Hashmap mapping label name (string) to label value (string)
* `value` - metric's value, type depends on metric type
* `name` [ONLY COMPLEX METRICS] - metric's name (string)
* `timestamps(s)` [ONLY COMPLEX METRICS] - metric's timestamp if metric's type is VectorMetric or timestamps if type is Matrix metric

Please note that Translator does not contain components responsible for reading metrics from a given endpoint and mapping JSON representation to metrics object since those are dependent on user application. To see an example use of translator based on Spring Boot see [Hephaestus Demo - Metrics Adapter](https://github.com/Hephaestus-Metrics/Metrics-Adapter) and [Object mapping in Metrics Adapter](https://github.com/Hephaestus-Metrics/Metrics-Adapter/blob/main/src/main/java/com/example/droolsprototype/services/PrometheusQueryService.java).
