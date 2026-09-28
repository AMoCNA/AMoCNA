package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.sensor.KubernetesSensor;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for sensors that watch namespaced Kubernetes resources.
 *
 * <p>Handles namespace iteration and informer lifecycle (start/stop).
 * Subclasses implement {@link #createInformer(KubernetesClient, String)} to
 * produce a {@link SharedIndexInformer} for their specific resource type and
 * namespace, and {@link #name()} for logging.
 *
 * <p>If {@code metis.sensor.namespaces} is empty, a single informer watching
 * all namespaces is created instead.
 */
public abstract class AbstractNamespacedSensor implements KubernetesSensor {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final KubernetesClient client;
    private final List<String> namespaces;
    private final List<SharedIndexInformer<?>> informers = new ArrayList<>();

    protected AbstractNamespacedSensor(KubernetesClient client, MetisProperties properties) {
        this.client = client;
        this.namespaces = properties.sensor() != null ? properties.sensor().namespaces() : List.of();
    }

    @Override
    public final void start() {
        if (namespaces.isEmpty()) {
            // Watch all namespaces
            SharedIndexInformer<?> informer = createInformer(client, null);
            informer.start();
            informers.add(informer);
            log.info("{} watching all namespaces", name());
        } else {
            for (String ns : namespaces) {
                SharedIndexInformer<?> informer = createInformer(client, ns);
                informer.start();
                informers.add(informer);
            }
            log.info("{} watching namespaces: {}", name(), namespaces);
        }
    }

    @Override
    public final void stop() {
        for (SharedIndexInformer<?> informer : informers) {
            try {
                informer.stop();
            } catch (Exception e) {
                log.warn("{} error stopping informer: {}", name(), e.getMessage());
            }
        }
        informers.clear();
        log.info("{} stopped", name());
    }

    /**
     * Create a {@link SharedIndexInformer} for the resource type this sensor watches.
     *
     * @param client    the Fabric8 client
     * @param namespace the namespace to watch, or {@code null} to watch all namespaces
     * @return a configured (but not yet started) informer
     */
    protected abstract SharedIndexInformer<?> createInformer(KubernetesClient client, String namespace);
}
