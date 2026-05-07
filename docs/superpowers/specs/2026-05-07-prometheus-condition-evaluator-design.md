# Design: PrometheusConditionEvaluator

## 1. Architecture
- **Component**: `com.kubiki.themis.condition.impl.PrometheusConditionEvaluator`
- **Interface**: Implements `com.kubiki.themis.condition.ConditionEvaluator`.
- **Communication**: Uses a dedicated `RestClient` bean (`prometheusRestClient`) to interact with the Prometheus Instant Query API (`/api/v1/query`).
- **Configuration**: Managed via `ThemisProperties` and a new `PrometheusConfig` class.

## 2. Configuration (ThemisProperties)
Update `ThemisProperties.java` to include:
```java
public record Prometheus(String url) {}
```
And add it as a nested record:
```java
public record ThemisProperties(
    @NestedConfigurationProperty GraphDB graphdb,
    @NestedConfigurationProperty Ontology ontology,
    @NestedConfigurationProperty Prometheus prometheus
) { ... }
```

## 3. Constants (OntologyConstants)
Add to `OntologyConstants.java`:
```java
public static final String CLASS_PROMETHEUS_CONDITION = "PrometheusCondition";
```

## 4. Execution Logic
1. **Support Check**: `supports(conditionType)` returns true if `conditionType.equals(moaNamespace + "PrometheusCondition")`.
2. **Query Execution**:
   - URL: `{prometheusUrl}/api/v1/query?query={policy}`.
   - Method: GET.
3. **Response Parsing**:
   - Use `JsonNode` for dynamic parsing.
   - Check `status == "success"`.
   - Result: `true` if `data.result` is a non-empty array; `false` otherwise.
4. **Error Handling**:
   - **Fail-Fast**: Throw `ConditionEvaluationException` (new or existing) for non-2xx responses or network failures.
   - Prometheus errors (e.g., status != "success") also result in an exception.

## 5. Components
- `PrometheusConfig`: Spring `@Configuration` to define the `RestClient` bean.
- `PrometheusConditionEvaluator`: The core logic component.
- `ConditionEvaluationException`: Exception class for evaluation failures (if not already existing, otherwise reuse).

## 6. Testing
- **Unit Test**: `PrometheusConditionEvaluatorTest` using `MockRestServiceServer`.
- **Test Cases**:
  - Valid query with results -> returns `true`.
  - Valid query with NO results -> returns `false`.
  - Prometheus returns 400 Bad Request -> throws `ConditionEvaluationException`.
  - Prometheus returns 500 Internal Server Error -> throws `ConditionEvaluationException`.
  - Prometheus is unreachable -> throws `ConditionEvaluationException`.
