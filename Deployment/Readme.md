# Deployment files for the Hephaestus Project
The repository contains files with [Hephaestus Project](https://github.com/orgs/Hephaestus-Metrics/repositories "Hephaestus Project") configuration for Kubernetes.
Some declarations have been provided as an example and should be changed before real-life deployment.
Those are: 
* data/metrics.json in 01-GUI-cfgmap.YAML - File contains example metrics' set to select at the start of GUI application. Can be left empty if no initial configuration is desired.
Input is in json format and consists of chosenMetrics list containing metrics. Each metric contains following fields: 
  * values - list, sepcifies metric's key- value pairs 
  * isQuery - boolean, specifies whether labels match single metric (false) or metrics set (true)

* spec/template/spec/containers/args/--prometheus.addres in 02-gui-dep.yaml - Prometheus address on cluster.
Addresses have the format of \<SERVICE-NAME\>.\<NAMESPACE\>:\<PORT\>. An example configuration is compatible with  [Sock Shop Demo](https://github.com/microservices-demo/microservices-demo "Sock Shop Demo").

## Getting Started with Hephaestus and Kubernetes
If this is your first time dealing with Kubernetes, Prometheus, and Hephaestus we strongly suggest following the route to get a better grasp of those systems:
* Download [Minikube](https://minikube.sigs.k8s.io/docs/start/) - local Kubernetes
* Start Minikube Cluster using `minikube start`
* Clone [Sock Shop](https://github.com/microservices-demo/microservices-demo) repository - Microservices Demo designed to show example application deployment
* Deploy Sock Shop services and Sock Shop monitoring services using:

`kubectl apply -f microservices-demo/deploy/kubernetes/manifests`

`kubectl apply -f microservices-demo/deploy/kubernetes/manifests-monitoring`
* Deploy Hephaestus using 

`kubectl apply -f Deployment/manifests/`

* Expose GUI service - Service can be exposed using command `minikube service -n hephaestus hephaestus-gui`. This will expose `<SERVICE ADDRESS: SERVICE PORT>` address and allow you to see acces Hephaestus GUI on `<SERVICE ADDRESS: SERVICE PORT>/app/index.html`.

* The result of rule engine can be seen on [Hephaestus Demo - Metrics Adapter](https://github.com/Hephaestus-Metrics/Metrics-Adapter) console. To access console use command `minkube dashboard` and select Metrics Adapter pod. Console can then be displayed by selecting icon in top right.


Those steps allow you to see a real-life example of an application working with Kubernetes and Hephaestus. For more details on how to use Hephaestus read the instructions attached to specific Project parts:
* [Hephaestus GUI frontend](https://github.com/Hephaestus-Metrics/GUI)
* [Hephaestus GUI backend](https://github.com/Hephaestus-Metrics/GUI-backend)
* [Hephaestus Translator](https://github.com/Hephaestus-Metrics/Translator)
* [Hephaestus Demo - Metrics Adapter](https://github.com/Hephaestus-Metrics/Metrics-Adapter)

## Files Description
Manifests contain the following declarations:
* demo-metrics-adapter - Metrics Adapter related declarations
* manifests - Hephaestus GUI and bbackend
* volume-creation - Declarations for preparing volume for Hephaestus pod

## Deployment with kie
The kie namespace can be deployed independently from Hephaestus with:

`kubectl apply -f Deployment/manifests-kie/`

Use `minikube service -n kie kie-workbench` to expose the service (if multiple ports are exposed, used the first one) and navigate to /business-central to access the workbench. Usually it takes up to 5min after the pod is created for the workbench to start and the website to start responding - be patient. The default credentials are `admin` for both username and password.

## Temporary deployment (since project files are now not on DockerHub)
Before deploying change NAME:TAG to actual values in files containing "dep" in a name.
Gui frontend should be built with command ng build --base-href /app/ and result files (not a directory) should be put in /resources/static/app in GUI backend.

### Deployment steps
* build an angular app
* put result files (**files** not result directory) in GUI back
* make .jar from GUI back & Metrics adapter
* build docker images in **minikube env**
* deploy pods
