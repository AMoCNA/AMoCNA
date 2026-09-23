# ASPOF Evaluation Draft — SCIG Pillar PoC

Manuscript revision text for *Self-protection capabilities among resources of the Cloud-native execution environment*.
Replace the placeholders `Evaluation of ....` / `Implementation` and update Intro/Conclusions as marked.

---

## Acronym summary table (Intro / early Related Work)

| Acronym | Expansion |
|---------|-----------|
| AC | Autonomic Computing |
| MAPE-K | Monitor–Analyze–Plan–Execute–Knowledge |
| MRE-K | Monitor–Rule-Engine–Execute–Knowledge |
| ASPOF | Autonomic Security Posture and Orchestration Framework |
| SCIG | Supply Chain Integrity Guardian |
| ACLA | Autonomic Control Loop Attestation |
| RBW | Runtime Behavior Warden |
| CDS | Configuration Drift Sentinel |
| SBOM | Software Bill of Materials |
| CVE | Common Vulnerabilities and Exposures |

---

## Explicit contributions (add to Introduction)

This paper makes the following contributions:

1. A structured survey and comparison of autonomic self-protection approaches for cloud-native environments, including an operational overhead evaluation of representative frameworks and CNCF security tools.
2. A gap analysis identifying five underaddressed threat vectors in current solutions.
3. ASPOF, a modular policy-driven reference architecture with four pillars (SCIG, ACLA, RBW, CDS), each mapped to one identified gap.
4. **A proof-of-concept implementation and evaluation of the SCIG pillar on the AMoCNA autonomic platform**, demonstrating automated SBOM/CVE monitoring and policy-driven container image remediation across heterogeneous microservice workloads (retail-style e-commerce demos and a polyglot service-mesh sample).

---

## Section: Implementation of the SCIG Pillar (PoC)

While ASPOF is proposed as a reference architecture, validating feasibility requires empirical evidence beyond conceptual design. We therefore implement **one ASPOF pillar—SCIG—as a proof-of-concept** integrated into AMoCNA, a policy-driven MRE-K autonomic framework for Kubernetes. The remaining pillars (ACLA, RBW, CDS) are out of scope of this PoC and remain architectural.

### Mapping ASPOF SCIG phases to the PoC

| ASPOF SCIG (MAPE-K) | PoC realization on AMoCNA |
|---------------------|---------------------------|
| **Monitor** | Kubernetes CronJob (`amocna-scig`) runs Syft to generate an SBOM for each target image and stores it in Redis (`sbom:repo:{repository}:{tag}`). |
| **Analyze** | Grype matches SBOM packages against its vulnerability database; results are stored in Redis (`sbom:cve:*`). Palamedes periodically synchronizes these findings into an in-memory `VulnerabilityCatalog` (merged with a curated demo catalog that supplies known fixed *image* tags for reproducible remediation experiments). Optional Trivy scans are stored for cross-scanner comparison (`sbom:trivy:*`) but are not on the remediation critical path. |
| **Plan** | `ImageRemediationPlanner` queries GraphDB topology for workloads running catalog-affected image versions and selects a fix tag under an upgrade policy (PATCH/MINOR). It creates an `ImageUpdateIntent` workflow with hydration parameters (namespace, container, repository, target version). |
| **Execute** | Themis executes the planned action against the Kubernetes API (image update on the Deployment), closing the autonomic loop. |

The PoC reuses cloud-native tooling already common in production environments (Syft, Grype, Redis, Kubernetes Jobs/CronJobs, Fabric8/kubectl), addressing the need for integration with Kubernetes-native security tooling.

### Scope limits (honest vs. full ASPOF SCIG)

Relative to the conceptual SCIG description, this PoC:

- Performs **runtime remediation** (update the running Deployment image) rather than **admission-time blocking** or CI/CD pipeline failure.
- Uses a **curated fix-tag catalog** for deterministic image upgrades in addition to dynamic Grype CVE evidence; package-level fixed versions from Grype are not automatically mapped to new image digests without that curation.
- Does **not** implement ACLA, RBW, or CDS.

We therefore describe the PoC as a *runtime remediation instantiation of SCIG*, sufficient to demonstrate that the supply-chain gap can be closed inside a single global autonomic loop without claiming a complete ASPOF deployment.

### Industry-oriented workloads (generalizability)

To avoid evaluating a single application stack, SCIG scans three heterogeneous microservice suites:

- **Sock Shop** — retail e-commerce microservices (primary remediation target).
- **Online Boutique** — larger e-commerce demo (Google microservices-demo).
- **BookInfo** — Istio polyglot sample (service-mesh / multi-language).

