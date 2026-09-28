package com.kubiki.palamedes.knowledge;

import com.kubiki.common.model.Protocol;
import com.kubiki.palamedes.model.ActionData;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ModelMapper {

    private static final Logger log = LoggerFactory.getLogger(ModelMapper.class);

    private static final String INTENT_SUFFIX_COMPLEX = "Workflow";

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
    private static final String BINDING_IDEMPOTENCY_WINDOW = "idempotencyWindowSeconds";
    private static final String BINDING_PRIORITY = "priority";
    private static final String BINDING_EXECUTION_DELAY = "executionDelay";
    private static final String BINDING_IDEMPOTENCY_KEY = "idempotencyKey";

    private static final String BINDING_FUNCTIONAL_INTENT = "functionalIntent";
    private static final String BINDING_LAYER_BOUNDARY = "layerBoundary";
    private static final String BINDING_COST_VALUE = "costValue";

    private static final String BINDING_PRE_ID = "preId";
    private static final String BINDING_PRE_TYPE = "preType";
    private static final String BINDING_PRE_POLICY = "prePolicy";
    private static final String BINDING_POST_ID = "postId";
    private static final String BINDING_POST_TYPE = "postType";
    private static final String BINDING_POST_POLICY = "postPolicy";

    private static final String ONT_AUTONOMIC_ACTION = "AutonomicAction";
    private static final String ONT_SIMPLE_ACTION = "SimpleAction";
    private static final String ONT_COMPLEX_WORKFLOW = "ComplexWorkflow";

    private static final float DEFAULT_COST = 1.0f;
    private static final int DEFAULT_IDEMPOTENCY_WINDOW = 60;
    private static final int DEFAULT_TIMEOUT = 30;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_PRIORITY_OR_DELAY = 0;

    private static final int EXPECTED_STATUS_CODE_SUCCESS = 200;
    private static final int EXPECTED_STATUS_CODE_SHELL = 0;

    private static final int INDEX_OFFSET = 1;

    public Map<IRI, ActionData> mapActions(Map<IRI, List<BindingSet>> allBindings, List<IRI> rootActionIds) {
        Map<IRI, ActionData> results = new HashMap<>();
        for (IRI rootId : rootActionIds) {
            Result<ActionData> result = mapAction(rootId, allBindings);
            if (result.isSuccess()) {
                results.put(rootId, result.value());
            } else {
                log.warn("Failed to map action {}: {}", rootId, result.error());
            }
        }
        return results;
    }

    public Result<ActionData> mapAction(IRI actionId, Map<IRI, List<BindingSet>> allBindings) {
        List<BindingSet> bindings = allBindings.get(actionId);
        if (bindings == null || bindings.isEmpty()) {
            return Result.failure("No bindings found for action: " + actionId);
        }

        if (isComplexWorkflow(bindings)) {
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
                .filter(i -> !i.getLocalName().equals(ONT_AUTONOMIC_ACTION)
                        && !i.getLocalName().equals(ONT_SIMPLE_ACTION)
                        && !i.getLocalName().equals(ONT_COMPLEX_WORKFLOW))
                .findFirst()
                .orElse(intents.isEmpty() ? null : intents.get(0));
    }

    private boolean isComplexWorkflow(List<BindingSet> bindings) {
        return bindings.stream()
                .map(bs -> bs.getValue(BINDING_INTENT))
                .filter(v -> v instanceof IRI)
                .map(v -> (IRI) v)
                .anyMatch(i -> i.getLocalName().equals(ONT_COMPLEX_WORKFLOW) || i.getLocalName().endsWith(INTENT_SUFFIX_COMPLEX));
    }

    private Result<ActionData> mapSimpleAction(IRI actionId, List<BindingSet> bindings) {
        Result<Protocol> protocolResult = getProtocol(bindings);
        Result<String> instructionResult = getString(bindings, BINDING_INSTRUCTION);

        return Result.combine(protocolResult, instructionResult, (protocol, instruction) -> {
            List<ActionData.Condition> pre = extractConditions(bindings, BINDING_PRE_ID, BINDING_PRE_TYPE, BINDING_PRE_POLICY);
            List<ActionData.Condition> post = extractConditions(bindings, BINDING_POST_ID, BINDING_POST_TYPE, BINDING_POST_POLICY);

            ActionData.SimpleAction action = OtmMapper.map(bindings, ActionData.SimpleAction.class, actionId, pre, post);

            int expected = getExpectedStatusCode(bindings, protocol);

            return ActionData.SimpleAction.builder()
                    .id(action.id())
                    .functionalIntent(action.functionalIntent())
                    .layerBoundary(action.layerBoundary())
                    .executionCost(action.executionCost())
                    .protocol(action.protocol())
                    .instruction(action.instruction())
                    .target(action.target())
                    .data(action.data())
                    .method(action.method())
                    .payload(action.payload())
                    .preConditions(action.preConditions())
                    .postConditions(action.postConditions())
                    .expectedStatusCode(expected)
                    .authMechanism(action.authMechanism())
                    .timeoutSeconds(action.timeoutSeconds())
                    .isIdempotent(action.isIdempotent())
                    .maxRetries(action.maxRetries())
                    .idempotencyWindowSeconds(action.idempotencyWindowSeconds())
                    .priority(action.priority())
                    .executionDelay(action.executionDelay())
                    .idempotencyKey(action.idempotencyKey())
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
        return protocol == Protocol.SHELL ? EXPECTED_STATUS_CODE_SHELL : EXPECTED_STATUS_CODE_SUCCESS;
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

        ActionData.ComplexWorkflow mapped = OtmMapper.map(bindings, ActionData.ComplexWorkflow.class, actionId, distinctSteps, compensations);

        return Result.success(new ActionData.ComplexWorkflow(
                mapped.id(),
                mapped.functionalIntent(),
                mapped.layerBoundary(),
                mapped.executionCost(),
                mapped.target(),
                mapped.idempotencyWindowSeconds(),
                mapped.priority(),
                mapped.executionDelay(),
                mapped.idempotencyKey(),
                distinctSteps,
                compensations
        ));
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
            name = raw.substring(raw.indexOf("#") + INDEX_OFFSET);
        } else if (raw.contains("/")) {
            name = raw.substring(raw.lastIndexOf("/") + INDEX_OFFSET);
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
        String name = raw.contains("#") ? raw.substring(raw.indexOf("#") + INDEX_OFFSET) : raw;
        try {
            return HttpMethod.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
