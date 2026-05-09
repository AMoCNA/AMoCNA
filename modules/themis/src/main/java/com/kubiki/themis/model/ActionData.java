package com.kubiki.themis.model;

import org.eclipse.rdf4j.model.IRI;
import org.springframework.http.HttpMethod;
import java.util.List;
import java.util.Map;

public sealed interface ActionData 
    permits ActionData.SimpleAction, ActionData.ComplexWorkflow {
    
    IRI id();
    String functionalIntent();

    record ConditionData(IRI id, IRI type, String policy) {}

    record SimpleAction(
        IRI id,
        String functionalIntent,
        Protocol protocol,
        String instruction,
        IRI targetIri,
        Map<String, String> data,
        HttpMethod method,
        String payload,
        List<ConditionData> preConditions,
        List<ConditionData> postConditions
    ) implements ActionData {}

    record ComplexWorkflow(
        IRI id,
        String functionalIntent,
        List<ActionData> steps,
        Map<IRI, ActionData> compensations
    ) implements ActionData {}
}
