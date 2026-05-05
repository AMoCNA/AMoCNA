#!/bin/bash
# test-themis.sh - Run local tests for the Themis module

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
THEMIS_DIR="$PROJECT_ROOT/modules/themis"

echo "Running Automated Unit Tests..."
mvn test -f "$THEMIS_DIR/pom.xml"

if [ $? -eq 0 ]; then
    echo "Unit tests PASSED."
else
    echo "Unit tests FAILED."
    exit 1
fi

echo "---"
echo "To perform manual E2E gRPC tests, ensure Themis is running (use scripts/launch-themis.sh) and run:"
echo ""
echo "grpcurl -plaintext -d '{\"resource_id\": \"cnee:pod-123\"}' localhost:50051 themis.ActionService/GetExecutableActions"
echo ""
echo "grpcurl -plaintext -d '{\"action_id\": \"moa:DeletePod_1\", \"target_id\": \"default/test-pod\"}' localhost:50051 themis.ActionService/ExecuteRemediation"
