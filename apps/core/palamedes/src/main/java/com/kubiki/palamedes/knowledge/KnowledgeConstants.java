package com.kubiki.palamedes.knowledge;

import lombok.experimental.UtilityClass;

@UtilityClass
public class KnowledgeConstants {
    public static final String ACTIONS_PREFIX_VARIABLE = "ACTIONS_PREFIX";
    public static final String RESOURCES_PREFIX_VARIABLE = "RESOURCES_PREFIX";
    public static final String BRIDGE_PREFIX_VARIABLE = "BRIDGE_PREFIX";

    public static final String STATE_INITIAL = "STATE_INITIAL";
    public static final String STATE_PLANNED = "STATE_PLANNED";
    public static final String STATE_VALIDATED = "STATE_VALIDATED";
    public static final String STATE_IN_PROGRESS = "STATE_INPROGRESS";
    public static final String STATE_SUCCEEDED = "STATE_SUCCEEDED";
    public static final String STATE_FAILED = "STATE_FAILED";
    public static final String STATE_COMPENSATING = "STATE_COMPENSATING";

    public static final String DEFAULT_STATE_INITIAL = "State_Initial";
    public static final String DEFAULT_STATE_PLANNED = "State_Planned";
    public static final String DEFAULT_STATE_VALIDATED = "State_Validated";
    public static final String DEFAULT_STATE_IN_PROGRESS = "State_InProgress";
    public static final String DEFAULT_STATE_SUCCEEDED = "State_Succeeded";
    public static final String DEFAULT_STATE_FAILED = "State_Failed";
    public static final String DEFAULT_STATE_COMPENSATING = "State_Compensating";

    public static final String PROPERTIES_INITIAL_STATE_NAME = "initial";
    public static final String PROPERTIES_PLANNED_STATE_NAME = "planned";
    public static final String PROPERTIES_VALIDATED_STATE_NAME = "validated";
    public static final String PROPERTIES_IN_PROGRESS_STATE_NAME = "in-progress";
    public static final String PROPERTIES_SUCCEEDED_STATE_NAME = "succeeded";
    public static final String PROPERTIES_FAILED_STATE_NAME = "failed";
    public static final String PROPERTIES_COMPENSATING_STATE_NAME = "compensating";
}