These are standard research/demo applications spanning retail-style and mesh-oriented deployments; they are **not** full industry case studies, but they show that SCIG monitoring is not tied to one language or one application topology.

---

## Section: Evaluation of the SCIG PoC

### Objectives and research questions

We evaluate the SCIG PoC along three questions:

- **RQ1 (Detection):** Does SCIG produce SBOMs and CVE findings for heterogeneous applications in a live Kubernetes cluster?
- **RQ2 (Remediation):** Does the AMoCNA control loop autonomously replace vulnerable Sock Shop images with policy-compliant tags without operator-driven `kubectl set image`?
- **RQ3 (Overhead / scalability):** What is the wall-clock cost of complete SCIG scan Jobs as the namespace scope grows from one to three applications, and what Redis memory footprint results?

### Experimental setup

- **Platform:** Kubernetes cluster with AMoCNA (Metis, Palamedes, Themis, GraphDB, RabbitMQ), Redis, and SCIG CronJob/Job.
- **Scanners:** Syft (SBOM), Grype (CVE; remediation path), Trivy (comparison only).
- **Remediation targets (RQ2):** `front-end` (PATCH, 0.3.0→0.3.12), `orders` (MINOR, 0.4.0→0.4.7), `carts` (MINOR, 0.3.5→0.4.8).
- **Metrics:** scan Job completion time, per-image Syft/Grype durations (from Redis metadata), CVE counts by severity, Grype∩Trivy Jaccard similarity, E2E remediation latency until Deployment image tag matches the expected fix, Redis `used_memory`, and `kubectl top` samples for Palamedes during experiments.
- **Statistics:** results reported as mean ± standard deviation over \(N\) iterations (scan timing \(N \ge 3\); remediation \(N \ge 3\); policy-path microbenchmarks \(N = 10\)). Exact \(N\) and measured values are filled from `evaluation_results/` after harness runs.

### Results (measured)

Raw JSON/LaTeX artifacts live in [`evaluation_results/`](../evaluation_results/). Harness: `./amocna.py scig evaluate -e {s1|s2|s3|s4}`.

#### Table I — Detection across applications (RQ1)

From Redis `sbom:meta:*` after completed SCIG Jobs (Syft+Grype; Trivy produced empty vulnerability arrays in this cluster build):

| Application | Images (listed) | Packages (Σ) | Grype CVEs (Σ) | Critical | High |
|-------------|-----------------|--------------|----------------|----------|------|
| Sock Shop | 8 | 5659 | 2309 | 252 | 1052 |
| BookInfo | 6 | 19434 | 1827 | 24 | 141 |
| Online Boutique | 10 | 13263 | 2836 | 145 | 1047 |

Per-image Syft/Grype wall times are written into Redis metadata (`syftDurationMs`, `grypeDurationMs`; second-resolution). Observed Grype times are typically on the order of **2–6 minutes per image** on this cluster; Syft is usually tens of seconds. Insert `s1_per_image_table.tex` / `s1_severity_table.tex` in the camera-ready PDF.

**Grype unique CVE IDs (sample of Redis keys):** 1179+. Trivy unique IDs: 0 in this deployment (Trivy ran but returned no CVE records usable for Jaccard)—report Grype as the remediation-path scanner and treat Trivy as optional/non-functional here.

#### Table III — End-to-end remediation (RQ2)

Controlled S2 run (\(N=3\)): reset `front-end`/`orders`/`carts` to vulnerable tags, allow only `ImageUpdateIntent`, wait for Deployment image tags to match catalog fixes. **Success rate 100%** for all three services. Wall-clock from reset to patched tag (mean ± std, ms):

| Service | Policy | Rollout (ms) |
|---------|--------|--------------|
| front-end | PATCH → 0.3.12 | \(10643 \pm 36\) |
| orders | MINOR → 0.4.7 | \(12690 \pm 2752\) |
| carts | MINOR → 0.4.8 | \(17675 \pm 2875\) |

Parallel iteration E2E \(\approx 19 \pm 3\) s. Results: `s2_results.json` / `s2_remediation_latency.tex`.

#### Table IV — Scanning scalability (RQ3)

| Scope | Measured wall time | Images (Redis scope) | Grype CVE sum | Redis memory |
|-------|--------------------|----------------------|---------------|--------------|
| Sock Shop (discover Job, complete) | **4675 s** (~78 min) | 17 | 5579 | ~157 MB |
| Sock Shop + BookInfo (inventory; time lower-bounded) | ≥4675 s | 21 | 7406 | ~157 MB |
| All 3 Applications (historical CronJob 96 min) | **5760 s** | 32 | 10795 | ~157 MB |

