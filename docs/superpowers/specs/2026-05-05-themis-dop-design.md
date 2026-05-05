# High-Level Design: Themis (Data-Oriented & Autonomic)

## 1. Core Philosophy: Data-Oriented Programming (DOP)
Following DOP principles in Java 25, Themis will treat actions and systemic states as **immutable data**. We decouple "what the action is" (Data) from "how it is performed" (Logic).

### DOP Implementation Strategy:
- **Records as Data:** All semantic entities (Actions, Conditions, Costs) are represented as `sealed` records.
- **Pattern Matching:** Use `switch` expressions over sealed types for type-safe execution dispatch.
- **Isolation:** No "magic numbers" or central constant classes. Configuration is injected via type-safe records mapped from YAML.

## 2. Truly Autonomic Execution Flow (Ground Truth)
Themis operates as a **Stateless Ingestion Engine**. It possesses no internal knowledge of what an action "should" do; it only knows how to perform mechanical operations based on the data it ingests from GraphDB.

1. **Knowledge Ingestion (Ground Truth):** Themis performs a deep query on a GraphDB Individual (e.g., `moa:RestartPod_42`). It retrieves the entire semantic graph for that action:
    - Functional Intent (e.g., `moa:LifecycleControlAction`)
    - Sub-actions (for `ComplexWorkflow`)
    - Execution Parameters (stored as RDF properties)
    - Pre/Post conditions (linked individuals)
2. **DOP Transformation:** This "Ground Truth" graph is mapped into an immutable Java 25 `record`.
3. **Execution:** The engine executes the data. If you change the GraphDB Individual (e.g., add a step to a workflow or change a timeout), Themis immediately adopts the new behavior without code changes.

## 3. Configuration at Scale
Following the provided Medium pattern, configuration is handled via a nested hierarchy:
```yaml
themis:
  executors:
    kubernetes:
      endpoint: "http://mgmt:8080"
      timeout: 30s
    logging:
      level: "INFO"
```

## 4. Components

### 4.1 Domain Model (Sealed Types)
```java
public sealed interface ActionData permits SimpleAction, ComplexWorkflow {}

public record SimpleAction(
    String id,
    ActionType type,
    String targetIri,
    Map<String, String> parameters
) implements ActionData {}

public record ComplexWorkflow(
    String id,
    List<ActionData> steps,
    Map<String, ActionData> compensations
) implements ActionData {}
```

### 4.2 Generic Execution Engine
A polymorphic system where `Executor<T extends ActionData>` handles specific types without hardcoded strings.

---

# Implementation Plan Refinement

### Task 1: DOP Model & Configuration
- [ ] Define `sealed` interface hierarchy for `ActionData`.
- [ ] Implement `ThemisProperties` with nested records for every executor type.
- [ ] Use `@ConfigurationProperties` to bind YAML to these records.

### Task 2: Generic Dispatcher
- [ ] Implement `ActionDispatcher` using Java 25 `switch` expressions.
- [ ] Remove all hardcoded string mapping ("DeletePodAction").

### Task 3: Knowledge-to-Data Mapper
- [ ] Implement a generic mapper that takes an RDF4J `BindingSet` and produces an `ActionData` record.
