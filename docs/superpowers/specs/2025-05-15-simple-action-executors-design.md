# Task 4: Simple Action Executors (Spring Integrated) Design

## Goal
Implement a pluggable action execution system for Themis, starting with a `DeletePodExecutor` that communicates with an external Kubernetes management service.

## Architecture
- **Interface-based design**: `ActionExecutor` defines the contract for all autonomic actions.
- **Spring Integration**: Executors are implemented as Spring `@Component`s for easy discovery and injection.
- **REST Communication**: `DeletePodExecutor` uses `RestTemplate` to trigger actions in the `KubernetesManagement` service.
- **Configuration-driven**: Uses `ThemisProperties` to resolve external service URLs.

## Components
### 1. `ActionExecutor` (Interface)
- `execute(String targetId)`: Performs the action.
- `compensate(String targetId)`: Attempts to undo the action (best-effort).
- `getActionType()`: Returns a unique string identifier for the action type.

### 2. `DeletePodExecutor` (Implementation)
- Implements `ActionExecutor`.
- Dependencies: `RestTemplate` (via `RestTemplateBuilder`), `ThemisProperties`.
- Behavior: Splits `targetId` (namespace/podName) and calls the management service.

## Data Flow
1. Themis determines a `DeletePodAction` is needed for a specific pod.
2. The `DeletePodExecutor` is invoked with the `targetId`.
3. `DeletePodExecutor` constructs the URL using `ThemisProperties.kubernetes().managementUrl()`.
4. `DeletePodExecutor` sends a GET request to the management service.
5. Success/failure is returned based on the HTTP response.

## Error Handling
- Invalid `targetId` format returns `false`.
- HTTP exceptions during the REST call are caught and return `false`.

## Testing Strategy
- Unit tests for `DeletePodExecutor` using `MockRestServiceServer` or mocking `RestTemplate`.
- Integration test (optional) verifying Spring context loading and bean discovery.

## Dependency Changes
- Add `spring-boot-starter-web` to `modules/themis/pom.xml` to provide `RestTemplate` and `RestTemplateBuilder`.
