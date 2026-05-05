# Themis Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Themis module in Java 25, providing gRPC-based autonomic action execution and knowledge retrieval from GraphDB using MoaMont ontology.

**Architecture:** Shared Semantic Core approach. Themis acts as a gRPC server that queries GraphDB (via RDF4J) for MoaMont action individuals and executes them (Simple or Complex Sagas).

**Tech Stack:** Java 25, Maven, Spring Boot 3.4, gRPC (yidongnan starter), RDF4J, JUnit 5.

---

### Task 1: DOP Model & Configuration at Scale

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/model/ActionData.java`
- Create: `modules/themis/src/main/java/com/kubiki/themis/config/ThemisProperties.java`
- Create: `modules/themis/src/main/resources/application.yml`
- Create: `modules/themis/src/main/resources/application-dev.yml`
- Create: `modules/themis/src/main/resources/application-prod.yml`

- [ ] **Step 1: Define Immutable DOP Model (`ActionData.java`)**

```java
package com.kubiki.themis.model;

import java.util.List;
import java.util.Map;

public sealed interface ActionData 
    permits ActionData.SimpleAction, ActionData.ComplexWorkflow {
    
    String id();
    String functionalIntent();

    record SimpleAction(
        String id,
        String functionalIntent,
        String targetIri,
        Map<String, String> parameters
    ) implements ActionData {}

    record ComplexWorkflow(
        String id,
        String functionalIntent,
        List<ActionData> steps,
        Map<String, ActionData> compensations
    ) implements ActionData {}
}
```

- [ ] **Step 2: Implement Configuration at Scale (`ThemisProperties.java`)**

```java
package com.kubiki.themis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Root configuration class for Themis, following the "Configuration Properties at Scale" pattern.
 * Uses nested records for namespace-based discovery.
 */
@ConfigurationProperties(prefix = "themis")
public record ThemisProperties(
    @NestedConfigurationProperty GraphDB graphdb,
    @NestedConfigurationProperty Executors executors
) {
    public record GraphDB(String url, String repositoryId) {}
    
    public record Executors(
        @NestedConfigurationProperty Kubernetes kubernetes,
        @NestedConfigurationProperty Logging logging
    ) {
        public record Kubernetes(String managementUrl, int timeoutMs) {}
        public record Logging(String level) {}
    }
}
```

- [ ] **Step 3: Define Multi-profile YAML (Dev/Prod/K8s overrides)**

`application.yml`:
```yaml
spring:
  threads:
    virtual:
      enabled: true
  profiles:
    active: dev
grpc:
  server:
    port: 50051
```

`application-dev.yml`:
```yaml
themis:
  graphdb:
    url: http://localhost:7200
    repository-id: moamont
  executors:
    kubernetes:
      management-url: http://localhost:8080
      timeout-ms: 5000
    logging:
      level: INFO
