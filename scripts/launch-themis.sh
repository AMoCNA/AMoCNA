#!/bin/bash
# launch-themis.sh - Build and launch the Themis module

# Set project root relative to script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
THEMIS_DIR="$PROJECT_ROOT/modules/themis"

echo "Building Themis..."
mvn clean compile -f "$THEMIS_DIR/pom.xml"

if [ $? -eq 0 ]; then
  echo "Launching Themis (Java 25)..."
  mvn exec:java \
    -Dexec.mainClass="com.kubiki.themis.ThemisApplication" \
    -Dspring.classformat.ignore=true \
    -f "$THEMIS_DIR/pom.xml"
else
  echo "Build failed. Check the errors above."
  exit 1
fi
