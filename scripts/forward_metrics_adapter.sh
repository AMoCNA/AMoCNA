#!/bin/bash

# Configuration
NAMESPACE="metrics-adapter"
SERVICE="metrics-adapter"
LOCAL_PORT=8081
TARGET_PORT=8085

echo "Forwarding Metrics Adapter to http://localhost:$LOCAL_PORT"
echo "Decision logs available at: http://localhost:$LOCAL_PORT/query/log"
echo "Press Ctrl+C to stop forwarding."

# Execute port-forward
kubectl port-forward -n $NAMESPACE svc/$SERVICE $LOCAL_PORT:$TARGET_PORT
