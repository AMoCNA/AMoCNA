# Hephaestus Gui - Backend

This Repository contains the back-end part of [Hephaestus Project](https://github.com/Hephaestus-Metrics) GUI.

## Brief Description
Hephaestus GUI application allows for an easy selection of metrics that should be translated using Hephaestus Translator. It then queries Prometheus about selected metrics and exposes results on `address:8080/metrics/selected` in JSON format under 'simpleMetrics' key. Endpoint data is updated on every query and, therefore, always up to date.

To get more information about using GUI as a user see Hephaestus GUI Front-end.

To see an example usage of selected Metrics see [Hephaestus Demo - Metrics Adapter](https://github.com/Hephaestus-Metrics/Metrics-Adapter)

## Program arguments

Hephaestus GUI - Back-end requires 3 arguments at the start:
* prometheus.address - address of prometheus service used to query metrics
* saved.path - path to a place where selected metrics are saved (used mainly in Kubernetes environment, can be left as an empty string otherwise)
* config.path - path to config map containing initial metrics selection (used in Kubernetes environment, can be left as an empty string otherwise). See the Deployment section to learn more about the initial configuration.



## Deployment
To deploy the application on Kubernetes Cluster see [Hephaestus Deployment](https://github.com/Hephaestus-Metrics/Deployment).
