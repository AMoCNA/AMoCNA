package com.kubiki.metis.sensor;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.knowledge.CneeOntology;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Constructs CNEEOnt IRIs for Kubernetes resources.
 *
 * <p>IRI scheme:
 * <ul>
 *   <li>Namespaced resources: {@code cnee:<Kind>_<namespace>_<name>}
 *       e.g. {@code cnee:Pod_default_my-pod-abc}</li>
 *   <li>Cluster-scoped resources: {@code cnee:<Kind>_<name>}
 *       e.g. {@code cnee:Node_worker-1}</li>
 * </ul>
 *
 * <p>All segments are percent-encoded so that special characters in names
 * (hyphens, dots, slashes) do not break IRI parsing.
 */
@Component
public class IriFactory {

    private final String cneeNamespace;

    public IriFactory(MetisProperties properties) {
        this.cneeNamespace = properties.ontology().cneeNamespace();
    }

    private static String encode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * @return the configured CNEEOnt namespace IRI prefix
     */
    public String getCneeNamespace() {
        return cneeNamespace;
    }

    /**
     * IRI for a namespaced resource (Pod, Service, …).
     *
     * @param kind      Kubernetes kind, e.g. {@link com.kubiki.metis.knowledge.CneeOntology#KIND_POD}
     * @param namespace Kubernetes namespace
     * @param name      resource name
     * @return fully qualified CNEEOnt IRI string
     */
    public String namespacedIri(String kind, String namespace, String name) {
        return cneeNamespace + encode(kind) + "_" + encode(namespace) + "_" + encode(name);
    }

    /**
     * IRI for a cluster-scoped resource (Node, …).
     *
     * @param kind Kubernetes kind, e.g. {@link com.kubiki.metis.knowledge.CneeOntology#KIND_NODE}
     * @param name resource name
     * @return fully qualified CNEEOnt IRI string
     */
    public String clusterScopedIri(String kind, String name) {
        return cneeNamespace + encode(kind) + "_" + encode(name);
    }

    // -------------------------------------------------------------------------

    /**
     * CNEEOnt type IRI for a given local class name.
     *
     * @param localName e.g. {@code "ExecutionUnit"}
     * @return fully qualified CNEEOnt class IRI
     */
    public String typeIri(String localName) {
        return cneeNamespace + localName;
    }

    /**
     * IRI for a container image reference (repository + tag).
     */
    public String imageIri(String repository, String tag) {
        return cneeNamespace + encode(CneeOntology.KIND_IMAGE)
                + "_" + encode(repository) + "_" + encode(tag);
    }

    /**
     * IRI for an OCI/Docker registry host (e.g. docker.io).
     */
    public String imageRegistryIri(String registryHost) {
        return cneeNamespace + encode(CneeOntology.KIND_IMAGE_REGISTRY) + "_" + encode(registryHost);
    }

    /**
     * IRI for a container running inside a pod.
     */
    public String containerIri(String namespace, String podName, String containerName) {
        return namespacedIri(CneeOntology.KIND_CONTAINER, namespace, podName + "_" + containerName);
    }

    /**
     * IRI for a Kubernetes Secret (e.g. a registry pull secret).
     */
    public String secretIri(String namespace, String name) {
        return namespacedIri(CneeOntology.KIND_SECRET, namespace, name);
    }
}