```

`application-prod.yml`:
```yaml
themis:
  graphdb:
    url: ${GRAPHDB_URL:http://graphdb:7200}
    repository-id: ${GRAPHDB_REPO:moamont}
  executors:
    kubernetes:
      management-url: ${K8S_MGMT_URL:http://kubernetes-management:8080}
      timeout-ms: ${K8S_TIMEOUT:30000}
    logging:
      level: ${LOG_LEVEL:WARN}
```

- [ ] **Step 4: Verify build**

Run: `mvn clean compile` in `modules/themis`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add modules/themis
git commit -m "feat(themis): implement DOP model and scaled configuration"
```

---

### Task 2: gRPC Interface Definition

**Files:**
- Create: `modules/themis/src/main/proto/themis.proto`

- [ ] **Step 1: Define `themis.proto`**

```proto
syntax = "proto3";

option java_multiple_files = true;
option java_package = "com.kubiki.themis.grpc";
option java_outer_classname = "ThemisProto";

package themis;

service ActionService {
  rpc GetExecutableActions (ResourceRequest) returns (ActionList);
  rpc ValidatePreconditions (ActionRequest) returns (ValidationResponse);
  rpc ExecuteRemediation (ActionRequest) returns (stream ExecutionStatus);
}

message ResourceRequest {
  string resource_id = 1;
}

message ActionRequest {
  string action_id = 1;
  string target_id = 2;
}

message ActionList {
  repeated Action actions = 1;
}

message Action {
  string id = 1;
  string type = 2; 
  string functional_intent = 3;
}

message ValidationResponse {
  bool valid = 1;
  string message = 2;
}

message ExecutionStatus {
  string step = 1;
  string state = 2; 
  string message = 3;
}
```

- [ ] **Step 2: Generate gRPC classes**

Run: `mvn compile`
Expected: Classes generated.

- [ ] **Step 3: Commit**

```bash
git add modules/themis/src/main/proto/themis.proto
git commit -m "feat(themis): define ActionService gRPC interface"
```

---

### Task 4: Ground Truth Ingestion (SPARQL to Record)

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/knowledge/MoaMapper.java`
- Modify: `modules/themis/src/main/java/com/kubiki/themis/knowledge/GraphDBGateway.java`

- [ ] **Step 1: Implement `MoaMapper`**

```java
package com.kubiki.themis.knowledge;

import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.query.BindingSet;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;

@Component
public class MoaMapper {
    public ActionData.SimpleAction mapSimpleAction(BindingSet bindings) {
        return new ActionData.SimpleAction(
            bindings.getValue("action").stringValue(),
            bindings.getValue("intent").stringValue(),
            bindings.getValue("target").stringValue(),
            new HashMap<>() // Parameters would be fetched in a second query or JOIN
        );
    }
}
```

- [ ] **Step 2: Update `GraphDBGateway` to use the mapper**

Update `findActionsForResource` to return `List<ActionData>`.

- [ ] **Step 3: Commit**

```bash
git add modules/themis/src/main/java/com/kubiki/themis/knowledge
git commit -m "feat(themis): implement semantic mapper for Ground Truth ingestion"
```

---

### Task 5: Generic Dispatcher & Saga Engine

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/execution/ActionDispatcher.java`
- Create: `modules/themis/src/main/java/com/kubiki/themis/saga/SagaEngine.java`

- [ ] **Step 1: Implement `ActionDispatcher` using Java 25 switch expressions**

```java
package com.kubiki.themis.execution;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.saga.SagaEngine;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActionDispatcher {
    private final Map<String, ActionExecutor> simpleExecutors;

    public ActionDispatcher(List<ActionExecutor> executors) {
        this.simpleExecutors = executors.stream()
            .collect(Collectors.toMap(ActionExecutor::getActionType, Function.identity()));
    }

    public boolean dispatch(ActionData action) {
        return switch (action) {
            case ActionData.SimpleAction s -> executeSimple(s);
            case ActionData.ComplexWorkflow c -> executeWorkflow(c);
        };
    }

    private boolean executeSimple(ActionData.SimpleAction action) {
        ActionExecutor executor = simpleExecutors.get(action.functionalIntent());
        return executor != null && executor.execute(action.targetIri());
    }

    private boolean executeWorkflow(ActionData.ComplexWorkflow workflow) {
        SagaEngine saga = new SagaEngine();
        for (ActionData step : workflow.steps()) {
            if (step instanceof ActionData.SimpleAction s) {
                saga.addStep(new SagaEngine.Step(s.id(), simpleExecutors.get(s.functionalIntent()), s.targetIri()));
            }
        }
        return saga.run();
    }
}
```

---

### Task 6: gRPC Service Integration

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/grpc/ActionServiceImpl.java`

- [ ] **Step 1: Implement `ActionServiceImpl`**

Connect `GraphDBGateway` and `ActionDispatcher` to the gRPC endpoints.

- [ ] **Step 2: Final Integration Test**

Run: `mvn clean install`
Expected: BUILD SUCCESS
