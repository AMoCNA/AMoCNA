package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.Protocol;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
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
    private static final String BINDING_EXPECTED_STATUS = "expectedStatusCode";
    private static final String BINDING_AUTH_MECHANISM = "authMechanism";
    private static final String BINDING_TIMEOUT = "timeoutSeconds";
    private static final String BINDING_IS_IDEMPOTENT = "isIdempotent";
    private static final String BINDING_MAX_RETRIES = "maxRetries";

    private static final String BINDING_FUNCTIONAL_INTENT = "functionalIntent";
    private static final String BINDING_LAYER_BOUNDARY = "layerBoundary";
    private static final String BINDING_COST_VALUE = "costValue";

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

        // Industrial Rule: Select the most specific Intent
        IRI intent = selectBestIntent(bindings);
        String intentStr = intent.stringValue();

        if (isComplexWorkflow(intentStr)) {
            return mapComplexWorkflow(actionId, bindings, allBindings);
        }
        return mapSimpleAction(actionId, bindings);
    }

    private IRI selectBestIntent(List<BindingSet> bindings) {
        List<IRI> intents = bindings.stream()
                .map(bs -> bs.getValue(BINDING_INTENT))
                .filter(v -> v instanceof IRI)
                .map(v -> (IRI) v)
                .toList();

        return intents.stream()
                .filter(i -> !i.getLocalName().equals("AutonomicAction")
                        && !i.getLocalName().equals("SimpleAction")
                        && !i.getLocalName().equals("ComplexWorkflow"))
                .findFirst()
                .orElse(intents.isEmpty() ? null : intents.getFirst());
    }

    private boolean isComplexWorkflow(String intent) {
        return intent != null && intent.endsWith(INTENT_SUFFIX_COMPLEX);
    }

    private Result<ActionData> mapSimpleAction(IRI actionId, List<BindingSet> bindings) {
        Result<Protocol> protocolResult = getProtocol(bindings);
        Result<String> instructionResult = getString(bindings, BINDING_INSTRUCTION);

        IRI target = getIRI(bindings, BINDING_TARGET).value();
        IRI functionalIntent = getIRI(bindings, BINDING_FUNCTIONAL_INTENT).value();
        IRI layerBoundary = getIRI(bindings, BINDING_LAYER_BOUNDARY).value();
        float cost = getOptionalFloat(bindings, BINDING_COST_VALUE, 1.0f);

        return Result.combine(protocolResult, instructionResult, (protocol, instruction) -> {
            List<ActionData.Condition> pre = extractConditions(bindings, BINDING_PRE_ID, BINDING_PRE_TYPE, BINDING_PRE_POLICY);
            List<ActionData.Condition> post = extractConditions(bindings, BINDING_POST_ID, BINDING_POST_TYPE, BINDING_POST_POLICY);

            return ActionData.SimpleAction.builder()
                    .id(actionId)
                    .functionalIntent(functionalIntent)
                    .layerBoundary(layerBoundary)
                    .executionCost(cost)
                    .protocol(protocol)
                    .target(target)
                    .instruction(instruction)
                    .method(getHttpMethod(bindings).value())
                    .payload(getOptionalString(bindings, BINDING_PAYLOAD))
                    .data(new HashMap<>())
                    .preConditions(pre)
                    .postConditions(post)
                    .expectedStatusCode(getExpectedStatusCode(bindings, protocol))
                    .authMechanism(getOptionalString(bindings, BINDING_AUTH_MECHANISM))
                    .timeoutSeconds(getOptionalInt(bindings, BINDING_TIMEOUT, 30))
                    .isIdempotent(getOptionalBoolean(bindings, BINDING_IS_IDEMPOTENT, true))
                    .maxRetries(getOptionalInt(bindings, BINDING_MAX_RETRIES, 3))
                    .build();
        });
    }

    private int getExpectedStatusCode(List<BindingSet> bindings, Protocol protocol) {
        String val = getOptionalString(bindings, BINDING_EXPECTED_STATUS);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException _) {
            }
        }
        return protocol == Protocol.SHELL ? 0 : 200;
    }

    private String getOptionalString(List<BindingSet> bindings, String name) {
        for (BindingSet bs : bindings) {
            Value val = bs.getValue(name);
            if (val != null) {
                return val.stringValue();
            }
        }
        return null;
    }

    private int getOptionalInt(List<BindingSet> bindings, String name, int defaultValue) {
        String val = getOptionalString(bindings, name);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException _) {
            }
        }
        return defaultValue;
    }

    private float getOptionalFloat(List<BindingSet> bindings, String name, float defaultValue) {
        for (BindingSet bs : bindings) {
            Value val = bs.getValue(name);
            if (val instanceof Literal l) {
                return l.floatValue();
            }
        }
        return defaultValue;
    }

    private boolean getOptionalBoolean(List<BindingSet> bindings, String name, boolean defaultValue) {
        String val = getOptionalString(bindings, name);
        if (val != null) {
            return Boolean.parseBoolean(val);
        }
        return defaultValue;
    }

    private Result<ActionData> mapComplexWorkflow(
            IRI actionId,
            List<BindingSet> bindings,
            Map<IRI, List<BindingSet>> allBindings
    ) {
        List<ActionData> steps = new ArrayList<>();
        Map<IRI, ActionData> compensations = new HashMap<>();

        IRI target = getIRI(bindings, BINDING_TARGET).value();
        IRI functionalIntent = getIRI(bindings, BINDING_FUNCTIONAL_INTENT).value();
        IRI layerBoundary = getIRI(bindings, BINDING_LAYER_BOUNDARY).value();
        float cost = getOptionalFloat(bindings, BINDING_COST_VALUE, 1.0f);

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
                continue;
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

        // De-duplicate steps
        List<ActionData> distinctSteps = steps.stream().distinct().toList();

        return Result.success(new ActionData.ComplexWorkflow(actionId, functionalIntent, layerBoundary, cost, target, distinctSteps, compensations));
    }

    private List<ActionData.Condition> extractConditions(
            List<BindingSet> bindings,
            String idVar,
            String typeVar,
            String policyVar
    ) {
        Map<IRI, ActionData.Condition> conditions = new LinkedHashMap<>();
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
            conditions.putIfAbsent(id, new ActionData.Condition(id, typeResult.value(), policyResult.value()));
        }
        return List.copyOf(conditions.values());
    }

    private Result<IRI> getIRI(List<BindingSet> bindings, String name) {
        for (BindingSet bs : bindings) {
            Value val = bs.getValue(name);
            if (val instanceof IRI iri) {
                return Result.success(iri);
            }
        }
        return Result.failure("Missing or invalid IRI binding: " + name);
    }

    private Result<IRI> getIRI(BindingSet bs, String name) {
        Value val = bs.getValue(name);
        if (val instanceof IRI iri) {
            return Result.success(iri);
        }
        return Result.failure("Missing or invalid IRI binding: " + name);
    }

    private Result<String> getString(List<BindingSet> bindings, String name) {
        for (BindingSet bs : bindings) {
            Value val = bs.getValue(name);
            if (val != null) {
                return Result.success(val.stringValue());
            }
        }
        return Result.failure("Missing binding: " + name);
    }

    private Result<String> getString(BindingSet bs, String name) {
        Value val = bs.getValue(name);
        if (val != null) {
            return Result.success(val.stringValue());
        }
        return Result.failure("Missing binding: " + name);
    }

    private Result<Protocol> getProtocol(List<BindingSet> bindings) {
        Result<String> protocolStrResult = getString(bindings, BINDING_PROTOCOL);
        if (!protocolStrResult.isSuccess()) {
            return Result.success(Protocol.REST); // Default
        }
        return parseProtocol(protocolStrResult.value());
    }

    private Result<Protocol> parseProtocol(String raw) {
        String name = raw;
        if (raw.contains("#")) {
            name = raw.substring(raw.indexOf("#") + 1);
        } else if (raw.contains("/")) {
            name = raw.substring(raw.lastIndexOf("/") + 1);
        }
        try {
            return Result.success(Protocol.valueOf(name.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Result.failure("Unknown protocol: " + name);
        }
    }

    private Result<HttpMethod> getHttpMethod(List<BindingSet> bindings) {
        Result<String> methodStrResult = getString(bindings, BINDING_METHOD);
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
}
