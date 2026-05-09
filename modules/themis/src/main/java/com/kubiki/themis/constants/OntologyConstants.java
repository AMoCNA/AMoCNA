package com.kubiki.themis.constants;

public final class OntologyConstants {
    private OntologyConstants() {}
    public static final String CLASS_PROMETHEUS_CONDITION = "PrometheusCondition";
    public static final String CLASS_STATE_BASED_CONDITION = "StateBasedCondition";
    public static final String CLASS_AUTONOMIC_ACTION = "AutonomicAction";
    public static final String CLASS_SIMPLE_ACTION = "SimpleAction";
    public static final String CLASS_COMPLEX_WORKFLOW = "ComplexWorkflow";
    
    public static final String PROP_HAS_EXECUTION_STATUS = "hasExecutionStatus";
    public static final String PROP_HAS_PRE_CONDITION = "hasPreCondition";
    public static final String PROP_HAS_POST_CONDITION = "hasPostCondition";
    public static final String PROP_IS_DECOMPOSED_INTO = "isDecomposedInto";
    public static final String PROP_HAS_COMPENSATION = "hasCompensation";
    public static final String PROP_TARGETS_ENTITY = "targetsEntity";
    public static final String PROP_HAS_EXECUTION_PROTOCOL = "hasExecutionProtocol";
    public static final String PROP_HAS_EXECUTION_INSTRUCTION = "hasExecutionInstruction";
    public static final String PROP_HAS_HTTP_METHOD = "hasHttpMethod";
    public static final String PROP_HAS_EXECUTION_PAYLOAD = "hasExecutionPayload";
    public static final String PROP_POLICY_QUERY_STRING = "policyQueryString";
}
