package com.kubiki.themis.model;

import java.util.List;
import java.util.Map;

public sealed interface ActionData 
    permits ActionData.SimpleAction, ActionData.ComplexWorkflow {
    
    String id();
    String functionalIntent();

    record SimpleAction(
        String id,
        String functionalIntent,
        String targetIri,
        Map<String, String> parameters
    ) implements ActionData {}

    record ComplexWorkflow(
        String id,
        String functionalIntent,
        List<ActionData> steps,
        Map<String, ActionData> compensations
    ) implements ActionData {}
}
