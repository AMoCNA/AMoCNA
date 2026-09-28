package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.grpc.StateChangedEvent;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.KubernetesSensor;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Watches Kubernetes Node conditions and detects node-level anomaly states:
 * <ul>
 *   <li>Ready=False → {@code cnee:NodeNotReadyState}</li>
 *   <li>MemoryPressure=True → {@code cnee:NodeMemoryStarvedState}</li>
 *   <li>DiskPressure=True → (mapped to storage anomaly if needed)</li>
 * </ul>
 *
 * <p>Nodes are cluster-scoped so this sensor manages a single informer directly.
 */
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class NodeConditionSensor implements KubernetesSensor {

    private static final Logger log = LoggerFactory.getLogger(NodeConditionSensor.class);

    private final KubernetesClient client;
    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;

    private SharedIndexInformer<Node> informer;

    public NodeConditionSensor(KubernetesClient client,
                               SensorEventPublisher publisher,
                               IriFactory iriFactory) {
        this.client = client;
        this.publisher = publisher;
        this.iriFactory = iriFactory;
    }

    @Override
    public String name() {
        return "NodeConditionSensor";
    }

    @Override
    public void start() {
        informer = client.nodes().inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Node node) {
                detectNodeAnomalies(node);
            }

            @Override
            public void onUpdate(Node oldNode, Node newNode) {
                detectNodeAnomalies(newNode);
            }

            @Override
            public void onDelete(Node node, boolean deletedFinalStateUnknown) {
                // Deletion handled by NodeSensor
            }
        });
        informer.start();
        log.info("NodeConditionSensor watching all nodes (cluster-scoped)");
    }

    @Override
    public void stop() {
        if (informer != null) {
            try {
                informer.stop();
            } catch (Exception e) {
                log.warn("NodeConditionSensor error stopping informer: {}", e.getMessage());
            }
        }
        log.info("NodeConditionSensor stopped");
    }

    // -------------------------------------------------------------------------

    void detectNodeAnomalies(Node node) {
        if (node.getStatus() == null) return;

        String name = node.getMetadata().getName();
        String nodeIri = iriFactory.clusterScopedIri(CneeOntology.KIND_NODE, name);

        List<NodeCondition> conditions = node.getStatus().getConditions();
        if (conditions == null) return;

        for (NodeCondition condition : conditions) {
            String anomalyState = mapConditionToAnomaly(condition);
            if (anomalyState != null) {
                emitStateChange(nodeIri, anomalyState);
                log.info("NodeConditionSensor: anomaly {} on node {}", anomalyState, name);
            }
        }
    }

    /**
     * Maps a Kubernetes node condition to a CNEEOnt anomaly state.
     * Returns {@code null} if the condition is healthy.
     */
    private String mapConditionToAnomaly(NodeCondition condition) {
        String type = condition.getType();
        String status = condition.getStatus();

        // Ready=False means node is not ready
        if ("Ready".equals(type) && "False".equals(status)) {
            return CneeOntology.STATE_NODE_NOT_READY;
        }

        // MemoryPressure=True means node is running out of memory
        if ("MemoryPressure".equals(type) && "True".equals(status)) {
            return CneeOntology.STATE_NODE_MEMORY_STARVED;
        }

        // PIDPressure or DiskPressure could map to other anomalies
        // but we don't have specific CNEEOnt states for them yet

        return null;
    }

    private void emitStateChange(String resourceIri, String stateLocalName) {
        String stateIri = iriFactory.typeIri(stateLocalName);

        StateChangedEvent stateChanged = StateChangedEvent.newBuilder()
                .setResourceIri(resourceIri)
                .setNewStateIri(stateIri)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setStateChanged(stateChanged)));
    }
}
