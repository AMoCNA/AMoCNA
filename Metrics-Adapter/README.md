# Hephaestus Demo - Metrics Adapter

This Repository contains a demo of [Hephaestus Project](https://github.com/Hephaestus-Metrics).

## Brief description

This application periodically queries [Hephaestus GUI backend](https://github.com/Hephaestus-Metrics/GUI-backend) about selected metrics and their values.

It then fires rules using [Jboss Drools Rule Engine's](https://www.drools.org/) Java API. 

Example rules can be seen in `src/main/resources/static/rules.drl`.  
To get more information about Java metric representation see [Hephaestus Translator](https://github.com/Hephaestus-Metrics/Translator).

In a given example fulfilling the rule's requirement results in the Execution Service logging message specified in the rule.

## Mapping JSON object from Hephaestus GUI to metric object
To see how to map JSON representation to Java object using Spring Boot see [PrometheusQueryService](https://github.com/Hephaestus-Metrics/Metrics-Adapter/blob/main/src/main/java/com/example/droolsprototype/services/PrometheusQueryService.java)

## Example rule definition
Below you can see an example rule

```
/**
 * Logs if filesystem is taken in more than 80% in /tmp
 */
rule "more_than_80_percent_filesystem_taken"
    dialect "mvel"
    when
        avail_bytes_m : VectorMetric(
            name == "node_filesystem_avail_bytes",
            labels["mountpoint"] == "/tmp",
            avail_bytes : value
        )
        size_bytes_m : VectorMetric(
            name == "node_filesystem_size_bytes",
            labels["mountpoint"] == "/tmp",
            size : value
        )
        executor : ExecutionService()
        eval(1 - (avail_bytes / size) > 0.8)
    then
        executor.log("More than 80% of file system taken in \"/tmp\": " + (1 - (avail_bytes / size)) * 100 + "%");
end
```

This rule fires when:
* VectorMetric with name "node_filesystem_avail_bytes", and label "mountpoint" = "/tmp" was received from Hephaestus GUI back-end
* VectorMetric with name "node_filesystem_size_bytes", and label "mountpoint" = "/tmp" was received from Hephaestus GUI back-end
* (1 - value of 1. metric (referred to as avail_bytes) / value of 2. metric (referred to as size)) is greater than 0.8 
Firing this rule results in the executor logging the appropriate message.

## Deployment
To deploy the application on Kubernetes Cluster see [Hephaestus Deployment](https://github.com/Hephaestus-Metrics/Deployment).
