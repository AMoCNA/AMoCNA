package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.KubernetesSensor;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Indexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Watches Pods and Services to detect and emit topology relationships:
 * <ul>
 *   <li>{@code cnee:contains(service, pod)} — when a pod's labels match a service's selector</li>
 *   <li>{@code cnee:isHostedOn(pod, node)} — when a pod is scheduled to a node</li>
 *   <li>{@code cnee:isRealizedBy(service, pod)} — when a pod is an endpoint for a service</li>
 * </ul>
 *
 * <p>Reads existing pods and services from the local informer cache via
 * {@link Indexer#list()} — no API calls per event.
 *
 * <h2>Sync barrier</h2>
 * Both pod and service informers are started together, then the sensor blocks
 * on a worker thread until both caches report {@link SharedIndexInformer#hasSynced()}.
 * Only then are cross-handlers attached and a one-time reconciliation sweep emitted.
 * This avoids races where pod {@code onAdd} runs before the service cache is populated.
 */
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class BindingSensor implements KubernetesSensor {

    private static final Logger log = LoggerFactory.getLogger(BindingSensor.class);
    private static final long SYNC_POLL_MS = 100;
    private static final long SYNC_TIMEOUT_MS = 60_000;

    private final KubernetesClient client;
    private final MetisProperties properties;
    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;
    private final String cneeNamespace;

    private final List<SharedIndexInformer<Pod>> podInformers = new ArrayList<>();
    private final List<SharedIndexInformer<Service>> svcInformers = new ArrayList<>();
    private final List<SharedIndexInformer<Endpoints>> endpointsInformers = new ArrayList<>();

    private ScheduledExecutorService syncExecutor;

    public BindingSensor(KubernetesClient client,
                         MetisProperties properties,
                         SensorEventPublisher publisher,
                         IriFactory iriFactory) {
        this.client = client;
        this.properties = properties;
        this.publisher = publisher;
        this.iriFactory = iriFactory;
        this.cneeNamespace = properties.ontology().cneeNamespace();
    }

    private static String nodeName(Pod pod) {
        if (pod.getSpec() == null) return null;
        return pod.getSpec().getNodeName();
    }

    private static Map<String, String> selector(Service svc) {
        if (svc.getSpec() == null) return null;
        return svc.getSpec().getSelector();
    }

    private static boolean selectorMatches(Service svc, Map<String, String> podLabels) {
        Map<String, String> sel = selector(svc);
        if (sel == null || sel.isEmpty()) return false;
        return podLabels.entrySet().containsAll(sel.entrySet());
    }

    // -------------------------------------------------------------------------

    private static boolean selectorsEqual(Service a, Service b) {
        return Objects.equals(selector(a), selector(b));
    }

    @Override
    public String name() {
        return "BindingSensor";
    }

    @Override
    public void start() {
        List<String> namespaces = properties.sensor() != null
                ? properties.sensor().namespaces()
                : List.of();

        syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "binding-sensor-sync");
            t.setDaemon(true);
            return t;
        });

        if (namespaces.isEmpty()) {
            startForNamespace(null);
            log.info("BindingSensor watching all namespaces");
        } else {
            for (String ns : namespaces) {
                startForNamespace(ns);
            }
            log.info("BindingSensor watching namespaces: {}", namespaces);
        }
    }

    @Override
    public void stop() {
        if (syncExecutor != null) {
            syncExecutor.shutdownNow();
        }
        for (SharedIndexInformer<?> informer : podInformers) {
            try {
                informer.stop();
            } catch (Exception e) {
                log.warn("BindingSensor error stopping pod informer: {}", e.getMessage());
            }
        }
        for (SharedIndexInformer<?> informer : svcInformers) {
            try {
                informer.stop();
            } catch (Exception e) {
                log.warn("BindingSensor error stopping service informer: {}", e.getMessage());
            }
        }
        for (SharedIndexInformer<?> informer : endpointsInformers) {
            try {
                informer.stop();
            } catch (Exception e) {
                log.warn("BindingSensor error stopping endpoints informer: {}", e.getMessage());
            }
        }
        podInformers.clear();
        svcInformers.clear();
        endpointsInformers.clear();
        log.info("BindingSensor stopped");
    }

    // -------------------------------------------------------------------------
    // Service → Pod Realization (via Endpoints)

    private void startForNamespace(String namespace) {
        var podOp = namespace != null
                ? client.pods().inNamespace(namespace)
                : client.pods().inAnyNamespace();

        var svcOp = namespace != null
                ? client.services().inNamespace(namespace)
                : client.services().inAnyNamespace();

        var endpointsOp = namespace != null
                ? client.endpoints().inNamespace(namespace)
                : client.endpoints().inAnyNamespace();

        SharedIndexInformer<Service> svcInformer = svcOp.inform();
        SharedIndexInformer<Pod> podInformer = podOp.inform();
        SharedIndexInformer<Endpoints> endpointsInformer = endpointsOp.inform();

        svcInformers.add(svcInformer);
        podInformers.add(podInformer);
        endpointsInformers.add(endpointsInformer);

        // Wait for all caches to sync before attaching cross-handlers.
        syncExecutor.execute(() -> waitForSyncAndAttach(podInformer, svcInformer, endpointsInformer, namespace));
    }

    // -------------------------------------------------------------------------
    // Pod → Node

    /**
     * Polls until all informers report synced, then attaches event handlers
     * and emits a one-time reconciliation sweep over existing data.
     */
    private void waitForSyncAndAttach(SharedIndexInformer<Pod> podInformer,
                                      SharedIndexInformer<Service> svcInformer,
                                      SharedIndexInformer<Endpoints> endpointsInformer,
                                      String namespace) {
        long deadline = System.currentTimeMillis() + SYNC_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (podInformer.hasSynced() && svcInformer.hasSynced() && endpointsInformer.hasSynced()) {
                log.info("BindingSensor caches synced [namespace={}] — attaching handlers",
                        namespace == null ? "<all>" : namespace);
                attachHandlers(podInformer, svcInformer, endpointsInformer);
                reconcileExisting(podInformer.getIndexer(), svcInformer.getIndexer(), endpointsInformer.getIndexer());
                return;
            }
            try {
                Thread.sleep(SYNC_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("BindingSensor timed out waiting for cache sync [namespace={}] — attaching handlers anyway",
                namespace == null ? "<all>" : namespace);
        attachHandlers(podInformer, svcInformer, endpointsInformer);
        reconcileExisting(podInformer.getIndexer(), svcInformer.getIndexer(), endpointsInformer.getIndexer());
    }

    // -------------------------------------------------------------------------
    // Pod → Services

    private void attachHandlers(SharedIndexInformer<Pod> podInformer,
                                SharedIndexInformer<Service> svcInformer,
                                SharedIndexInformer<Endpoints> endpointsInformer) {
        podInformer.addEventHandler(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Pod pod) {
                emitPodNodeBinding(pod);
                emitPodServiceBindings(pod, svcInformer.getIndexer());
            }

            @Override
            public void onUpdate(Pod oldPod, Pod newPod) {
                String oldNode = nodeName(oldPod);
                String newNode = nodeName(newPod);
                if (newNode != null && !newNode.equals(oldNode)) {
                    emitPodNodeBinding(newPod);
                }
                if (!Objects.equals(oldPod.getMetadata().getLabels(), newPod.getMetadata().getLabels())) {
                    emitPodServiceBindings(newPod, svcInformer.getIndexer());
                }
            }

            @Override
            public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
                // Deletion handled by PodSensor — KnowledgeBaseWriter.deleteEntity removes all triples
            }
        });

        svcInformer.addEventHandler(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Service svc) {
                emitServicePodBindings(svc, podInformer.getIndexer());
            }

            @Override
            public void onUpdate(Service oldSvc, Service newSvc) {
                if (!selectorsEqual(oldSvc, newSvc)) {
                    emitServicePodBindings(newSvc, podInformer.getIndexer());
                }
            }

            @Override
            public void onDelete(Service svc, boolean deletedFinalStateUnknown) {
                // Deletion handled by ServiceSensor
            }
        });

        endpointsInformer.addEventHandler(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Endpoints endpoints) {
                emitServicePodRealization(endpoints);
            }

            @Override
            public void onUpdate(Endpoints oldEndpoints, Endpoints newEndpoints) {
                if (!Objects.equals(oldEndpoints.getSubsets(), newEndpoints.getSubsets())) {
                    emitServicePodRealization(newEndpoints);
                }
            }

            @Override
            public void onDelete(Endpoints endpoints, boolean deletedFinalStateUnknown) {
                // Handled by Service deletion
            }
        });
    }

    // -------------------------------------------------------------------------
    // Service → Pods

    /**
     * One-time sweep over already-cached resources after sync barrier passes.
     */
    private void reconcileExisting(Indexer<Pod> podCache,
                                   Indexer<Service> serviceCache,
                                   Indexer<Endpoints> endpointsCache) {
        for (Pod pod : podCache.list()) {
            emitPodNodeBinding(pod);
            emitPodServiceBindings(pod, serviceCache);
        }
        for (Endpoints endpoints : endpointsCache.list()) {
            emitServicePodRealization(endpoints);
        }
        log.info("BindingSensor reconciliation complete [pods={}, services={}, endpoints={}]",
                podCache.list().size(), serviceCache.list().size(), endpointsCache.list().size());
    }

    // -------------------------------------------------------------------------

    private void emitServicePodRealization(Endpoints endpoints) {
        String ns = endpoints.getMetadata().getNamespace();
        String svcName = endpoints.getMetadata().getName();
        String svcIri = iriFactory.namespacedIri(CneeOntology.KIND_SERVICE, ns, svcName);

        if (endpoints.getSubsets() == null) return;

        for (EndpointSubset subset : endpoints.getSubsets()) {
            if (subset.getAddresses() == null) continue;
            for (EndpointAddress address : subset.getAddresses()) {
                if (address.getTargetRef() != null && "Pod".equals(address.getTargetRef().getKind())) {
                    String podName = address.getTargetRef().getName();
                    String podIri = iriFactory.namespacedIri(CneeOntology.KIND_POD, ns, podName);
                    emitRelationship(svcIri, CneeOntology.PROP_IS_REALIZED_BY, podIri);
                    log.debug("BindingSensor: service {}/{} isRealizedBy pod {}", ns, svcName, podName);
                }
            }
        }
    }

    private void emitPodNodeBinding(Pod pod) {
        String node = nodeName(pod);
        if (node == null || node.isBlank()) return;

        String ns = pod.getMetadata().getNamespace();
        String podName = pod.getMetadata().getName();
        String podIri = iriFactory.namespacedIri(CneeOntology.KIND_POD, ns, podName);
        String nodeIri = iriFactory.clusterScopedIri(CneeOntology.KIND_NODE, node);

        emitRelationship(podIri, CneeOntology.PROP_IS_HOSTED_ON, nodeIri);
        log.debug("BindingSensor: pod {}/{} isHostedOn node {}", ns, podName, node);
    }

    private void emitPodServiceBindings(Pod pod, Indexer<Service> serviceCache) {
        String ns = pod.getMetadata().getNamespace();
        String podName = pod.getMetadata().getName();
        Map<String, String> podLabels = pod.getMetadata().getLabels();
        if (podLabels == null || podLabels.isEmpty()) return;

        for (Service svc : serviceCache.list()) {
            if (!ns.equals(svc.getMetadata().getNamespace())) continue;
            if (!selectorMatches(svc, podLabels)) continue;

            String svcIri = iriFactory.namespacedIri(CneeOntology.KIND_SERVICE, ns, svc.getMetadata().getName());
            String podIri = iriFactory.namespacedIri(CneeOntology.KIND_POD, ns, podName);
            emitRelationship(svcIri, CneeOntology.PROP_CONTAINS, podIri);
            log.debug("BindingSensor: service {}/{} contains pod {}", ns, svc.getMetadata().getName(), podName);
        }
    }

    private void emitServicePodBindings(Service svc, Indexer<Pod> podCache) {
        Map<String, String> selector = selector(svc);
        if (selector == null || selector.isEmpty()) return;

        String ns = svc.getMetadata().getNamespace();
        String svcName = svc.getMetadata().getName();
        String svcIri = iriFactory.namespacedIri(CneeOntology.KIND_SERVICE, ns, svcName);

        for (Pod pod : podCache.list()) {
            if (!ns.equals(pod.getMetadata().getNamespace())) continue;
            Map<String, String> podLabels = pod.getMetadata().getLabels();
            if (podLabels == null || !podLabels.entrySet().containsAll(selector.entrySet())) continue;

            String podIri = iriFactory.namespacedIri(CneeOntology.KIND_POD, ns, pod.getMetadata().getName());
            emitRelationship(svcIri, CneeOntology.PROP_CONTAINS, podIri);
            log.debug("BindingSensor: service {}/{} contains pod {}", ns, svcName, pod.getMetadata().getName());
        }
    }

    private void emitRelationship(String subjectIri, String predicateLocalName, String objectIri) {
        RelationshipAssertedEvent rel = RelationshipAssertedEvent.newBuilder()
                .setSubjectIri(subjectIri)
                .setPredicate(cneeNamespace + predicateLocalName)
                .setObjectIri(objectIri)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setRelationshipAsserted(rel)));
    }
}