Per-image Grype dominates Job time (~200 s+/image). Discover mode also scans ancillary Sock Shop images (DBs, Locust, Redis sidecars), increasing scope beyond the eight application services. See `s3_scalability_table.tex`.

#### Policy-path microbenchmark (S4, \(N=10\))

In-process mirror of catalog ingest (JSON parse of \(N\) records), merge, and SPARQL VALUES construction (no commas between tuples). One-shot Redis load of up to 600 CVE records took \(\approx 2.4\) s and is **not** scaled with \(N\).

| Records | Parse (ms) | Merge (ms) | SPARQL (ms) | Throughput (rec/s) |
|---------|------------|------------|-------------|--------------------|
| 10 | \(0.019 \pm 0.002\) | \(0.007 \pm 0.002\) | \(0.005 \pm 0.002\) | \(\sim 3.4 \times 10^{5}\) |
| 50 | \(0.080 \pm 0.002\) | \(0.027 \pm 0.001\) | \(0.011 \pm 0.001\) | \(\sim 4.2 \times 10^{5}\) |
| 100 | \(0.161 \pm 0.006\) | \(0.056 \pm 0.001\) | \(0.025 \pm 0.001\) | \(\sim 4.1 \times 10^{5}\) |
| 250 | \(0.410 \pm 0.013\) | \(0.145 \pm 0.009\) | \(0.057 \pm 0.002\) | \(\sim 4.1 \times 10^{5}\) |
| 500 | \(0.829 \pm 0.051\) | \(0.278 \pm 0.006\) | \(0.114 \pm 0.008\) | \(\sim 4.1 \times 10^{5}\) |

Merge/SPARQL stay sub-millisecond; cost is negligible vs.\ multi-minute Grype Jobs.

### Discussion

- The PoC **confirms feasibility of SCIG monitoring**: continuous SBOM/CVE generation with Syft/Grype across three heterogeneous microservice suites, with Redis as the shared knowledge store and Deployment annotations for vulnerability status.
- Scanner wall-clock is dominated by Grype (minutes per image); Redis memory for this PoC inventory is on the order of **~150+ MB** (during full scans; lower after TTL/cleanup).
- **Remediation (RQ2):** Analyze→Plan→Execute on Palamedes/Themis with a curated fix-tag catalog autonomously patched all three Sock Shop targets in \(\approx\)10–21 s from reset (\(N=3\), 100% success).
- Relative to the earlier overhead study of AMoCNA/Falco/KubeArmor, SCIG adds a batch scanner Job and Redis knowledge rather than a continuous dataplane interceptor; its cost is dominated by image pull/SBOM/CVE scanning, not steady-state request latency of the business application.
- Evaluating a **single pillar** does not validate full ASPOF. Cross-pillar conflict resolution inside one global MAPE-K loop was not exercised; federated/hierarchical loops remain future work.
- Editor concern on “single instance”: heterogeneous apps show monitoring generality; remediation is demonstrated on Sock Shop where curated fix tags exist—consistent with a Computing Practices PoC rather than a multi-industry field study.

### Threats to validity

- **Internal:** Remediation correctness depends on the curated image-fix catalog plus Redis-synced CVE evidence. Grype’s package-level fixed versions alone do not select container image tags; claims must not assert fully automatic NVD→digest upgrades.
- **External:** Three demo applications and one cluster/hardware configuration limit generalizability to multi-tenant production.
- **Construct:** Runtime image upgrade ≠ admission controller / CI gate described in the conceptual SCIG subsection; we name the PoC accordingly.
- **Conclusion validity:** Other ASPOF pillars are unevaluated; results must not be generalized to ACLA/RBW/CDS.

---

## Revised Conclusions (replace “we plan to conduct…”)

As cloud-native systems grow in importance, their distributed nature expands the attack surface. Our survey and gap analysis highlight deficiencies in supply-chain protection, control-loop integrity, container escapes, zero-days, and configuration drift. ASPOF organizes responses to these gaps into four modular pillars.

We implemented and evaluated a **SCIG PoC on AMoCNA**, showing that SBOM/CVE monitoring with Syft/Grype and autonomic image remediation can address the supply-chain gap in a live Kubernetes environment across heterogeneous microservice workloads, with measured scan and remediation costs.

Limitations include curated fix-tag mapping, runtime-only enforcement, and unevaluated ACLA/RBW/CDS pillars. Future work will implement the remaining pillars, explore hierarchical control loops for scale and conflict resolution, and extend evaluation toward additional industry-oriented deployments under the same methodology.

---

## Narrative reorder note (R1/R3)

Move **Requirements / Gap Analysis** *before* **Comparison of AC solutions (overhead)**, so overhead results are framed as evidence of maturity gaps after the problem statement, not before it.
