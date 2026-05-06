#!/bin/bash
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE_NAME="kubernetes-management"
DOCKERFILE_PATH="Business-Demo/KubernetesManagment/Dockerfile"

REGISTRY="${REGISTRY:-sglomski}"
PUSH=false

while [[ "$#" -gt 0 ]]; do
    case $1 in
        --push) PUSH=true ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
    shift
done

echo "Building $IMAGE_NAME from $PROJECT_ROOT..."
echo "Registry: $REGISTRY, Push: $PUSH"

docker build -t "$REGISTRY/$IMAGE_NAME:latest" -f "$PROJECT_ROOT/$DOCKERFILE_PATH" "$PROJECT_ROOT"

if [ "$PUSH" = true ]; then
    echo "Pushing $REGISTRY/$IMAGE_NAME:latest..."
    docker push "$REGISTRY/$IMAGE_NAME:latest"
fi

echo "Success!"
