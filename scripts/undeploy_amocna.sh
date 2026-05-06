#!/bin/bash
echo "Starting AMoCNA removal process..."

# 1. Delete Namespaces (This removes deployments, services, pods, and PVCs automatically)
echo "Deleting namespaces (this may take a minute)..."
kubectl delete namespace hephaestus --ignore-not-found
kubectl delete namespace hephaestus-business --ignore-not-found
kubectl delete namespace kubernetes-management --ignore-not-found
kubectl delete namespace metrics-adapter --ignore-not-found
kubectl delete namespace kie --ignore-not-found # In case KIE was deployed

# 2. Delete Cluster-Scoped Resources
echo "Cleaning up cluster-scoped resources..."
kubectl delete storageclass hephaestus-manual --ignore-not-found
kubectl delete persistentvolume hephaestus-gui-pv --ignore-not-found
kubectl delete clusterrole pod-reader --ignore-not-found
kubectl delete clusterrolebinding read-secrets-global --ignore-not-found

echo "---------------------------------------------------"
echo "AMoCNA has been removed from the cluster."
echo "---------------------------------------------------"