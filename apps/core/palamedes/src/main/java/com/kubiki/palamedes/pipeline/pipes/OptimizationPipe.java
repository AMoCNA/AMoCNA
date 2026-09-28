package com.kubiki.palamedes.pipeline.pipes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.common.logging.LogLoopStep;
import com.kubiki.common.logging.LoopPhase;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.config.QueriesLoader;
import com.kubiki.palamedes.config.QueryDefinition;
import com.kubiki.palamedes.model.*;
import com.kubiki.palamedes.pipeline.MapePipe;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import com.kubiki.palamedes.pipeline.pipes.rules.*;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OptimizationPipe (MAPE-Plan):
 * Heuristic scheduler utilizing MoaMont data properties and Prometheus metrics.
 */
@Order(2)
@Component
public class OptimizationPipe implements MapePipe {
    private static final Logger log = LoggerFactory.getLogger(OptimizationPipe.class);

    // Indices for capacity array
    private static final int INFRA_CAP_INDEX = 0;
    private static final int CONTAINER_CAP_INDEX = 1;
    private static final int APP_CAP_INDEX = 2;
    private static final int TOTAL_LAYERS_COUNT = 3;

    private static final String STATE_IN_PROGRESS = "State_InProgress";
    private static final String INFRASTRUCTURE_LAYER = "Infrastructure";
    private static final String CONTAINERIZATION_LAYER = "Containerization";

    // Healing bias variables
    private static final double HEALING_BIAS = 1.0;
    private static final double NON_HEALING_BIAS = 0.5;
    private static final int MIN_PRIORITY = 1;

    // Math/Formula base values
    private static final double BASE_COST_MULTIPLIER = 1.0;
    private static final float DEFAULT_RISK_MULTIPLIER = 1.0f;

    // Telemetry default / check index values
    private static final double ERROR_METRIC_VALUE = -1.0;
    private static final int MIN_VALUE_NODE_SIZE = 2;
    private static final int METRIC_VALUE_INDEX = 1;

    private static final String METRIC_API_PATH = "/api/v1/query";
    private static final String METRIC_QUERY_PARAM = "query";
    private static final String STATUS_SUCCESS = "success";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_VALUE = "value";

    private final GraphDBGateway graphDBGateway;
    private final StateRepository stateRepository;
    private final WorkflowStateMapper mapper;
    private final RestClient prometheusClient;
    private final ObjectMapper objectMapper;
    private final PalamedesProperties palamedesProperties;
    private final QueriesLoader queriesLoader;

    // Specification Pattern Scheduling Rules
    private final Specification<SchedulingTarget> schedulingSpec;

    @org.springframework.beans.factory.annotation.Autowired
    public OptimizationPipe(
            GraphDBGateway graphDBGateway,
            StateRepository stateRepository,
            WorkflowStateMapper mapper,
            RestClient.Builder restClientBuilder,
            AmocnaCommonProperties properties,
            PalamedesProperties palamedesProperties,
            QueriesLoader queriesLoader,
            ObjectMapper objectMapper,
            List<Specification<SchedulingTarget>> specifications
    ) {
        this.graphDBGateway = graphDBGateway;
        this.stateRepository = stateRepository;
        this.mapper = mapper;
        this.palamedesProperties = palamedesProperties;
        this.queriesLoader = queriesLoader;
        this.objectMapper = objectMapper;

        String url = properties.prometheus() != null ? properties.prometheus().url() : null;
        if (url != null && !url.isBlank()) {
            this.prometheusClient = restClientBuilder.baseUrl(url).build();
            log.info("OptimizationPipe initialized with Prometheus URL: {}", url);
        } else {
            this.prometheusClient = null;
            log.warn("OptimizationPipe: Prometheus URL is not configured. Telemetry queries will fall back to default values.");
        }

        // Initialize scheduling rules using the Specification Pattern (dynamically composed)
        Specification<SchedulingTarget> combined = null;
        for (Specification<SchedulingTarget> spec : specifications) {
            if (combined == null) {
                combined = spec;
            } else {
                combined = combined.and(spec);
            }
        }
        this.schedulingSpec = combined != null ? combined : target -> true;
    }

    private PalamedesProperties.Scheduler getSchedulerConfig() {
        return (palamedesProperties != null && palamedesProperties.scheduler() != null)
                ? palamedesProperties.scheduler()
                : new PalamedesProperties.Scheduler();
    }

