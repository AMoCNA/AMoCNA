package com.kubiki.palamedes.model;

import org.eclipse.rdf4j.model.IRI;

public record ImageUpdateTarget(
        IRI deploymentIri,
        String deploymentName,
        String namespace,
        String containerName,
        String imageRepository,
        String currentVersion,
        String targetVersion,
        IRI serviceIri,
        String serviceName
) {
    public ImageUpdateTarget withTargetVersion(String targetVersion) {
        return new ImageUpdateTarget(
                deploymentIri, deploymentName, namespace, containerName,
                imageRepository, currentVersion, targetVersion, serviceIri, serviceName);
    }
}
