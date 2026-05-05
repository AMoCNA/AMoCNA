# Themis Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Themis module in Java 25, providing gRPC-based autonomic action execution and knowledge retrieval from GraphDB using MoaMont ontology.

**Architecture:** Shared Semantic Core approach. Themis acts as a gRPC server that queries GraphDB (via RDF4J) for MoaMont action individuals and executes them (Simple or Complex Sagas).

**Tech Stack:** Java 25, Maven, Spring Boot 3.4, gRPC (yidongnan starter), RDF4J, JUnit 5.

---

### Task 1: Project Scaffolding (Spring Boot & Multi-profile Config)

**Files:**
- Create: `modules/themis/pom.xml`
- Create: `modules/themis/src/main/java/com/kubiki/themis/ThemisApplication.java`
- Create: `modules/themis/src/main/resources/application.yml`
- Create: `modules/themis/src/main/resources/application-dev.yml`
- Create: `modules/themis/src/main/resources/application-prod.yml`
- Create: `modules/themis/src/main/java/com/kubiki/themis/config/ThemisProperties.java`

- [ ] **Step 1: Create `modules/themis/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>3.4.0</version>
		<relativePath/>
	</parent>
	<groupId>com.kubiki</groupId>
	<artifactId>themis</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>themis</name>
	<description>Themis Autonomic Action Executor</description>

	<properties>
		<java.version>25</java.version>
		<grpc-spring-boot-starter.version>3.1.0.RELEASE</grpc-spring-boot-starter.version>
		<rdf4j.version>4.3.9</rdf4j.version>
		<graphdb.version>10.8.13</graphdb.version>
	</properties>

	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter</artifactId>
		</dependency>
		<dependency>
			<groupId>net.devh</groupId>
			<artifactId>grpc-server-spring-boot-starter</artifactId>
			<version>${grpc-spring-boot-starter.version}</version>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-configuration-processor</artifactId>
			<optional>true</optional>
		</dependency>

		<!-- RDF4J / GraphDB -->
		<dependency>
			<groupId>com.ontotext.graphdb</groupId>
			<artifactId>graphdb-runtime</artifactId>
			<version>${graphdb.version}</version>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.xolstice.maven.plugins</groupId>
				<artifactId>protobuf-maven-plugin</artifactId>
				<version>0.6.1</version>
				<configuration>
					<protocArtifact>com.google.protobuf:protoc:3.25.3:exe:${os.detected.classifier}</protocArtifact>
					<pluginId>grpc-java</pluginId>
					<pluginArtifact>io.grpc:protoc-gen-grpc-java:1.62.2:exe:${os.detected.classifier}</pluginArtifact>
				</configuration>
				<executions>
					<execution>
						<goals>
							<goal>compile</goal>
							<goal>compile-custom</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>
</project>
```

- [ ] **Step 2: Create `ThemisApplication.java` and `ThemisProperties.java`**

```java
// ThemisProperties.java
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
    @NestedConfigurationProperty Kubernetes kubernetes
) {
    public record GraphDB(String url, String repositoryId) {}
    public record Kubernetes(String managementUrl) {}
}

// ThemisApplication.java
package com.kubiki.themis;

import com.kubiki.themis.config.ThemisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ThemisProperties.class)
public class ThemisApplication {
    public static void main(String[] args) {
        SpringApplication.run(ThemisApplication.class, args);
    }
}
```

- [ ] **Step 3: Create multi-profile YAML configuration**

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
  kubernetes:
    management-url: http://localhost:8080
```

`application-prod.yml`:
```yaml
themis:
  graphdb:
    url: ${GRAPHDB_URL:http://graphdb:7200}
    repository-id: ${GRAPHDB_REPO:moamont}
  kubernetes:
    management-url: ${K8S_MGMT_URL:http://kubernetes-management:8080}
```

- [ ] **Step 4: Verify build**

Run: `mvn clean compile` in `modules/themis`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add modules/themis
git commit -m "feat(themis): initialize spring boot 3.4 with multi-profile config"
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

### Task 3: GraphDB Gateway (Spring Bean)

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/knowledge/GraphDBGateway.java`

- [ ] **Step 1: Implement `GraphDBGateway` as a Spring Service**

```java
package com.kubiki.themis.knowledge;

import com.kubiki.themis.config.ThemisProperties;
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
    private static final String NAMESPACE = "http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#";

    public GraphDBGateway(ThemisProperties properties) {
        this.repository = new HTTPRepository(properties.graphdb().url(), properties.graphdb().repositoryId());
    }

    @PostConstruct
    public void init() {
        this.repository.init();
    }

    public List<String> findActionsForResource(String resourceId) {
        String sparql = "PREFIX moa: <" + NAMESPACE + "> " +
                        "SELECT ?action WHERE { " +
                        "  ?action moa:targetsEntity <" + resourceId + "> . " +
                        "  ?action a moa:AutonomicAction . " +
                        "}";
        
        List<String> actions = new ArrayList<>();
        try (RepositoryConnection conn = repository.getConnection()) {
            TupleQuery query = conn.prepareTupleQuery(sparql);
            try (TupleQueryResult result = query.evaluate()) {
                while (result.hasNext()) {
                    actions.add(result.next().getValue("action").stringValue());
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

- [ ] **Step 2: Commit**

```bash
git add modules/themis/src/main/java/com/kubiki/themis/knowledge/GraphDBGateway.java
git commit -m "feat(themis): implement GraphDBGateway as a Spring Service"
```

---

### Task 4: Action Executors (Spring Integrated)

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/execution/ActionExecutor.java`
- Create: `modules/themis/src/main/java/com/kubiki/themis/execution/impl/DeletePodExecutor.java`

- [ ] **Step 1: Define `ActionExecutor`**

```java
package com.kubiki.themis.execution;

public interface ActionExecutor {
    boolean execute(String targetId);
    boolean compensate(String targetId);
    String getActionType();
}
```

- [ ] **Step 2: Implement `DeletePodExecutor` using `RestTemplate` or `WebClient`**

```java
package com.kubiki.themis.execution.impl;

import com.kubiki.themis.execution.ActionExecutor;
import com.kubiki.themis.config.ThemisProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DeletePodExecutor implements ActionExecutor {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String managementUrl;

    public DeletePodExecutor(ThemisProperties properties) {
        this.managementUrl = properties.kubernetes().managementUrl();
    }

    @Override
    public boolean execute(String targetId) {
        String[] parts = targetId.split("/");
        if (parts.length != 2) return false;

        String url = String.format("%s/kubernetes/management/pod/delete?namespace=%s&podName=%s",
                managementUrl, parts[0], parts[1]);

        try {
            restTemplate.getForObject(url, String.class);
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

- [ ] **Step 3: Commit**

```bash
git add modules/themis/src/main/java/com/kubiki/themis/execution
git commit -m "feat(themis): implement DeletePodExecutor as a Spring Component"
```

---

### Task 5: Saga Engine (Virtual Threads Optimized)

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/saga/SagaEngine.java`

- [ ] **Step 1: Implement `SagaEngine`**

- [ ] **Step 2: Commit**

---

### Task 6: gRPC Service Implementation

**Files:**
- Create: `modules/themis/src/main/java/com/kubiki/themis/grpc/ActionServiceImpl.java`

- [ ] **Step 1: Implement `ActionServiceImpl` using `@GrpcService`**

- [ ] **Step 2: Final Verification**

Run: `mvn clean install`
Expected: BUILD SUCCESS
