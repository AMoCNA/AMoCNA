# AMoCNA

The repository contains the implementation of the Autonomic Management Framework for Cloud-Native Applications [AMoCNA](https://www.researchgate.net/publication/344415012_Autonomic_Management_Framework_for_Cloud-Native_Applications). The main goal of the solution is to reduce the complexity of managing Cloud-native applications and provide a high level of autonomicity.

## Project Structure

The project has been restructured into the following top-level directories:

- **`apps/`**: Contains all the applications and services.
  - **`core/`**: The core AMoCNA framework (Daedalus, Metis, Palamedes, Themis). Managed by the root `pom.xml`.
- **`infra/`**: Contains Kubernetes manifests and infrastructure definitions.
- **`libs/`**: Shared libraries and schemas (e.g., ontology files).

## Orchestration CLI (`amocna.py`)

A unified Python 3 script is provided at the root of the project to handle building, deploying, and managing versions across the repository.

**Usage:**

```bash
# Show project status and discovered apps
./amocna.py status

# Login to github container registry. --user is optional, if not provided, it will be taken from the environment variable AMOCNA_USER
export AMOCNA_PAT=yourpassword
export AMOCNA_USER=yourusername
./amocna.py login --registry myregistry.com [--user user]

# Build Docker images
./amocna.py build --all
./amocna.py build --app themis

# Deploy all services to Kubernetes
./amocna.py deploy --all

# Undeploy everything
./amocna.py undeploy

# Sync versions across core POMs
./amocna.py version --bump minor

# Forward ports for local access
./amocna.py forward gui-backend

# Run tests for maven modules
./amocna.py test --all
./amocna.py test --app themis
```

All configurations (Docker registries, port forwarding, application definitions) are stored in `amocna.yaml`.

## Local Development (Docker Compose)

You can spin up the core AMoCNA services and their dependencies (RabbitMQ, GraphDB, Prometheus) using Docker Compose:

```bash
docker compose up -d
```

This will build the necessary images using the configurations in `apps/core/` and start the environment.

## Getting Started with Hephaestus and Kubernetes

If this is your first time dealing with Kubernetes, Prometheus, and Hephaestus, we strongly suggest following this route to get a better grasp of those systems:

1. Download [Minikube](https://minikube.sigs.k8s.io/docs/start/) - local Kubernetes.
2. Start Minikube Cluster using `minikube start`.
3. Clone [Sock Shop](https://github.com/microservices-demo/microservices-demo) repository - Microservices Demo designed to show example application deployment.
4. Deploy Sock Shop services and monitoring services:

   ```bash
   kubectl apply -f microservices-demo/deploy/kubernetes/manifests
   ```

5. Deploy Hephaestus using the orchestration CLI:

   ```bash
   ./amocna.py deploy --all
   ```
