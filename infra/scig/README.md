# SCIG Syft sensor (PoC)

Generates Software Bills of Materials with [Syft](https://github.com/anchore/syft)
and stores them in Redis for the ASPOF SCIG pillar.

## Components

| Resource | Namespace | Role |
|----------|-----------|------|
| Redis Deployment/Service | `redis` | SBOM store |
| CronJob `scig` | `amocna-scig` | Daily scan (`0 2 * * *` UTC) |

## SCIG image (recommended)

CronJob uses a custom image with tools preinstalled (`syft`, `redis-cli`, `kubectl`) to avoid runtime downloads.

Build and push via CLI (tag defaults to parent POM version):

```bash
./amocna.py build --app scig
./amocna.py build --app scig --push
# or with everything else:
./amocna.py build --all --push
```

Manual equivalent:

```bash
docker build -f infra/scig/scig.dockerfile -t ghcr.io/amocna-kr/scig:1.12.12-SNAPSHOT .
docker push ghcr.io/amocna-kr/scig:1.12.12-SNAPSHOT
```

`./amocna.py version` keeps the CronJob image tag in sync with other infra manifests.

## Redis keys

- `sbom:repo:{repository}:{tag}` — Syft JSON
- `sbom:meta:{repository}:{tag}` — metadata (`scannedAt`, `imageRef`, `packageCount`, …)

TTL default: **48h** (`SBOM_TTL_SECONDS=172800`).

## Deploy

```bash
kubectl apply -k infra/redis
kubectl apply -k infra/scig
kubectl -n redis rollout status deployment/redis
```

Manual one-shot scan from the CronJob template:

```bash
kubectl -n amocna-scig create job scig-manual-$(date +%s) --from=cronjob/scig
kubectl -n amocna-scig logs -f job/scig-manual-<id>
```

## Verify Redis

```bash
kubectl -n redis exec -it deploy/redis -- redis-cli KEYS 'sbom:*'
kubectl -n redis exec -it deploy/redis -- redis-cli GET 'sbom:meta:docker.io/weaveworksdemos/front-end:0.3.0'
```

## Image list

Edit [`images.txt`](images.txt) (default: Sock Shop `front-end:0.3.0`), then re-apply `infra/scig`.

To scan live pod images instead:

```bash
kubectl -n amocna-scig set env cronjob/scig DISCOVER_CLUSTER_IMAGES=true
kubectl -n amocna-scig set env cronjob/scig SCAN_NAMESPACES=sock-shop
```

## Palamedes SCIG consumer

When `PALAMEDES_SCIG_ENABLED=true`, Palamedes:

1. Reads `sbom:repo:*` / `sbom:meta:*` from Redis
2. Queries [OSV.dev](https://osv.dev) for package CVEs
3. Evaluates [`policies.yaml`](../../apps/core/palamedes/src/main/resources/scig/policies.yaml)
4. Logs a decision (`patch_image` / `delete_pod` / `fail_safe`)

Manual trigger (port-forward Palamedes `:8081`):

```bash
curl -X POST 'http://localhost:8081/api/scig/scan'
curl -X POST 'http://localhost:8081/api/scig/scan/image?repository=docker.io/weaveworksdemos/front-end&tag=0.3.0'
```
