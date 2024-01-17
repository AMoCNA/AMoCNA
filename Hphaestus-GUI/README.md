# Hephaestus Gui - Frontend
This Repository contains front end part of [Hephaestus Project](https://github.com/Hephaestus-Metrics) GUI.

Hephaestus GUI allows to select specific metrics based on their labels' values.

The GUI can be accessed on `<SERVICE ADDRESS: SERVICE PORT>/app/index.html` when deployed.

![image](https://user-images.githubusercontent.com/73036080/174442088-fb3d0719-16f4-4520-b747-cbb12e62ded7.png)

In order to deploy application on Kubernetes Cluter and get `<SERVICE ADDRESS: SERVICE PORT>` see [Hephaestus Deployment](https://github.com/Hephaestus-Metrics/Deployment)

To get more information about Hpheastus GUI [click here](https://github.com/Hephaestus-Metrics/GUI-backend)

## Quick user guide
### Searching for metric
Metrics can be filtred using their labels' values. In order to filter metric type in and select label name in search bar. Next type in and select value for given label.
Metrics matching chosen filters will be visible on the left panel.
### Selecting metrics for translation
In order to select single metric drag metric from the left pannel and drop on the right.
To select metrics  group click "select All as a Query"
When you are done with selection simply click "Save metrics" to save and propagate selection to backend.
### Clearing selection
Click on "Clear All" to clear current metrics' selection.
### Complex Queries
Work in progress

## Implementation details
Hephaestus GUI frontend is based on Angular 13.3.1 and Node.js 12.11.1 and compatible with newer versions.
