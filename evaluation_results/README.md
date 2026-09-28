# SCIG and paper evaluation results

## Paper evaluation (two scenarios, three apps)

```bash
kubectl apply -k infra/bookinfo/
kubectl apply -k infra/online-boutique/
kubectl apply -k infra/load-generation/
./amocna.py paper-eval run --example all -i 5 -o ./evaluation_results
```

| File | Content |
|------|---------|
| `paper_e1_*.json/.tex` | Example 1 image patching (Sock Shop, Boutique, BookInfo) |
| `paper_e2_*.json/.tex` | Example 2 SLA scale-out |

Manuscript text: [`docs/CNEEONT_PAPER_EVALUATION.md`](../docs/CNEEONT_PAPER_EVALUATION.md).

```bash
AMOCNA_SCALE_NAMESPACE=online-boutique ./amocna.py benchmark run --scenario 1
AMOCNA_SCALE_NAMESPACE=bookinfo ./amocna.py benchmark run --scenario 1
```

## SCIG re-run

```bash
kubectl apply -k infra/scig/
./amocna.py scig evaluate -e s4 -i 10 -o ./evaluation_results
./amocna.py scig evaluate -e s2 -i 3 -o ./evaluation_results
./amocna.py scig evaluate -e s1 -i 3 -o ./evaluation_results
./amocna.py scig evaluate -e s3 -i 2 -o ./evaluation_results
```

## Artifacts

| File | Content |
|------|---------|
| `s1_*.json/.tex` | Detection / SBOM+CVE inventory |
| `s2_*.json/.tex` | E2E remediation |
| `s3_*.json/.tex` | Scalability |
| `s4_*.json/.tex` | Catalog ingest / SPARQL microbench |

SCIG manuscript draft: [`docs/ASPOF_SCIG_EVALUATION.md`](../docs/ASPOF_SCIG_EVALUATION.md).
