# High-Level Design: Themis Module

**Topic:** Autonomic Management Action Executor & Knowledge Provider  
**Date:** 2026-05-05  
**Status:** Draft (Pending Review)

---

## 1. Introduction
The **Themis** module is a core component of the AMoCNA framework, responsible for the lifecycle of autonomic management actions defined in the **MoaMont** ontology. It serves as a semantic bridge between abstract remediation strategies and physical cluster state transitions.

## 2. Goals & Success Criteria
- **Semantic Interoperability:** Use MoaMont to define and validate actions independently of underlying cloud APIs.
- **Saga Orchestration:** Reliably execute complex, multi-step workflows with built-in compensation (rollback) mechanisms.
- **Drools Migration:** Re-implement all existing actions previously handled by Drools (e.g., `deletePod`, `Alerting`) as first-class individuals in GraphDB.
- **Knowledge-Driven Execution:** All "ground truth" actions are retrieved from GraphDB as individuals, rather than being hard-coded.
- **Java 25 Maturity:** Leverage modern Java features (Virtual Threads, Structured Concurrency) for concurrent Saga execution.

## 3. Architecture Overview
Themis follows a **Shared Semantic Core** architecture. It interacts directly with a central **GraphDB** that hosts the bridged ontologies: **MoaMont** (Actions), **CNEEont** (Resources), and a 3rd **Bridge Ontology**.

### Component Diagram
```
[ AMoCNA / Palamedes ]
       |
       | (gRPC)
       v
[ Themis Module (Java 25) ] <-----> [ GraphDB (Ontotext/RDF4J) ]
       |                               ^         ^
       | (Kubernetes API / REST / CLI) |         |
       v                               |         |
[ CNEE Cluster (K8s/Nodes) ] <---------+---------+
```

## 4. Key Components

### 4.1 Knowledge Gateway (RDF4J)
- **Responsibility:** Querying GraphDB for executable actions (Individuals) based on resource state.
- **Technology:** Eclipse RDF4J.
- **Logic:** Retrieves `SimpleAction` and `ComplexWorkflow` individuals, including their $\Phi_{pre}$, $\Phi_{post}$, and $\xi$ (compensation) definitions.

### 4.2 Saga Engine (Petri Net Executor)
- **Responsibility:** Managing the execution of `ComplexWorkflow` instances.
- **Logic:** 
  - Decomposes intents into `SimpleAction` sequences based on `isDecomposedInto` relations in GraphDB.
  - Tracks state transitions in a Petri Net model.
  - Triggers compensation actions ($\xi$) if $\Phi_{post}$ fails.

### 4.3 Action Provider (gRPC Service)
- **Responsibility:** Exposing Themis capabilities to other modules.
- **Endpoints:**
  - `GetExecutableActions`: Returns valid actions for a given resource.
  - `ValidatePreconditions`: Checks $\Phi_{pre}$ against the current knowledge base.
  - `ExecuteRemediation`: Starts a Saga workflow and streams status updates.

## 5. Migrated Actions (from Drools)
The following actions will be implemented as `SimpleAction` individuals in GraphDB:
- **`DeletePodAction`**: Targets a Pod and triggers the `kubernetes-management` REST API.
- **`LogAlertAction`**: A generic logging/alerting action used for monitoring-only rules (Disk Pressure, Network Down, etc.).

## 6. Technology Stack
- **Language:** Java 25 (OpenJDK).
- **Framework:** Spring Boot 3.4+ (Virtual Threads & Multi-profile Config).
- **Build Tool:** Maven.
- **gRPC:** gRPC Java with `grpc-spring-boot-starter`.
- **Semantic Library:** Eclipse RDF4J (GraphDB Runtime).
- **Configuration:** YAML-based profiles (`dev`, `prod`) with environment overrides.

## 7. Implementation Roadmap (Themis)
1. **Module Scaffolding:** Create `modules/themis` Spring Boot 3.4 project.
2. **Configuration Setup:** Define `dev`/`prod` profiles and environment override mappings.
3. **gRPC Definitions:** Define `.proto` files for Discovery and Execution.
4. **GraphDB Integration:** Implement RDF4J client using Spring beans.
5. **Simple Action Implementations:**
   - `DeletePodAction` (REST call to `kubernetes-management`).
   - `LogAlertAction` (Logging provider).
6. **Saga Engine:** Implement the Petri Net workflow logic with compensation using Virtual Threads.
7. **Integration Tests:** Verify CQ compliance using GraphDB individuals.
