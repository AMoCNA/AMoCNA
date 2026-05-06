#!/bin/bash
set -e

# Automatically find project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Starting build process for all AMoCNA modules..."

# List of modules and their build script locations
MODULES=(
    "Hphaestus-GUI-Backend/hephaestus-backend"
    "Metrics-Adapter"
    "Business-Demo/MetricExporter"
    "Business-Demo/KubernetesManagment"
)

# Run each build script with provided arguments (e.g., --push)
for module in "${MODULES[@]}"; do
    echo "---------------------------------------------------"
    echo "Building module: $module"
    echo "---------------------------------------------------"
    "$PROJECT_ROOT/$module/build.sh" "$@"
done

echo "---------------------------------------------------"
echo "All modules built successfully!"
echo "---------------------------------------------------"
