#!/bin/bash
set -e

echo "Starting AMoCNA deployment process..."

# 1. Create Namespaces
echo "Creating namespaces..."
kubectl apply -f Deployment/manifests/00-hephaestus-ns.yaml
kubectl apply -f Deployment/business-demo/00-hephaestus-business-ns.yaml
kubectl apply -f Deployment/kubernetes-management/00-ns.yaml
kubectl apply -f Deployment/demo-metrics-adapter/00-metrics-adapter-ns.yaml

# 2. Setup Infrastructure (Storage and Config)
echo "Setting up storage and configurations..."
# Note: Ensure your cluster has a default storage class or you have modified these files
kubectl apply -f Deployment/volume-creation/
kubectl apply -f Deployment/manifests/01-gui-cfgmap.yaml
kubectl apply -f Deployment/manifests/02-gui-pvc.yaml

# 3. Deploy Hephaestus GUI and Backend
echo "Deploying Hephaestus GUI and Backend..."
kubectl apply -f Deployment/manifests/03-gui-dep.yaml
kubectl apply -f Deployment/manifests/04-gui-svc.yaml

# 4. Deploy Metrics Adapter
echo "Deploying Metrics Adapter..."
kubectl apply -f Deployment/demo-metrics-adapter/

# 5. Deploy Business Demo
echo "Deploying Business Demo..."
kubectl apply -f Deployment/business-demo/

# 6. Deploy Kubernetes Management
echo "Deploying Kubernetes Management..."
kubectl apply -f Deployment/kubernetes-management/

echo "---------------------------------------------------"
echo "Deployment commands sent. Checking status..."
echo "Note: It may take a few minutes for all pods to reach 'Running' state."
echo "---------------------------------------------------"

