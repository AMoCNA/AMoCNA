#!/bin/bash

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
