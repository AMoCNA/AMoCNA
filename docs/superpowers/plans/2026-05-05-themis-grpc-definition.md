# Themis gRPC Interface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Define the gRPC interface for the Themis module to allow other components to interact with it for action execution and validation.

**Architecture:** Use Protocol Buffers (proto3) to define `ActionService` with methods for getting executable actions, validating preconditions, and executing remediation with streaming status updates.

**Tech Stack:** gRPC, Protobuf, Java, Maven.

---

### Task 1: Create gRPC Interface Definition

**Files:**
- Create: `modules/themis/src/main/proto/themis.proto`

- [ ] **Step 1: Create `themis.proto` with the defined service and messages.**

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
  string type = 2; // SimpleAction or ComplexWorkflow
  string functional_intent = 3;
}

message ValidationResponse {
  bool valid = 1;
  string message = 2;
}

message ExecutionStatus {
  string step = 1;
  string state = 2; // IN_PROGRESS, SUCCESS, FAILED, COMPENSATING, REVERTED
  string message = 3;
}
```

### Task 2: Verify Compilation and Code Generation

**Files:**
- Modify: `modules/themis/pom.xml` (if needed for os-maven-plugin)

- [ ] **Step 1: Run Maven compile to trigger code generation.**

Run: `mvn compile` in `modules/themis`
Expected: SUCCESS

- [ ] **Step 2: Verify generated sources.**

Check: `modules/themis/target/generated-sources/protobuf/java` exists and contains generated classes.

### Task 3: Commit Changes

- [ ] **Step 1: Commit the changes.**

```bash
git add modules/themis/src/main/proto/themis.proto
git commit -m "feat(themis): define ActionService gRPC interface"
```
