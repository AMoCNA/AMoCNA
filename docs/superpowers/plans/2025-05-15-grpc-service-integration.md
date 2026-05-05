# Task 6: gRPC Service Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the gRPC `ActionService` by connecting it to `GraphDBGateway` and `ActionDispatcher`.

**Architecture:** Refactor `ActionServiceImpl` to use `ActionDispatcher` for executing actions and `GraphDBGateway` for fetching executable actions.

**Tech Stack:** Java 25, Spring Boot 3.4, gRPC, Spring Boot Starter gRPC.

---

### Task 1: Refactor ActionServiceImpl

**Files:**
- Modify: `modules/themis/src/main/java/com/kubiki/themis/grpc/ActionServiceImpl.java`

- [ ] **Step 1: Update imports and dependencies**
Remove `ActionExecutor`, `SagaEngine`, `Map`, `Function`, `Collectors`.
Add `ActionDispatcher`.

- [ ] **Step 2: Update constructor and fields**
Replace `Map<String, ActionExecutor> executors` with `ActionDispatcher actionDispatcher`.
Update constructor to inject `ActionDispatcher`.

- [ ] **Step 3: Update getExecutableActions**
Update the mapping logic to handle different action types.

- [ ] **Step 4: Update validatePreconditions**
Implement a placeholder validation response.

- [ ] **Step 5: Update executeRemediation**
Use `ActionDispatcher` to execute the action. Note that `executeRemediation` returns a stream in proto but the user's snippet doesn't seem to use `StreamObserver` as a stream (it calls `onNext` multiple times but it's a server streaming RPC).

Wait, the proto says:
`rpc ExecuteRemediation (ActionRequest) returns (stream ExecutionStatus);`
The user's code:
```java
    @Override
    public void executeRemediation(ActionRequest request, StreamObserver<ExecutionStatus> responseObserver) {
        // ...
        responseObserver.onNext(ExecutionStatus.newBuilder()
                .setStep(request.getActionId())
                .setState("IN_PROGRESS")
                .setMessage("Ingesting Ground Truth and executing...")
                .build());

        boolean success = actionDispatcher.dispatch(mockAction);

        responseObserver.onNext(ExecutionStatus.newBuilder()
                .setStep(request.getActionId())
                .setState(success ? "SUCCESS" : "FAILED")
                .setMessage(success ? "Autonomic action completed" : "Action failed")
                .build());
        
        responseObserver.onCompleted();
    }
```
This correctly handles the stream by calling `onNext` multiple times.

### Task 2: Verification

- [ ] **Step 1: Run Maven build**
Run: `mvn clean install -DskipTests` in `modules/themis` to ensure compilation and gRPC code generation.
Expected: Build SUCCESS (ignore repackage errors if any).

- [ ] **Step 2: Run all tests**
Run: `mvn test` in `modules/themis`.
Expected: All tests pass.

### Task 3: Commit

- [ ] **Step 1: Commit changes**
Commit message: `feat(themis): finalize gRPC service integration with autonomic dispatcher`
