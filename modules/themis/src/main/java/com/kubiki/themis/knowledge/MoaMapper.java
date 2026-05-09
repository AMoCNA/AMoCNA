package com.kubiki.themis.knowledge;

import com.kubiki.themis.exception.MoaMappingException;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.query.BindingSet;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MoaMapper {

    public ActionData mapAction(IRI actionId, Map<IRI, List<BindingSet>> allBindings) {
        List<BindingSet> bindings = allBindings.get(actionId);
        if (bindings == null || bindings.isEmpty()) {
            throw new MoaMappingException("No bindings found for action: " + actionId);
        }

        BindingSet first = bindings.get(0);
        if (!first.hasBinding("intent")) {
            throw new MoaMappingException("Missing intent for action: " + actionId);
        }
        IRI intent = (IRI) first.getValue("intent");
        String intentStr = intent.stringValue();

        if (intentStr.endsWith("ComplexWorkflow")) {
            List<ActionData> steps = new ArrayList<>();
            Map<IRI, ActionData> compensations = new HashMap<>();

            for (BindingSet bs : bindings) {
                if (bs.hasBinding("step")) {
                    IRI stepId = (IRI) bs.getValue("step");
                    // Avoid infinite loops
                    if (!stepId.equals(actionId)) {
                        ActionData stepData = mapAction(stepId, allBindings);
                        if (stepData != null && !steps.contains(stepData)) {
                            steps.add(stepData);
                        }

                        if (bs.hasBinding("compensation")) {
                            IRI compId = (IRI) bs.getValue("compensation");
                            ActionData compData = mapAction(compId, allBindings);
                            if (compData != null) {
                                compensations.put(stepId, compData);
                            }
                        }
                    }
                }
            }
            return new ActionData.ComplexWorkflow(actionId, intentStr, steps, compensations);
        } else {
            return mapSimpleActionGroup(bindings);
        }
    }

    public ActionData.SimpleAction mapSimpleAction(BindingSet bindings) {
        return mapSimpleActionGroup(List.of(bindings));
    }

    public ActionData.SimpleAction mapSimpleActionGroup(List<BindingSet> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            throw new MoaMappingException("Cannot map empty binding set to SimpleAction");
        }

        BindingSet first = bindings.get(0);
        if (!first.hasBinding("action")) {
            throw new MoaMappingException("Missing action IRI in bindings");
        }
        IRI actionIri = (IRI) first.getValue("action");

        if (!first.hasBinding("intent")) {
            throw new MoaMappingException("Missing intent in bindings for action: " + actionIri);
        }
        String intent = first.getValue("intent").stringValue();

        Map<IRI, ActionData.ConditionData> preConditions = new HashMap<>();
        Map<IRI, ActionData.ConditionData> postConditions = new HashMap<>();

        for (BindingSet bs : bindings) {
            if (bs.hasBinding("preId")) {
                IRI id = (IRI) bs.getValue("preId");
                IRI type = bs.hasBinding("preType") ? (IRI) bs.getValue("preType") : null;
                String policy = bs.hasBinding("prePolicy") ? bs.getValue("prePolicy").stringValue() : null;
                preConditions.putIfAbsent(id, new ActionData.ConditionData(id, type, policy));
            }
            if (bs.hasBinding("postId")) {
                IRI id = (IRI) bs.getValue("postId");
                IRI type = bs.hasBinding("postType") ? (IRI) bs.getValue("postType") : null;
                String policy = bs.hasBinding("postPolicy") ? bs.getValue("postPolicy").stringValue() : null;
                postConditions.putIfAbsent(id, new ActionData.ConditionData(id, type, policy));
            }
        }

        String protocolStr = first.hasBinding("protocol") ? first.getValue("protocol").stringValue() : "REST";
        Protocol protocol;
        try {
            String pName = protocolStr.contains("#") ? protocolStr.substring(protocolStr.indexOf("#") + 1) : protocolStr;
            protocol = Protocol.valueOf(pName.toUpperCase());
        } catch (IllegalArgumentException e) {
            protocol = Protocol.REST;
        }

        String methodStr = first.hasBinding("method") ? first.getValue("method").stringValue() : null;
        HttpMethod method = null;
        if (methodStr != null) {
            String mName = methodStr.contains("#") ? methodStr.substring(methodStr.indexOf("#") + 1) : methodStr;
            try {
                method = HttpMethod.valueOf(mName.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore invalid method
            }
        }

        if (!first.hasBinding("target")) {
            throw new MoaMappingException("Missing target for action: " + actionIri);
        }
        IRI targetIri = (IRI) first.getValue("target");

        return new ActionData.SimpleAction(
                actionIri,
                intent,
                protocol,
                first.hasBinding("instruction") ? first.getValue("instruction").stringValue() : null,
                targetIri,
                new HashMap<>(),
                method,
                first.hasBinding("payload") ? first.getValue("payload").stringValue() : null,
                List.copyOf(preConditions.values()),
                List.copyOf(postConditions.values())
        );
    }
}
