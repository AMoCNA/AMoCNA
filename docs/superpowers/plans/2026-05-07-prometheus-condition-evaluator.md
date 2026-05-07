# PrometheusConditionEvaluator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `PrometheusConditionEvaluator` to evaluate conditions by querying Prometheus Instant Query API.

**Architecture:** A strategy-based `ConditionEvaluator` that uses a dedicated `RestClient` to execute PromQL queries. A condition is met if the query returns a non-empty result set.

**Tech Stack:** Java 25, Spring Boot 4.0.6, RestClient, Jackson (JsonNode).

---

### Task 1: Update Configuration and Constants

**Files:**
- Modify: `modules/themis/src/main/java/com/kubiki/themis/config/ThemisProperties.java`
- Modify: `modules/themis/src/main/java/com/kubiki/themis/constants/OntologyConstants.java`

- [ ] **Step 1: Update ThemisProperties record**

Add `Prometheus` nested record to `ThemisProperties`.

```java
package com.kubiki.themis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "themis")
public record ThemisProperties(
    @NestedConfigurationProperty GraphDB graphdb,
    @NestedConfigurationProperty Ontology ontology,
    @NestedConfigurationProperty Prometheus prometheus
) {
    public record GraphDB(String url, String repositoryId, int timeoutMs) {}
    public record Ontology(
        String moaNamespace,
        String cneeNamespace
    ) {}
    public record Prometheus(String url) {}
}
```

- [ ] **Step 2: Add PrometheusCondition constant**

```java
package com.kubiki.themis.constants;

public final class OntologyConstants {
    private OntologyConstants() {}
    public static final String CLASS_STATE_BASED_CONDITION = "StateBasedCondition";
    public static final String CLASS_PROMETHEUS_CONDITION = "PrometheusCondition";
    // ... rest of constants
}
```

- [ ] **Step 3: Commit changes**

```bash
git add modules/themis/src/main/java/com/kubiki/themis/config/ThemisProperties.java \
        modules/themis/src/main/java/com/kubiki/themis/constants/OntologyConstants.java
git commit -m "feat(themis): add Prometheus configuration and constants"
```

---

### Task 2: Create Exception and Infrastructure

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/exception/ConditionEvaluationException.java`
- Create: `modules/themis/src/main/java/com/kubiki/themis/config/PrometheusConfig.java`

- [ ] **Step 1: Create ConditionEvaluationException**

```java
package com.kubiki.themis.exception;

public class ConditionEvaluationException extends RuntimeException {
    public ConditionEvaluationException(String message) {
        super(message);
    }
    public ConditionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: Create PrometheusConfig**

```java
package com.kubiki.themis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PrometheusConfig {

    @Bean(name = "prometheusRestClient")
    public RestClient prometheusRestClient(ThemisProperties properties, RestClient.Builder builder) {
        return builder.baseUrl(properties.prometheus().url()).build();
    }
}
```

- [ ] **Step 3: Commit infrastructure**

```bash
git add modules/themis/src/main/java/com/kubiki/themis/exception/ConditionEvaluationException.java \
        modules/themis/src/main/java/com/kubiki/themis/config/PrometheusConfig.java
git commit -m "feat(themis): add ConditionEvaluationException and PrometheusConfig"
```

---

### Task 3: Implement PrometheusConditionEvaluator (TDD)

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/condition/impl/PrometheusConditionEvaluator.java`
- Create: `modules/themis/src/test/java/com/kubiki/themis/condition/impl/PrometheusConditionEvaluatorTest.java`

- [ ] **Step 1: Write failing test for PrometheusConditionEvaluator**

Use `MockRestServiceServer` to mock Prometheus responses.

```java
package com.kubiki.themis.condition.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.exception.ConditionEvaluationException;
import com.kubiki.themis.model.ActionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

@RestClientTest(PrometheusConditionEvaluator.class)
class PrometheusConditionEvaluatorTest {

    @Autowired
    private PrometheusConditionEvaluator evaluator;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private ObjectMapper objectMapper;

    private final ThemisProperties properties = new ThemisProperties(
        null, 
        new ThemisProperties.Ontology("http://example.org/moa#", "http://example.org/cnee#"),
        new ThemisProperties.Prometheus("http://prometheus:9090")
    );

    @Test
    void shouldSupportPrometheusCondition() {
        assertTrue(evaluator.supports("http://example.org/moa#PrometheusCondition"));
    }

    @Test
    void shouldReturnTrueWhenResultIsNotEmpty() throws JsonProcessingException {
        String response = objectMapper.writeValueAsString(Map.of(
            "status", "success",
            "data", Map.of("result", java.util.List.of(Map.of("metric", Map.of(), "value", java.util.List.of(1, "1"))))
        ));

        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
              .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");
        assertTrue(evaluator.evaluate(condition));
    }

    @Test
    void shouldReturnFalseWhenResultIsEmpty() throws JsonProcessingException {
        String response = objectMapper.writeValueAsString(Map.of(
            "status", "success",
            "data", Map.of("result", java.util.List.of())
        ));

        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
              .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");
        assertFalse(evaluator.evaluate(condition));
    }

    @Test
    void shouldThrowExceptionOnError() {
        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
              .andRespond(withServerError());

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");
        assertThrows(ConditionEvaluationException.class, () -> evaluator.evaluate(condition));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=PrometheusConditionEvaluatorTest -f modules/themis/pom.xml`
Expected: Compilation error (class missing).

- [ ] **Step 3: Implement PrometheusConditionEvaluator**

```java
package com.kubiki.themis.condition.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.kubiki.themis.condition.ConditionEvaluator;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.constants.OntologyConstants;
import com.kubiki.themis.exception.ConditionEvaluationException;
import com.kubiki.themis.model.ActionData;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PrometheusConditionEvaluator implements ConditionEvaluator {

    private final RestClient restClient;
    private final ThemisProperties properties;

    public PrometheusConditionEvaluator(@Qualifier("prometheusRestClient") RestClient restClient, ThemisProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public boolean supports(String conditionType) {
        String prometheusCondition = properties.ontology().moaNamespace() + OntologyConstants.CLASS_PROMETHEUS_CONDITION;
        return prometheusCondition.equals(conditionType);
    }

    @Override
    public boolean evaluate(ActionData.ConditionData condition) {
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/query")
                            .queryParam("query", condition.policy())
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, resp) -> {
                        throw new ConditionEvaluationException("Prometheus query failed with status: " + resp.getStatusCode());
                    })
                    .body(JsonNode.class);

            if (response == null || !"success".equals(response.get("status").asText())) {
                throw new ConditionEvaluationException("Prometheus query was not successful");
            }

            JsonNode result = response.get("data").get("result");
            return result.isArray() && !result.isEmpty();
        } catch (Exception e) {
            if (e instanceof ConditionEvaluationException) {
                throw e;
            }
            throw new ConditionEvaluationException("Error evaluating Prometheus condition: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=PrometheusConditionEvaluatorTest -f modules/themis/pom.xml`
Expected: PASS

- [ ] **Step 5: Commit implementation**

```bash
git add modules/themis/src/main/java/com/kubiki/themis/condition/impl/PrometheusConditionEvaluator.java \
        modules/themis/src/test/java/com/kubiki/themis/condition/impl/PrometheusConditionEvaluatorTest.java
git commit -m "feat(themis): implement PrometheusConditionEvaluator"
```