    @Override
    @LogLoopStep(
            phase = LoopPhase.PLAN,
            step = "Action Optimization",
            actionId = "#context.actionId().stringValue()",
            resource = "#context.metadata().get('resourceName') != null ? #context.metadata().get('resourceName').toString() : null",
            details = "'functionalIntent=' + #context.actionData().functionalIntent() + ', executionCost=' + #context.actionData().executionCost()"
    )
    public boolean process(WorkflowContext context) {
        String plannedStateFragment = mapper.getFragment(WorkflowState.PLANNED);
        String currentStateFragment = (String) context.metadata().get("currentState");

        if (!plannedStateFragment.equals(currentStateFragment)) {
            log.debug("OptimizationPipe: Action is not in State_Planned, skipping scheduling optimization");
            return true;
        }

        IRI currentActionId = context.actionId();
        ActionData currentAction = context.actionData();
        String currentResourceName = (String) context.metadata().get("resourceName");

        log.info("OptimizationPipe: Starting scheduling pipeline for action {} on resource {}", currentActionId, currentResourceName);

        // Fetch all active actions
        List<ActiveActionSummary> activeSummaries = graphDBGateway.findActiveActions();
        List<IRI> activeIris = activeSummaries.stream().map(ActiveActionSummary::actionIri).toList();

        // Ensure our current action is in the structures map
        Map<IRI, ActionData> structures = graphDBGateway.fetchActionStructures(activeIris);
        if (!structures.containsKey(currentActionId)) {
            structures.put(currentActionId, currentAction);
        }

        // Fetch all intent metadata
        List<IRI> intentIds = structures.values().stream()
                .map(ActionData::functionalIntent)
                .distinct()
                .toList();
        Map<IRI, IntentMetadata> intentMetadata = graphDBGateway.fetchIntentMetadata(intentIds);

        SchedulingState state = buildSchedulingState(activeSummaries, structures, intentMetadata, currentResourceName, currentActionId);

        // Execute rules via Specification Pattern!
        boolean pipelineResult = schedulingSpec.isSatisfiedBy(new SchedulingTarget(context, state));

        log.info("OptimizationPipe: Scheduling pipeline finished for action {} with result: {}", currentActionId, pipelineResult);
        return pipelineResult;
    }

    private SchedulingState buildSchedulingState(
            List<ActiveActionSummary> activeSummaries,
            Map<IRI, ActionData> structures,
            Map<IRI, IntentMetadata> intentMetadata,
            String currentResourceName,
            IRI currentActionId
    ) {
        Map<IRI, Double> dynamicCosts = new HashMap<>();
        Map<IRI, Double> densities = new HashMap<>();
        PalamedesProperties.Scheduler config = getSchedulerConfig();

        structures.forEach((actionId, data) -> {
            IntentMetadata metadata = intentMetadata.getOrDefault(data.functionalIntent(),
                    new IntentMetadata(data.functionalIntent(), DEFAULT_RISK_MULTIPLIER, Integer.MAX_VALUE, true));

            String actionResourceName = getResourceName(actionId, activeSummaries, currentActionId, currentResourceName);
            double cpuUtil = getCpuUtilization(actionResourceName);
            double memUtil = getMemUtilization(actionResourceName);

            double costStatic = data.executionCost();
            double costDynamic = Math.max(config.minDynamicCost(), costStatic * (BASE_COST_MULTIPLIER + config.alpha() * cpuUtil + config.beta() * memUtil) * metadata.riskMultiplier());
            dynamicCosts.put(actionId, costDynamic);

            double bias = metadata.isHealing() ? HEALING_BIAS : NON_HEALING_BIAS;
            double density = (Math.max(MIN_PRIORITY, data.priority()) * bias) / costDynamic;
            densities.put(actionId, density);
        });

        // Compute remaining capacities and current active counts per intent generically
        Map<String, Double> remainingCapacities = new HashMap<>();
        Map<String, Double> configuredCapacities = config.layerCapacities();
        if (configuredCapacities == null || configuredCapacities.isEmpty()) {
            configuredCapacities = Map.of(
                    "Infrastructure", config.infrastructureCapacity(),
                    "Containerization", config.containerizationCapacity(),
                    "Application", config.applicationCapacity()
            );
        }
        remainingCapacities.putAll(configuredCapacities);

        Map<IRI, Integer> intentCounts = new HashMap<>();

        activeSummaries.stream()
                .filter(active -> STATE_IN_PROGRESS.equals(active.stateFragment()) && !active.actionIri().equals(currentActionId))
                .forEach(active -> {
                    ActionData runningAction = structures.get(active.actionIri());
                    if (runningAction != null) {
                        double dynCost = dynamicCosts.getOrDefault(active.actionIri(), (double) runningAction.executionCost());
                        String layer = runningAction.layerBoundary() != null ? runningAction.layerBoundary().getLocalName() : "";

                        // Generic lookup
                        String matchedLayerKey = remainingCapacities.keySet().stream()
                                .filter(layerKey -> layer.contains(layerKey))
                                .findFirst()
                                .orElse("Application");

                        remainingCapacities.put(matchedLayerKey, remainingCapacities.getOrDefault(matchedLayerKey, 0.0) - dynCost);
                        intentCounts.merge(runningAction.functionalIntent(), MIN_PRIORITY, Integer::sum);
                    }
                });

        double infraCap = remainingCapacities.getOrDefault("Infrastructure", config.infrastructureCapacity());
        double containerCap = remainingCapacities.getOrDefault("Containerization", config.containerizationCapacity());
        double appCap = remainingCapacities.getOrDefault("Application", config.applicationCapacity());

        Map<SchedulingState.ResourcePair, Boolean> dependencyCache = new java.util.HashMap<>();
        java.util.function.BiPredicate<IRI, IRI> dependencyChecker = (src, tgt) -> {
            if (src == null || tgt == null) return false;
            return dependencyCache.computeIfAbsent(new SchedulingState.ResourcePair(src, tgt),
                    pair -> graphDBGateway.isDependentResource(pair.source(), pair.target()));
        };

        return new SchedulingState(
                activeSummaries,
                structures,
                intentMetadata,
                dynamicCosts,
                densities,
                intentCounts,
                infraCap,
                containerCap,
                appCap,
                remainingCapacities,
                dependencyChecker
        );
    }

