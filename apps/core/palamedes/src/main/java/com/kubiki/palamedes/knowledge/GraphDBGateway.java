package com.kubiki.palamedes.knowledge;

import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.daedalus.knowledge.SparqlClient;
import com.kubiki.palamedes.analyzer.ImageRemediationPlanner;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.model.*;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.BindingSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphDBGateway implements ActionRepository, ResourceDependencyService, IdempotencyAndCompensationService, WorkloadDiscoveryService, ActionHydrationService, IntentMetadataService, ConditionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(GraphDBGateway.class);
    private static final String RESOURCE_IRI = "resource";
    private static final String ACTION_IRI = "action";
    private static final String INTENT_IRI = "intent";
    private static final String HAS_CURRENT_STATE_IRI = "hasCurrentState";
    private static final String HAS_LAST_TRANSITION_TIMESTAMP_IRI = "hasLastTransitionTimestamp";
    private static final String TARGETS_ENTITY_IRI = "targetsEntity";
    private static final String HAS_ACTION_ID_IRI = "hasActionID";
    private static final String IS_DECOMPOSED_INTO_IRI = "isDecomposedInto";
    private static final String HAS_EXECUTION_PROTOCOL_IRI = "hasExecutionProtocol";
    private static final String HAS_EXECUTION_INSTRUCTION_IRI = "hasExecutionInstruction";
    private static final String COMPLEX_WORKFLOW_IRI = "ComplexWorkflow";
    private static final String SIMPLE_ACTION_IRI = "SimpleAction";
    private static final String HAS_HTTP_METHOD_IRI = "hasHttpMethod";
    private static final String HAS_EXPECTED_STATUS_CODE_IRI = "hasExpectedStatusCode";
    private static final String DEPENDS_ON_IRI = "dependsOn";
    private static final String RESOURCE_NAME_VAR = "resourceName";
    private static final String STATE_IRI = "state";
    private static final String WINDOW_IRI = "window";
    private static final String LAST_TRANSITION_IRI = "lastTransition";
    private static final String COMPENSATION_INTENT_IRI = "compensationIntent";
    private static final String ROOT_RESOURCE_IRI = "rootResource";
    private static final String ROOT_RESOURCE_NAME_IRI = "rootResourceName";
    private static final String DEPENDENT_IRI = "dependent";

    private static final String HAS_EXECUTION_STATUS_IRI = "hasExecutionStatus";
    private static final String AUTONOMIC_ACTION_IRI = "AutonomicAction";
    private static final String HYDRATION_PAYLOAD_IRI = "hydrationPayload";

    private static final String STATE_KEY_INITIAL = "initial";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_COMPENSATING = "COMPENSATING";

    private static final String HAS_PRIORITY = "hasPriority";
    private static final String HAS_EXECUTION_DELAY = "hasExecutionDelay";
    private static final String HAS_IDEMPOTENCY_KEY = "hasIdempotencyKey";
    private static final String HAS_EXECUTION_PAYLOAD = "hasExecutionPayload";
    private static final String HAS_AUTH_MECHANISM = "hasAuthMechanism";
    private static final String TIMEOUT_SECONDS = "timeoutSeconds";
    private static final String IS_IDEMPOTENT = "isIdempotent";
    private static final String MAX_RETRIES = "maxRetries";

    private static final String BINDING_CONTAINER_NAME = "containerName";
    private static final String BINDING_IMAGE_REPOSITORY = "imageRepository";
    private static final String BINDING_CURRENT_VERSION = "currentVersion";
    private static final String BINDING_DEPLOYMENT = "deployment";
    private static final String BINDING_DEPLOYMENT_NAME = "deploymentName";
    private static final String BINDING_SERVICE = "service";
    private static final String BINDING_SERVICE_NAME = "serviceName";
    private static final String BINDING_NAMESPACE = "namespace";
    private static final String BINDING_PULL_SECRET_NAME = "pullSecretName";
    private static final String BINDING_INTENT_VAR = "intent";
    private static final String BINDING_RISK_MULTIPLIER = "riskMultiplier";
    private static final String BINDING_CARDINALITY_CAP = "cardinalityCap";
    private static final String BINDING_IS_HEALING = "isHealing";
    private static final String BINDING_PAYLOAD_VAR = "payload";

    private static final float DEFAULT_RISK_MULTIPLIER = 1.0f;
    private static final int MIN_PRIORITY = 0;
    private static final int MIN_DELAY = 0;
    private static final int MIN_RETRIES = 0;

    private final SparqlClient sparqlClient;
    private final SparqlRepository sparqlRepository;
    private final ModelMapper modelMapper;
    private final WorkflowStateMapper workflowStateMapper;
    private final OntologyRegistry ontologyRegistry;
    private final PalamedesProperties properties;

    private String mapStateToStatus(String stateFragment) {
        if (stateFragment == null) return STATUS_PENDING;
        return switch (stateFragment) {
            case "State_Initial", "State_Planned", "State_Validated" -> STATUS_PENDING;
            case "State_InProgress" -> STATUS_IN_PROGRESS;
            case "State_Succeeded" -> STATUS_COMPLETED;
            case "State_Failed" -> STATUS_FAILED;
            case "State_Compensating" -> STATUS_COMPENSATING;
            default -> STATUS_PENDING;
        };
    }

    @Timed(value = "palamedes.graphdb.transition", description = "Time taken to transition action state")
    public void transitionState(IRI actionId, String stateFragment) {
        IRI hasCurrentState = ontologyRegistry.actionsOntology(HAS_CURRENT_STATE_IRI);
        IRI newState = ontologyRegistry.actionsOntology(stateFragment);
        IRI hasLastTransitionTimestamp = ontologyRegistry.actionsOntology(HAS_LAST_TRANSITION_TIMESTAMP_IRI);
        IRI hasExecutionStatus = ontologyRegistry.actionsOntology(HAS_EXECUTION_STATUS_IRI);

        sparqlClient.executeWithConnection(conn -> {
            ValueFactory vf = conn.getValueFactory();
            conn.begin();
            conn.remove(actionId, hasCurrentState, null);
            conn.remove(actionId, hasLastTransitionTimestamp, null);
            conn.remove(actionId, hasExecutionStatus, null);
            conn.add(actionId, hasCurrentState, newState);
            conn.add(actionId, hasLastTransitionTimestamp, vf.createLiteral(OffsetDateTime.now().toString(), XSD.DATETIME));
            conn.add(actionId, hasExecutionStatus, vf.createLiteral(mapStateToStatus(stateFragment)));
            conn.commit();
            log.info("Transitioned action {} to {}", actionId, stateFragment);
        });
    }

    public void createActionWorkflow(IRI resourceIri, IRI intentIri, String actionId) {
        IRI actionIri = ontologyRegistry.actionsOntology(actionId);
        IRI hasCurrentState = ontologyRegistry.actionsOntology(HAS_CURRENT_STATE_IRI);
        IRI stateInitial = ontologyRegistry.actionsOntology(properties.states().actionStates().get(STATE_KEY_INITIAL));
        IRI targetsEntity = ontologyRegistry.actionsOntology(TARGETS_ENTITY_IRI);
        IRI hasActionID = ontologyRegistry.actionsOntology(HAS_ACTION_ID_IRI);
        IRI hasLastTransitionTimestamp = ontologyRegistry.actionsOntology(HAS_LAST_TRANSITION_TIMESTAMP_IRI);
        IRI hasExecutionStatus = ontologyRegistry.actionsOntology(HAS_EXECUTION_STATUS_IRI);

        sparqlClient.executeWithConnection(conn -> {
            ValueFactory vf = conn.getValueFactory();
            conn.begin();
            conn.add(actionIri, RDF.TYPE, intentIri);
            conn.add(actionIri, RDF.TYPE, ontologyRegistry.actionsOntology(AUTONOMIC_ACTION_IRI));
            conn.add(actionIri, hasCurrentState, stateInitial);
            conn.add(actionIri, targetsEntity, resourceIri);
            conn.add(actionIri, hasActionID, vf.createLiteral(actionId));
            conn.add(actionIri, hasLastTransitionTimestamp, vf.createLiteral(OffsetDateTime.now().toString(), XSD.DATETIME));
            conn.add(actionIri, hasExecutionStatus, vf.createLiteral(STATUS_PENDING));
            conn.commit();
            log.info("Created new action workflow {} for resource {} with intent {}", actionIri, resourceIri, intentIri);
        });
    }

    public void materializeActionInstance(IRI actionIri, ActionData template, IRI target, IRI parentIri) {
        IRI targetsEntity = ontologyRegistry.actionsOntology(TARGETS_ENTITY_IRI);
        IRI hasActionID = ontologyRegistry.actionsOntology(HAS_ACTION_ID_IRI);
        IRI isDecomposedInto = ontologyRegistry.actionsOntology(IS_DECOMPOSED_INTO_IRI);
        IRI hasExecutionStatus = ontologyRegistry.actionsOntology(HAS_EXECUTION_STATUS_IRI);

        sparqlClient.executeWithConnection(conn -> {
            ValueFactory vf = conn.getValueFactory();
            conn.begin();
            conn.add(parentIri, isDecomposedInto, actionIri);
            conn.add(actionIri, RDF.TYPE, template.id());
            conn.add(actionIri, RDF.TYPE, ontologyRegistry
                    .actionsOntology(template instanceof ActionData.ComplexWorkflow ? COMPLEX_WORKFLOW_IRI : SIMPLE_ACTION_IRI));
            conn.add(actionIri, targetsEntity, target);
            conn.add(actionIri, hasActionID, vf.createLiteral(actionIri.getLocalName()));
            conn.add(actionIri, hasExecutionStatus, vf.createLiteral(STATUS_PENDING));

            // Add the new optional data properties for any action template
            if (template.priority() > MIN_PRIORITY) {
                conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_PRIORITY),
                        vf.createLiteral(String.valueOf(template.priority()), XSD.INTEGER));
            }
            if (template.executionDelay() > MIN_DELAY) {
                conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_EXECUTION_DELAY),
                        vf.createLiteral(String.valueOf(template.executionDelay()), XSD.INTEGER));
            }
            if (template.idempotencyKey() != null) {
                conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_IDEMPOTENCY_KEY),
                        vf.createLiteral(template.idempotencyKey()));
            }

            if (template instanceof ActionData.SimpleAction sa) {
                conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_EXECUTION_PROTOCOL_IRI),
                        vf.createLiteral(sa.protocol().name()));
                conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_EXECUTION_INSTRUCTION_IRI),
                        vf.createLiteral(sa.instruction()));
                if (sa.method() != null) {
                    conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_HTTP_METHOD_IRI),
                            vf.createLiteral(sa.method().name()));
                }
                conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_EXPECTED_STATUS_CODE_IRI),
                        vf.createLiteral(String.valueOf(sa.expectedStatusCode()), XSD.INTEGER));
                if (sa.timeoutSeconds() > MIN_PRIORITY) {
                    conn.add(actionIri, ontologyRegistry.actionsOntology(TIMEOUT_SECONDS),
                            vf.createLiteral(String.valueOf(sa.timeoutSeconds()), XSD.INTEGER));
                }
                conn.add(actionIri, ontologyRegistry.actionsOntology(IS_IDEMPOTENT),
                        vf.createLiteral(String.valueOf(sa.isIdempotent()), XSD.BOOLEAN));
                if (sa.maxRetries() >= MIN_RETRIES) {
                    conn.add(actionIri, ontologyRegistry.actionsOntology(MAX_RETRIES),
                            vf.createLiteral(String.valueOf(sa.maxRetries()), XSD.INTEGER));
                }
                if (sa.payload() != null) {
                    conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_EXECUTION_PAYLOAD),
                            vf.createLiteral(sa.payload()));
                }
                if (sa.authMechanism() != null) {
                    conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_AUTH_MECHANISM),
                            vf.createLiteral(sa.authMechanism()));
                }
            }

            conn.commit();
            log.info("Materialized action instance {} under parent {}", actionIri, parentIri);
        });
    }

    public WorkflowState getState(IRI actionIri) {
        IRI hasCurrentState = ontologyRegistry.actionsOntology(HAS_CURRENT_STATE_IRI);
        return sparqlClient.executeWithConnection(conn -> {
            var statements = conn.getStatements(actionIri, hasCurrentState, null);
            if (statements.hasNext()) {
                IRI stateIri = (IRI) statements.next().getObject();
                return workflowStateMapper.fromFragment(stateIri.getLocalName());
            }
            return null;
        });
    }

    public Map<IRI, WorkflowState> getStates(Collection<IRI> actionIris) {
        if (actionIris == null || actionIris.isEmpty()) {
            return Map.of();
        }
        String collectIds = actionIris.stream()
                .map(iri -> "<" + iri.stringValue() + ">")
                .collect(Collectors.joining(" "));

        List<BindingSet> results = sparqlRepository.fetchActionStates(collectIds);
        Map<IRI, WorkflowState> statesMap = new HashMap<>();
        for (BindingSet bs : results) {
            IRI action = (IRI) bs.getValue("action");
            String stateStr = bs.getValue("state").stringValue();
            statesMap.put(action, workflowStateMapper.fromFragment(stateStr));
        }
        return statesMap;
    }

    public void linkDependent(IRI dependent, IRI dependency) {
        IRI dependsOn = ontologyRegistry.actionsOntology(DEPENDS_ON_IRI);
        sparqlClient.executeWithConnection(conn -> {
            conn.begin();
            conn.add(dependent, dependsOn, dependency);
            conn.commit();
            log.info("Linked {} to depend on {}", dependent, dependency);
        });
    }

    public ActionData fetchActionStructure(IRI actionId) {
        List<BindingSet> allBindings = sparqlRepository.fetchActionStructure(actionId.stringValue());

        Map<IRI, List<BindingSet>> grouped = allBindings.stream().collect(
                Collectors.groupingBy(bs -> (IRI) bs.getValue(ACTION_IRI), LinkedHashMap::new, Collectors.toList()));
        Result<ActionData> result = modelMapper.mapAction(actionId, grouped);
        if (result.isSuccess()) {
            return result.value();
        } else {
            log.error("Failed to map action structure for {}: {}", actionId, result.error());
            return null;
        }
    }

    public Map<IRI, ActionData> fetchActionStructures(List<IRI> actionIds) {
        String joinedIds = actionIds.stream()
                .map(iri -> "<" + iri.stringValue() + ">")
                .collect(Collectors.joining(" "));

        List<BindingSet> allBindings = sparqlRepository.fetchActionStructures(joinedIds);

        Map<IRI, List<BindingSet>> grouped = allBindings.stream().collect(
                Collectors.groupingBy(bs -> (IRI) bs.getValue(ACTION_IRI), LinkedHashMap::new, Collectors.toList()));
        return modelMapper.mapActions(grouped, actionIds);
    }

    public List<ActiveActionSummary> findActiveActions() {
        return sparqlRepository.findActiveActions().stream().map(bs -> new ActiveActionSummary(
                (IRI) bs.getValue(ACTION_IRI),
                (IRI) bs.getValue(RESOURCE_IRI),
                bs.getValue(RESOURCE_NAME_VAR).stringValue(),
                bs.getValue(STATE_IRI).stringValue())).collect(Collectors.toList());
    }

    public Map<IRI, Boolean> fetchIdempotencyStates(List<IRI> actionIds) {
        if (actionIds.isEmpty()) return Map.of();

        String joinedIds = actionIds.stream()
                .map(iri -> "<" + iri.stringValue() + ">")
                .collect(Collectors.joining(" "));

        List<BindingSet> results = sparqlRepository.checkIdempotencyBatch(joinedIds);
        Map<IRI, Boolean> states = new LinkedHashMap<>();

        OffsetDateTime now = OffsetDateTime.now();

        for (BindingSet bs : results) {
            IRI action = (IRI) bs.getValue(ACTION_IRI);
            int window = ((Literal) bs.getValue(WINDOW_IRI)).intValue();

            if (bs.getValue(LAST_TRANSITION_IRI) != null) {
                OffsetDateTime lastTransition = OffsetDateTime.parse(bs.getValue(LAST_TRANSITION_IRI).stringValue());
                states.put(action, now.isAfter(lastTransition.plusSeconds(window)));
            } else {
                states.put(action, true);
            }
        }

        // Actions not in results are considered to have open window (no previous execution recorded)
        for (IRI id : actionIds) {
            states.putIfAbsent(id, true);
        }

        return states;
    }

    public boolean isIdempotencyWindowOpen(IRI target, IRI intent) {
        List<BindingSet> results = sparqlRepository.findRecentAction(target.stringValue(), intent.stringValue());
        if (results.isEmpty()) return true;

        BindingSet bs = results.getFirst();
        if (bs.getValue(WINDOW_IRI) == null) return true;
        if (bs.getValue(LAST_TRANSITION_IRI) == null) return true;

        int window = ((Literal) bs.getValue(WINDOW_IRI)).intValue();
        OffsetDateTime lastTransition = OffsetDateTime.parse(bs.getValue(LAST_TRANSITION_IRI).stringValue());
        OffsetDateTime now = OffsetDateTime.now();

        return now.isAfter(lastTransition.plusSeconds(window));
    }

    public IRI findCompensation(IRI actionId) {
        List<BindingSet> results = sparqlRepository.findCompensation(actionId.stringValue());
        if (!results.isEmpty()) {
            return (IRI) results.getFirst().getValue(COMPENSATION_INTENT_IRI);
        }
        return null;
    }

    @Timed(value = "palamedes.graphdb.query", extraTags = {"type", "anomalies"}, description = "Time taken to find anomalies")
    public List<AnomalyTarget> findAnomalies() {
        return sparqlRepository.findAnomalies().stream().map(bs -> new AnomalyTarget(
                (IRI) bs.getValue(RESOURCE_IRI),
                bs.getValue(RESOURCE_NAME_VAR).stringValue(),
                (IRI) bs.getValue(INTENT_IRI))).collect(Collectors.toList());
    }

    @Timed(value = "palamedes.graphdb.query", extraTags = {"type", "root-cause"}, description = "Time taken to find root cause")
    public List<AnomalyTarget> findRootCause(IRI startResource) {
        return sparqlRepository.findRootCause(startResource.stringValue()).stream().map(bs -> new AnomalyTarget(
                (IRI) bs.getValue(ROOT_RESOURCE_IRI),
                bs.getValue(ROOT_RESOURCE_NAME_IRI).stringValue(),
                (IRI) bs.getValue(INTENT_IRI))).collect(Collectors.toList());
    }

    public Optional<ImageUpdateTarget> findWorkloadDetails(IRI workloadIri) {
        List<BindingSet> results = sparqlRepository.fetchWorkloadDetails(workloadIri.stringValue());
        if (results.isEmpty()) return Optional.empty();

        BindingSet bs = results.getFirst();
        return Optional.of(new ImageUpdateTarget(
                workloadIri,
                workloadIri.getLocalName(),
                ImageRemediationPlanner.parseNamespaceFromDeploymentIri(workloadIri),
                bs.getValue(BINDING_CONTAINER_NAME).stringValue(),
                bs.getValue(BINDING_IMAGE_REPOSITORY).stringValue(),
                bs.getValue(BINDING_CURRENT_VERSION).stringValue(),
                null,
                null,
                null
        ));
    }

    public List<ImageUpdateTarget> findVulnerableWorkloads(String vulnerablePairs) {
        return sparqlRepository.findVulnerableWorkloads(vulnerablePairs).stream()
                .map(bs -> new ImageUpdateTarget(
                        (IRI) bs.getValue(BINDING_DEPLOYMENT),
                        bs.getValue(BINDING_DEPLOYMENT_NAME).stringValue(),
                        ImageRemediationPlanner.parseNamespaceFromDeploymentIri((IRI) bs.getValue(BINDING_DEPLOYMENT)),
                        bs.getValue(BINDING_CONTAINER_NAME).stringValue(),
                        bs.getValue(BINDING_IMAGE_REPOSITORY).stringValue(),
                        bs.getValue(BINDING_CURRENT_VERSION).stringValue(),
                        null,
                        bs.hasBinding(BINDING_SERVICE) ? (IRI) bs.getValue(BINDING_SERVICE) : null,
                        bs.hasBinding(BINDING_SERVICE_NAME) ? bs.getValue(BINDING_SERVICE_NAME).stringValue() : null
                ))
                .toList();
    }

    @Timed(value = "palamedes.graphdb.query", extraTags = {"type", "registry-auth"}, description = "Time taken to find registry auth failures")
    public List<RegistryAuthTarget> findRegistryAuthFailures() {
        return sparqlRepository.findRegistryAuthFailures().stream()
                .map(bs -> new RegistryAuthTarget(
                        (IRI) bs.getValue(BINDING_DEPLOYMENT),
                        bs.getValue(BINDING_DEPLOYMENT_NAME).stringValue(),
                        bs.getValue(BINDING_NAMESPACE).stringValue(),
                        bs.getValue(BINDING_PULL_SECRET_NAME).stringValue()))
                .toList();
    }

    public void storeActionHydration(String actionId, Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        IRI actionIri = ontologyRegistry.actionsOntology(actionId);
        IRI hydrationKey = ontologyRegistry.actionsOntology(HYDRATION_PAYLOAD_IRI);
        String payload = ActionHydrationPayload.serialize(parameters);
        sparqlClient.executeWithConnection(conn -> {
            var vf = conn.getValueFactory();
            conn.begin();
            conn.remove(actionIri, hydrationKey, null);
            conn.add(actionIri, hydrationKey, vf.createLiteral(payload));
            conn.commit();
            return null;
        });
    }

    public Map<IRI, Map<String, String>> fetchActionHydrations(List<IRI> actionIds) {
        if (actionIds.isEmpty()) return Map.of();

        String joinedIds = actionIds.stream()
                .map(iri -> "<" + iri.stringValue() + ">")
                .collect(Collectors.joining(" "));

        List<BindingSet> results = sparqlRepository.fetchActionHydrations(joinedIds);
        Map<IRI, Map<String, String>> hydrations = new LinkedHashMap<>();
        for (BindingSet bs : results) {
            IRI action = (IRI) bs.getValue(ACTION_IRI);
            String payload = bs.getValue(BINDING_PAYLOAD_VAR).stringValue();
            hydrations.put(action, ActionHydrationPayload.deserialize(payload));
        }
        return hydrations;
    }


    public List<IRI> findDependents(IRI actionId) {
        return sparqlRepository.findDependents(actionId.stringValue()).stream()
                .map(bs -> (IRI) bs.getValue(DEPENDENT_IRI))
                .collect(Collectors.toList());
    }

    public void clearResourceState(IRI resourceIri) {
        try {
            sparqlRepository.clearResourceState(resourceIri.stringValue());
            log.info("Cleared resource state for {}", resourceIri);
        } catch (Exception e) {
            log.error("Failed to clear resource state for {}: {}", resourceIri, e.getMessage());
        }
    }

    public IRI findParent(IRI childIri) {
        IRI isDecomposedInto = ontologyRegistry.actionsOntology(IS_DECOMPOSED_INTO_IRI);
        return sparqlClient.executeWithConnection(conn -> {
            var statements = conn.getStatements(null, isDecomposedInto, childIri);
            if (statements.hasNext()) {
                return (IRI) statements.next().getSubject();
            }
            return null;
        });
    }

    public List<IRI> findChildren(IRI parentIri) {
        IRI isDecomposedInto = ontologyRegistry.actionsOntology(IS_DECOMPOSED_INTO_IRI);
        return sparqlClient.executeWithConnection(conn -> {
            return conn.getStatements(parentIri, isDecomposedInto, null)
                    .stream().map(s -> (IRI) s.getObject()).toList();
        });
    }

    public boolean executeConditionQuery(String query) {
        return sparqlClient.executeBooleanQuery(query);
    }

    public void updateExecutionStatus(IRI actionId, String status) {
        IRI hasExecutionStatus = ontologyRegistry.actionsOntology(HAS_EXECUTION_STATUS_IRI);
        sparqlClient.executeWithConnection(conn -> {
            ValueFactory vf = conn.getValueFactory();
            conn.begin();
            conn.remove(actionId, hasExecutionStatus, null);
            conn.add(actionId, hasExecutionStatus, vf.createLiteral(status));
            conn.commit();
            log.info("Updated action {} execution status to {}", actionId, status);
        });
    }

    public Map<IRI, IntentMetadata> fetchIntentMetadata(List<IRI> intentIds) {
        if (intentIds.isEmpty()) return Map.of();
        String joinedIds = intentIds.stream()
                .map(iri -> "<" + iri.stringValue() + ">")
                .collect(Collectors.joining(" "));
        
        List<BindingSet> results = sparqlRepository.fetchIntentMetadata(joinedIds);
        Map<IRI, IntentMetadata> metadata = new LinkedHashMap<>();
        for (BindingSet bs : results) {
            IRI intent = (IRI) bs.getValue(BINDING_INTENT_VAR);
            float risk = bs.getValue(BINDING_RISK_MULTIPLIER) != null ? ((Literal) bs.getValue(BINDING_RISK_MULTIPLIER)).floatValue() : DEFAULT_RISK_MULTIPLIER;
            int cap = bs.getValue(BINDING_CARDINALITY_CAP) != null ? ((Literal) bs.getValue(BINDING_CARDINALITY_CAP)).intValue() : Integer.MAX_VALUE;
            boolean healing = bs.getValue(BINDING_IS_HEALING) != null ? ((Literal) bs.getValue(BINDING_IS_HEALING)).booleanValue() : true;
            
            metadata.put(intent, new IntentMetadata(intent, risk, cap, healing));
        }
        for (IRI id : intentIds) {
            metadata.putIfAbsent(id, new IntentMetadata(id, DEFAULT_RISK_MULTIPLIER, Integer.MAX_VALUE, true));
        }
        return metadata;
    }

    public boolean isDependentResource(IRI source, IRI target) {
        if (source == null || target == null) return false;
        return sparqlRepository.isDependentResource(source.stringValue(), target.stringValue());
    }

    public java.time.Instant getLastTransitionTimestamp(IRI actionIri) {
        IRI hasLastTransitionTimestamp = ontologyRegistry.actionsOntology(HAS_LAST_TRANSITION_TIMESTAMP_IRI);
        return sparqlClient.executeWithConnection(conn -> {
            var statements = conn.getStatements(actionIri, hasLastTransitionTimestamp, null);
            if (statements.hasNext()) {
                String val = statements.next().getObject().stringValue();
                try {
                    return java.time.Instant.parse(val);
                } catch (Exception e) {
                    try {
                        return java.time.OffsetDateTime.parse(val).toInstant();
                    } catch (Exception ex) {
                        return null;
                    }
                }
            }
            return null;
        });
    }
}

