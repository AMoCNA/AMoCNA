package com.kubiki.palamedes.model;

import org.eclipse.rdf4j.model.IRI;

/**
 * A workload stuck in {@code ImagePullBackOff} for which the registry pull secret
 * has been inferred from a sibling workload pulling from the same registry.
 *
 * @param deploymentIri  IRI of the Deployment to patch (topmost owner of the failing pod)
 * @param deploymentName the Deployment's {@code resourceName} (fills {@code ${resourceName}})
 * @param namespace      the namespace shared by the failing pod, sibling, and secret
 * @param pullSecretName the existing pull secret to add to {@code imagePullSecrets}
 */
public record RegistryAuthTarget(
        IRI deploymentIri,
        String deploymentName,
        String namespace,
        String pullSecretName
) {
}
