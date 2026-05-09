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

    private static final String INTENT_SUFFIX_COMPLEX = "ComplexWorkflow";

    private static final String BINDING_INTENT = "intent";
    private static final String BINDING_TARGET = "target";
    private static final String BINDING_INSTRUCTION = "instruction";
    private static final String BINDING_METHOD = "method";
    private static final String BINDING_PAYLOAD = "payload";
    private static final String BINDING_PROTOCOL = "protocol";
    private static final String BINDING_STEP = "step";
    private static final String BINDING_COMPENSATION = "compensation";

    private static final String BINDING_PRE_ID = "preId";
    private static final String BINDING_PRE_TYPE = "preType";
    private static final String BINDING_PRE_POLICY = "prePolicy";
    private static final String BINDING_POST_ID = "postId";
    private static final String BINDING_POST_TYPE = "postType";
    private static final String BINDING_POST_POLICY = "postPolicy";

    public Result<ActionData> mapAction(IRI actionId, Map<IRI, List<BindingSet>> allBindings) {
        List<BindingSet> bindings = allBindings.get(actionId);
        if (bindings == null || bindings.isEmpty()) {
            return Result.failure("No bindings found for action: " + actionId);
        }

        return getIRI(bindings.get(0), BINDING_INTENT).flatMap(intent -> {
            String intentStr = intent.stringValue();
            if (isComplexWorkflow(intentStr)) {
                return mapComplexWorkflow(actionId, intentStr, bindings, allBindings);
            }
            return mapSimpleAction(actionId, intentStr, bindings);
        });
    }

    private boolean isComplexWorkflow(String intent) {
        return intent.endsWith(INTENT_SUFFIX_COMPLEX);
    }

    private Result<ActionData> mapSimpleAction(IRI actionId, String intent, List<BindingSet> bindings) {
        BindingSet first = bindings.get(0);

        Result<Protocol> protocolResult = getProtocol(first);
        Result<IRI> targetResult = getIRI(first, BINDING_TARGET);

        return Result.combine(protocolResult, targetResult, (protocol, target) -> {
            List<ActionData.ConditionData> pre = extractConditions(bindings, BINDING_PRE_ID, BINDING_PRE_TYPE, BINDING_PRE_POLICY);
            List<ActionData.ConditionData> post = extractConditions(bindings, BINDING_POST_ID, BINDING_POST_TYPE, BINDING_POST_POLICY);

            return ActionData.SimpleAction.builder()
                .id(actionId)
                .functionalIntent(intent)
                .protocol(protocol)
                .targetIri(target)
                .instruction(getString(first, BINDING_INSTRUCTION).value())
                .method(getHttpMethod(first).value())
                .payload(getString(first, BINDING_PAYLOAD).value())
                .data(new HashMap<>())
                .preConditions(pre)
                .postConditions(post)
                .build();
        });
    }

    private Result<ActionData> mapComplexWorkflow(
        IRI actionId,
        String intent,
        List<BindingSet> bindings,
        Map<IRI, List<BindingSet>> allBindings
    ) {
        List<ActionData> steps = new ArrayList<>();
        Map<IRI, ActionData> compensations = new HashMap<>();

        for (BindingSet bs : bindings) {
            Result<IRI> stepIdResult = getIRI(bs, BINDING_STEP);
            if (!stepIdResult.isSuccess()) {
                continue;
            }

            IRI stepId = stepIdResult.value();
            if (stepId.equals(actionId)) {
                continue;
            }

            Result<ActionData> stepResult = mapAction(stepId, allBindings);
            if (!stepResult.isSuccess()) {
                return Result.failure("Failed to map step " + stepId + ": " + stepResult.error());
            }

            steps.add(stepResult.value());

            Result<IRI> compIdResult = getIRI(bs, BINDING_COMPENSATION);
            if (!compIdResult.isSuccess()) {
                continue;
            }

            Result<ActionData> compResult = mapAction(compIdResult.value(), allBindings);
            if (compResult.isSuccess()) {
                compensations.put(stepId, compResult.value());
            }
        }

        return Result.success(new ActionData.ComplexWorkflow(actionId, intent, steps, compensations));
    }

    private List<ActionData.ConditionData> extractConditions(
        List<BindingSet> bindings,
        String idVar,
        String typeVar,
        String policyVar
    ) {
        Map<IRI, ActionData.ConditionData> conditions = new LinkedHashMap<>();
        for (BindingSet bs : bindings) {
            Result<IRI> idResult = getIRI(bs, idVar);
            if (!idResult.isSuccess()) {
                continue;
            }
            Result<IRI> typeResult = getIRI(bs, typeVar);
            if (!typeResult.isSuccess()) {
                continue;
            }
            Result<String> policyResult = getString(bs, policyVar);
            if (!policyResult.isSuccess()) {
                continue;
            }
            IRI id = idResult.value();
            conditions.putIfAbsent(id, new ActionData.ConditionData(id, typeResult.value(), policyResult.value()));
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
        Result<String> protocolStrResult = getString(bs, BINDING_PROTOCOL);
        if (!protocolStrResult.isSuccess()) {
            return Result.success(Protocol.REST); // Default
        }
        return parseEnum(protocolStrResult.value());
    }

    private Result<HttpMethod> getHttpMethod(BindingSet bs) {
        Result<String> methodStrResult = getString(bs, BINDING_METHOD);
        if (!methodStrResult.isSuccess()) {
            return Result.success(null);
        }
        return Result.success(parseHttpMethod(methodStrResult.value()));
    }

    private HttpMethod parseHttpMethod(String raw) {
        String name = raw.contains("#") ? raw.substring(raw.indexOf("#") + 1) : raw;
        try {
            return HttpMethod.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    private <T extends Enum<T>> Result<T> parseEnum(String raw) {
        try {
            String name = raw.contains("#") ? raw.substring(raw.indexOf("#") + 1) : raw;
            return Result.success(Enum.valueOf((Class<T>) Protocol.class, name.toUpperCase()));
        } catch (Exception e) {
            return Result.success((T) Protocol.REST);
        }
    }
}