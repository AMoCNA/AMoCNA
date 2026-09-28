package com.kubiki.metis.knowledge;

/**
 * Constants for CNEEOnt — local names of all classes, properties, and Kubernetes
 * kinds used across the codebase.
 *
 * <p>Always use these constants instead of string literals to keep the mapping
 * between Kubernetes resources and CNEEOnt vocabulary in one place.
 */
public final class CneeOntology {

    public static final String DEFAULT_NAMESPACE =
            "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/";

    // -------------------------------------------------------------------------
    // Default namespace (overridable via metis.ontology.cneeNamespace).
    // -------------------------------------------------------------------------
    public static final String KIND_POD = "Pod";

    // -------------------------------------------------------------------------
    // Kubernetes kinds — used as IRI prefixes by IriFactory.
    // -------------------------------------------------------------------------
    public static final String KIND_SERVICE = "Service";
    public static final String KIND_NODE = "Node";
    public static final String KIND_REPLICSET = "ReplicaSet";
    public static final String KIND_DEPLOYMENT = "Deployment";
    public static final String KIND_STATEFULSET = "StatefulSet";
    public static final String KIND_PV = "PersistentVolume";
    public static final String KIND_PVC = "PersistentVolumeClaim";
    public static final String KIND_CONTAINER = "Container";
    public static final String KIND_IMAGE = "Image";
    public static final String KIND_IMAGE_REGISTRY = "ImageRegistry";
    public static final String KIND_SECRET = "Secret";
    public static final String CLASS_EXECUTION_UNIT = "ExecutionUnit";
    public static final String CLASS_CONTAINER = "Container";
    public static final String CLASS_IMAGE = "Image";
    public static final String CLASS_IMAGE_REGISTRY = "ImageRegistry";
    public static final String CLASS_SECRET = "Secret";

    // -------------------------------------------------------------------------
    // CNEEOnt classes — local names.
    // -------------------------------------------------------------------------
    public static final String CLASS_SERVICE = "Service";
    public static final String CLASS_NODE = "Node";
    public static final String CLASS_METRIC = "Metric";
    public static final String CLASS_STATELESS_CONTROLLER = "StatelessWorkloadController";
    public static final String CLASS_STATEFUL_CONTROLLER = "StatefulWorkloadController";
    public static final String CLASS_PERSISTENT_STORAGE = "PersistentStorageResource";
    public static final String CLASS_INFRASTRUCTURE_LAYER = "InfrastructureLayerElement";
    public static final String CLASS_CONTAINERIZATION_LAYER = "ContainerizationLayerElement";
    public static final String CLASS_APPLICATION_LAYER = "ApplicationLayerElement";
    public static final String STATE_PENDING = "ExecutionUnitPending";

    // -------------------------------------------------------------------------
    // Pod phase → CNEEOnt state class local name.
    // -------------------------------------------------------------------------
    public static final String STATE_RUNNING = "ExecutionUnitRunning";
    public static final String STATE_FAILED = "ExecutionUnitFailed";
    public static final String STATE_SUCCEEDED = "ExecutionUnitSucceeded";
    public static final String STATE_UNKNOWN = "ExecutionUnitUnknown";
    public static final String STATE_CONTAINER_CRASH_LOOP = "ContainerCrashLoopBackOffState";

    // -------------------------------------------------------------------------
    // Container anomaly states — CNEEOnt State subclasses; BridgeOnt marks them Anomaly.
    // -------------------------------------------------------------------------
    public static final String STATE_CONTAINER_LIVENESS_FAILED = "ContainerLivenessProbeFailedState";
    public static final String STATE_CONTAINER_READINESS_FAILED = "ContainerReadinessProbeFailedState";
    public static final String STATE_CONTAINER_OOM_KILLED = "ContainerOOMKilledState";
    public static final String STATE_CONTAINER_CPU_THROTTLED = "ContainerCPUThrottledState";
    public static final String STATE_CONTAINER_MEMORY_LEAK = "ContainerMemoryLeakDetectedState";
    public static final String STATE_POD_EVICTED = "ExecutionUnitEvictedState";

    // -------------------------------------------------------------------------
    // Pod anomaly states — CNEEOnt State subclasses; BridgeOnt marks them Anomaly.
    // -------------------------------------------------------------------------
    public static final String STATE_IMAGE_PULL_BACKOFF = "ImagePullBackOffState";
    public static final String STATE_NODE_NOT_READY = "NodeNotReadyState";

    // -------------------------------------------------------------------------
    // Node anomaly states — CNEEOnt State subclasses; BridgeOnt marks them Anomaly.
    // -------------------------------------------------------------------------
    public static final String STATE_NODE_CPU_SATURATED = "NodeCPUSaturatedState";
    public static final String STATE_NODE_MEMORY_STARVED = "NodeMemoryStarvedState";
    public static final String PROP_CONTAINS = "contains";
    public static final String PROP_USES_IMAGE = "usesImage";
    public static final String PROP_PULLS_IMAGE_FROM = "pullsImageFrom";
    public static final String PROP_AUTHENTICATES_WITH = "authenticatesWith";
    public static final String PROP_GRANTS_ACCESS_TO = "grantsAccessTo";
    public static final String PROP_VERSION = "version";
    public static final String PROP_SECRET_TYPE = "secretType";
    public static final String PROP_NAMESPACE = "namespace";

    // -------------------------------------------------------------------------
    // CNEEOnt object properties — local names.
    // -------------------------------------------------------------------------
    public static final String PROP_IS_PART_OF = "isPartOf";
    public static final String PROP_HOSTS = "hosts";
    public static final String PROP_IS_HOSTED_ON = "isHostedOn";
    public static final String PROP_IS_REALIZED_BY = "isRealizedBy";
    public static final String PROP_USES_STORAGE = "usesStorage";
    public static final String PROP_COMMUNICATES_WITH = "communicatesWith";
    public static final String PROP_HAS_STATE = "hasState";
    public static final String PROP_EMITS_TELEMETRY = "emitsTelemetry";
    public static final String PROP_RESOURCE_ID = "resourceID";

    // -------------------------------------------------------------------------
    // CNEEOnt data properties — local names.
    // -------------------------------------------------------------------------
    public static final String PROP_RESOURCE_NAME = "resourceName";
    public static final String PROP_METRICS_ENDPOINT = "metricsEndpoint";

    private CneeOntology() {
    }
}
