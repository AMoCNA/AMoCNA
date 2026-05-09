package com.kubiki.themis.knowledge;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ModelMapper {

    public Result<ActionData> mapAction(IRI actionId, Map<IRI, List<BindingSet>> allBindings) {
        List<BindingSet> bindings = allBindings.get(actionId);
        if (bindings == null || bindings.isEmpty()) {
            return Result.failure("No bindings found for action: " + actionId);
        }

        return getIRI(bindings.get(0), "intent").flatMap(intent -> {
            String intentStr = intent.stringValue();
            if (intentStr.endsWith("ComplexWorkflow")) {
                return mapComplexWorkflow(actionId, intentStr, bindings, allBindings);
            } else {
                return mapSimpleAction(actionId, intentStr, bindings);
            }
        });
    }

    private Result<ActionData> mapSimpleAction(IRI actionId, String intent, List<BindingSet> bindings) {
        BindingSet first = bindings.get(0);
        
        Result<Protocol> protocolResult = getProtocol(first);
        Result<IRI> targetResult = getIRI(first, "target");
        
        return Result.combine(protocolResult, targetResult, (protocol, target) -> {
            List<ActionData.ConditionData> pre = extractConditions(bindings, "preId", "preType", "prePolicy");
            List<ActionData.ConditionData> post = extractConditions(bindings, "postId", "postType", "postPolicy");
            
            return ActionData.SimpleAction.builder()
                .id(actionId)
                .functionalIntent(intent)
                .protocol(protocol)
                .targetIri(target)
                .instruction(getString(first, "instruction").value())
                .method(getHttpMethod(first).value())
                .payload(getString(first, "payload").value())
                .data(new HashMap<>())
                .preConditions(pre)
                .postConditions(post)
                .build();
        });
    }

    private Result<ActionData> mapComplexWorkflow(IRI actionId, String intent, List<BindingSet> bindings, Map<IRI, List<BindingSet>> allBindings) {
        List<ActionData> steps = new ArrayList<>();
        Map<IRI, ActionData> compensations = new HashMap<>();

        for (BindingSet bs : bindings) {
            Result<IRI> stepIdResult = getIRI(bs, "step");
            if (stepIdResult.isSuccess()) {
                IRI stepId = stepIdResult.value();
                if (!stepId.equals(actionId)) {
                    Result<ActionData> stepResult = mapAction(stepId, allBindings);
                    if (stepResult.isSuccess()) {
                        steps.add(stepResult.value());
                        
                        Result<IRI> compIdResult = getIRI(bs, "compensation");
                        if (compIdResult.isSuccess()) {
                            Result<ActionData> compResult = mapAction(compIdResult.value(), allBindings);
                            if (compResult.isSuccess()) {
                                compensations.put(stepId, compResult.value());
                            }
                        }
                    } else {
                        return Result.failure("Failed to map step " + stepId + ": " + stepResult.error());
                    }
                }
            }
        }
        return Result.success(new ActionData.ComplexWorkflow(actionId, intent, steps, compensations));
    }

    private List<ActionData.ConditionData> extractConditions(List<BindingSet> bindings, String idVar, String typeVar, String policyVar) {
        Map<IRI, ActionData.ConditionData> conditions = new LinkedHashMap<>();
        for (BindingSet bs : bindings) {
            getIRI(bs, idVar).map(id -> {
                IRI type = getIRI(bs, typeVar).value();
                String policy = getString(bs, policyVar).value();
                return conditions.putIfAbsent(id, new ActionData.ConditionData(id, type, policy));
            });
        }
        return List.copyOf(conditions.values());
    }

    private Result<IRI> getIRI(BindingSet bs, String name) {
        Value val = bs.getValue(name);
        if (val instanceof IRI iri) {
            return Result.success(iri);
        }
        return Result.failure("Missing or invalid IRI binding: " + name);
    }

    private Result<String> getString(BindingSet bs, String name) {
        Value val = bs.getValue(name);
        if (val != null) {
            return Result.success(val.stringValue());
        }
        return Result.failure("Missing binding: " + name);
    }

    private Result<Protocol> getProtocol(BindingSet bs) {
        Result<String> protocolStrResult = getString(bs, "protocol");
        if (!protocolStrResult.isSuccess()) {
            return Result.success(Protocol.REST); // Default
        }
        String s = protocolStrResult.value();
        try {
            String name = s.contains("#") ? s.substring(s.indexOf("#") + 1) : s;
            return Result.success(Protocol.valueOf(name.toUpperCase()));
        } catch (Exception e) {
            return Result.success(Protocol.REST);
        }
    }

    private Result<HttpMethod> getHttpMethod(BindingSet bs) {
        Result<String> methodStrResult = getString(bs, "method");
        if (!methodStrResult.isSuccess()) {
            return Result.success(null);
        }
        String s = methodStrResult.value();
        try {
            String name = s.contains("#") ? s.substring(s.indexOf("#") + 1) : s;
            return Result.success(HttpMethod.valueOf(name.toUpperCase()));
        } catch (Exception e) {
            return Result.success(null);
        }
    }
}
