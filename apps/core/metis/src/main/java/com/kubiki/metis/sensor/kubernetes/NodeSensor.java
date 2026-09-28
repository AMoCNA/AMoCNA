package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.grpc.EntityDeletedEvent;
import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.KubernetesSensor;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Watches Kubernetes Nodes (cluster-scoped) and emits:
 * <ul>
 *   <li>{@link EntityDiscoveredEvent} — on node add</li>
 *   <li>{@link EntityDeletedEvent} — on node delete</li>
 * </ul>
 *
 * <p>CNEEOnt type: {@code cnee:Node}.
 *
 * <p>Nodes are cluster-scoped so this sensor does not extend
 * {@link AbstractNamespacedSensor} — it manages a single informer directly.
 */
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class NodeSensor implements KubernetesSensor {

    private static final Logger log = LoggerFactory.getLogger(NodeSensor.class);
    private static final String ONTOLOGY_TYPE_LOCAL = CneeOntology.CLASS_NODE;

    private final KubernetesClient client;
    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;

    private SharedIndexInformer<Node> informer;

    public NodeSensor(KubernetesClient client,
                      SensorEventPublisher publisher,
                      IriFactory iriFactory) {
        this.client = client;
        this.publisher = publisher;
        this.iriFactory = iriFactory;
    }

    @Override
    public String name() {
        return "NodeSensor";
    }

    @Override
    public void start() {
        informer = client.nodes().inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Node node) {
                onNodeAdded(node);
            }

            @Override
            public void onUpdate(Node oldNode, Node newNode) {
                // No meaningful state transitions for nodes in the current ontology
            }

            @Override
            public void onDelete(Node node, boolean deletedFinalStateUnknown) {
                onNodeDeleted(node);
            }
        });
        informer.start();
        log.info("NodeSensor watching all nodes (cluster-scoped)");
    }

    @Override
    public void stop() {
        if (informer != null) {
            try {
                informer.stop();
            } catch (Exception e) {
                log.warn("NodeSensor error stopping informer: {}", e.getMessage());
            }
        }
        log.info("NodeSensor stopped");
    }

    // -------------------------------------------------------------------------

    private void onNodeAdded(Node node) {
        String name = node.getMetadata().getName();
        String iri = iriFactory.clusterScopedIri(CneeOntology.KIND_NODE, name);
        String type = iriFactory.typeIri(ONTOLOGY_TYPE_LOCAL);

        EntityDiscoveredEvent discovered = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(type)
                .setResourceId(name)
                .setResourceName(name)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDiscovered(discovered)));

        log.debug("NodeSensor: added node {}", name);
    }

    private void onNodeDeleted(Node node) {
        String name = node.getMetadata().getName();
        String iri = iriFactory.clusterScopedIri(CneeOntology.KIND_NODE, name);
        String type = iriFactory.typeIri(ONTOLOGY_TYPE_LOCAL);

        EntityDeletedEvent deleted = EntityDeletedEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(type)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDeleted(deleted)));

        log.debug("NodeSensor: deleted node {}", name);
    }
}