    private String getResourceName(IRI actionId, List<ActiveActionSummary> activeSummaries, IRI currentActionId, String currentResourceName) {
        if (actionId.equals(currentActionId)) {
            return currentResourceName;
        }
        for (ActiveActionSummary summary : activeSummaries) {
            if (summary.actionIri().equals(actionId)) {
                return summary.resourceName();
            }
        }
        return null;
    }

    private double getCpuUtilization(String resourceName) {
        PalamedesProperties.Scheduler config = getSchedulerConfig();
        double defaultUtil = config.defaultUtilization();
        if (prometheusClient == null || resourceName == null) {
            return defaultUtil;
        }
        QueryDefinition cpuQuery = queriesLoader.getQuery("cpu");
        if (cpuQuery == null || cpuQuery.nodeQueryTemplate() == null || cpuQuery.fallbackQuery() == null) {
            log.warn("CPU query definition is missing or incomplete in configuration. Using default utilization.");
            return defaultUtil;
        }
        String query = String.format(cpuQuery.nodeQueryTemplate(), resourceName);
        double val = getMetricValue(query, ERROR_METRIC_VALUE);
        return val >= 0.0 ? val : getMetricValue(cpuQuery.fallbackQuery(), defaultUtil);
    }

    private double getMemUtilization(String resourceName) {
        PalamedesProperties.Scheduler config = getSchedulerConfig();
        double defaultUtil = config.defaultUtilization();
        if (prometheusClient == null || resourceName == null) {
            return defaultUtil;
        }
        QueryDefinition memQuery = queriesLoader.getQuery("memory");
        if (memQuery == null || memQuery.nodeQueryTemplate() == null || memQuery.fallbackQuery() == null) {
            log.warn("Memory query definition is missing or incomplete in configuration. Using default utilization.");
            return defaultUtil;
        }
        String query = String.format(memQuery.nodeQueryTemplate(), resourceName, resourceName);
        double val = getMetricValue(query, ERROR_METRIC_VALUE);
        return val >= 0.0 ? val : getMetricValue(memQuery.fallbackQuery(), defaultUtil);
    }

    private double getMetricValue(String query, double defaultValue) {
        if (prometheusClient == null) {
            return defaultValue;
        }
        try {
            String responseBody = prometheusClient.get()
                    .uri(uriBuilder -> uriBuilder.path(METRIC_API_PATH)
                            .queryParam(METRIC_QUERY_PARAM, "{query}")
                            .build(query))
                    .retrieve()
                    .body(String.class);
            if (responseBody == null) {
                return defaultValue;
            }
            JsonNode response = objectMapper.readTree(responseBody);
            if (response.has(FIELD_STATUS) && STATUS_SUCCESS.equals(response.get(FIELD_STATUS).asText())) {
                JsonNode result = response.path(FIELD_DATA).path(FIELD_RESULT);
                if (result.isArray() && !result.isEmpty()) {
                    JsonNode valueNode = result.get(0).path(FIELD_VALUE);
                    if (valueNode.isArray() && valueNode.size() >= MIN_VALUE_NODE_SIZE) {
                        return Double.parseDouble(valueNode.get(METRIC_VALUE_INDEX).asText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch metric for query {}: {}", query, e.getMessage());
        }
        return defaultValue;
    }
}
