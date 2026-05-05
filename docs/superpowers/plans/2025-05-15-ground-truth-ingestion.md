# Ground Truth Ingestion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `MoaMapper` to translate RDF4J results into `ActionData` and update `GraphDBGateway` to use it for querying ground truth actions.

**Architecture:** Introduce a dedicated mapper component for RDF results and integrate it into the existing GraphDB gateway. This follows a clean separation of concerns between data retrieval and data mapping.

**Tech Stack:** Java 25, Spring Boot 3.4, RDF4J.

---

### Task 1: Create MoaMapper

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/knowledge/MoaMapper.java`

- [ ] **Step 1: Implement MoaMapper**

```java
package com.kubiki.themis.knowledge;

import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.query.BindingSet;
import org.springframework.stereotype.Component;
import java.util.HashMap;

@Component
public class MoaMapper {
    public ActionData.SimpleAction mapSimpleAction(BindingSet bindings) {
        return new ActionData.SimpleAction(
            bindings.getValue("action").stringValue(),
            bindings.getValue("intent").stringValue(),
            bindings.getValue("target").stringValue(),
            new HashMap<>() // Parameters would be fetched in a second query or JOIN if needed
        );
    }
}
```

- [ ] **Step 2: Verify compilation**
Run: `mvn clean compile -pl modules/themis`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**
```bash
git add modules/themis/src/main/java/com/kubiki/themis/knowledge/MoaMapper.java
git commit -m "feat(themis): implement MoaMapper for RDF4J results"
```

### Task 2: Update GraphDBGateway

**Files:**
- Modify: `modules/themis/src/main/java/com/kubiki/themis/knowledge/GraphDBGateway.java`

- [ ] **Step 1: Inject MoaMapper and update findActionsForResource**

```java
package com.kubiki.themis.knowledge;

import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Service
public class GraphDBGateway {
    private final Repository repository;
    private final MoaMapper moaMapper;
    private static final String NAMESPACE = "http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#";

    public GraphDBGateway(ThemisProperties properties, MoaMapper moaMapper) {
        this.repository = new HTTPRepository(properties.graphdb().url(), properties.graphdb().repositoryId());
        this.moaMapper = moaMapper;
    }

    @PostConstruct
    public void init() {
        this.repository.init();
    }

    public List<ActionData> findActionsForResource(String resourceId) {
        String sparql = "PREFIX moa: <" + NAMESPACE + "> " +
                        "SELECT ?action ?intent ?target WHERE { " +
                        "  ?action moa:targetsEntity <" + resourceId + "> . " +
                        "  ?action a moa:AutonomicAction . " +
                        "  ?action a ?intent . " +
                        "  ?action moa:targetsEntity ?target . " +
                        "  FILTER(?intent != moa:AutonomicAction && ?intent != moa:SimpleAction) " +
                        "}";
        
        List<ActionData> actions = new ArrayList<>();
        try (RepositoryConnection conn = repository.getConnection()) {
            TupleQuery query = conn.prepareTupleQuery(sparql);
            try (TupleQueryResult result = query.evaluate()) {
                while (result.hasNext()) {
                    actions.add(moaMapper.mapSimpleAction(result.next()));
                }
            }
        }
        return actions;
    }

    @PreDestroy
    public void shutDown() {
        repository.shutDown();
    }
}
```

- [ ] **Step 2: Verify compilation**
Run: `mvn clean compile -pl modules/themis`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**
```bash
git add modules/themis/src/main/java/com/kubiki/themis/knowledge/GraphDBGateway.java
git commit -m "feat(themis): implement semantic mapper for Ground Truth ingestion"
```
