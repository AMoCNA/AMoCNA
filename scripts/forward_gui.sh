#!/bin/bash

# Configuration
NAMESPACE="hephaestus"
SERVICE="hephaestus-gui"
LOCAL_PORT=8080
TARGET_PORT=8080

echo "Forwarding Hephaestus GUI to http://localhost:$LOCAL_PORT/index.html"
echo "Press Ctrl+C to stop forwarding."

kubectl port-forward -n $NAMESPACE svc/$SERVICE $LOCAL_PORT:$TARGET_PORT
