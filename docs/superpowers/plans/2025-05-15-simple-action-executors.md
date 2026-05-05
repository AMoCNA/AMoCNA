# Simple Action Executors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `ActionExecutor` interface and `DeletePodExecutor` implementation in the Themis module.

**Architecture:** Interface-based design with Spring `@Component` implementations using `RestTemplate` for external communication.

**Tech Stack:** Java 25, Spring Boot 3.4.0, Maven.

---

### Task 1: Update Dependency

**Files:**
- Modify: `modules/themis/pom.xml`

- [ ] **Step 1: Add spring-boot-starter-web dependency**

Add the following to the `<dependencies>` section:
```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>
```

- [ ] **Step 2: Run mvn clean compile to verify**

Run: `mvn clean compile -f modules/themis/pom.xml`
Expected: Success

- [ ] **Step 3: Commit**

```bash
git add modules/themis/pom.xml
git commit -m "build(themis): add spring-boot-starter-web dependency"
```

---

### Task 2: Define ActionExecutor Interface

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/execution/ActionExecutor.java`

- [ ] **Step 1: Create the ActionExecutor interface**

```java
package com.kubiki.themis.execution;

public interface ActionExecutor {
    boolean execute(String targetId);
    boolean compensate(String targetId);
    String getActionType();
}
```

- [ ] **Step 2: Run mvn compile to verify**

Run: `mvn compile -f modules/themis/pom.xml`
Expected: Success

- [ ] **Step 3: Commit**

```bash
git add modules/themis/src/main/java/com/kubiki/themis/execution/ActionExecutor.java
git commit -m "feat(themis): define ActionExecutor interface"
```

---

### Task 3: Implement DeletePodExecutor

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/execution/impl/DeletePodExecutor.java`

- [ ] **Step 1: Create the DeletePodExecutor implementation**

```java
package com.kubiki.themis.execution.impl;

import com.kubiki.themis.execution.ActionExecutor;
import com.kubiki.themis.config.ThemisProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

@Component
public class DeletePodExecutor implements ActionExecutor {
    private final RestTemplate restTemplate;
    private final String managementUrl;

    public DeletePodExecutor(ThemisProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.managementUrl = properties.kubernetes().managementUrl();
        this.restTemplate = restTemplateBuilder.build();
    }

    @Override
    public boolean execute(String targetId) {
        // targetId format: namespace/podName
        String[] parts = targetId.split("/");
        if (parts.length != 2) return false;

        String url = String.format("%s/kubernetes/management/pod/delete?namespace=%s&podName=%s",
                managementUrl, parts[0], parts[1]);

        try {
            restTemplate.getForObject(url, Object.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean compensate(String targetId) {
        return true;
    }

    @Override
    public String getActionType() {
        return "DeletePodAction";
    }
}
```

- [ ] **Step 2: Run mvn compile to verify**

Run: `mvn compile -f modules/themis/pom.xml`
Expected: Success

- [ ] **Step 3: Commit**

```bash
git add modules/themis/src/main/java/com/kubiki/themis/execution/impl/DeletePodExecutor.java
git commit -m "feat(themis): implement DeletePodExecutor as a Spring Component"
```

---

### Task 4: Add Unit Tests for DeletePodExecutor

**Files:**
- Create: `modules/themis/src/test/java/com/kubiki/themis/execution/impl/DeletePodExecutorTest.java`

- [ ] **Step 1: Write failing test (TDD)**

```java
package com.kubiki.themis.execution.impl;

import com.kubiki.themis.config.ThemisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class DeletePodExecutorTest {
    private DeletePodExecutor executor;
    private MockRestServiceServer mockServer;
    private String managementUrl = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        ThemisProperties.Kubernetes kubernetes = new ThemisProperties.Kubernetes(managementUrl);
        ThemisProperties properties = new ThemisProperties(null, kubernetes);
        
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        
        RestTemplateBuilder builder = new RestTemplateBuilder() {
            @Override
            public RestTemplate build() {
                return restTemplate;
            }
        };
        
        executor = new DeletePodExecutor(properties, builder);
    }

    @Test
    void shouldReturnFalseForInvalidTargetId() {
        assertFalse(executor.execute("invalid-id"));
    }

    @Test
    void shouldReturnTrueWhenDeleteSucceeds() {
        mockServer.expect(requestTo(managementUrl + "/kubernetes/management/pod/delete?namespace=ns&podName=pod"))
                .andRespond(withSuccess());
        
        assertTrue(executor.execute("ns/pod"));
        mockServer.verify();
    }

    @Test
    void shouldReturnFalseWhenDeleteFails() {
        mockServer.expect(requestTo(managementUrl + "/kubernetes/management/pod/delete?namespace=ns&podName=pod"))
                .andRespond(withServerError());
        
        assertFalse(executor.execute("ns/pod"));
        mockServer.verify();
    }

    @Test
    void shouldReturnCorrectActionType() {
        assertEquals("DeletePodAction", executor.getActionType());
    }
}
```

- [ ] **Step 2: Run tests to verify**

Run: `mvn test -f modules/themis/pom.xml -Dtest=DeletePodExecutorTest`
Expected: Pass

- [ ] **Step 3: Commit**

```bash
git add modules/themis/src/test/java/com/kubiki/themis/execution/impl/DeletePodExecutorTest.java
git commit -m "test(themis): add unit tests for DeletePodExecutor"
```
