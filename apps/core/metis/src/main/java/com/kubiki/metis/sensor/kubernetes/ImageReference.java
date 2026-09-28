package com.kubiki.metis.sensor.kubernetes;

/**
 * Parsed OCI/Docker image reference (registry host, repository path, tag).
 */
public record ImageReference(String repository, String tag) {

    private static final String DEFAULT_TAG = "latest";
    private static final String DEFAULT_REGISTRY = "docker.io";

    public static ImageReference parse(String image) {
        if (image == null || image.isBlank()) {
            return new ImageReference("unknown", DEFAULT_TAG);
        }
        String trimmed = image.trim();
        int atDigest = trimmed.indexOf('@');
        if (atDigest >= 0) {
            trimmed = trimmed.substring(0, atDigest);
        }
        int lastSlash = trimmed.lastIndexOf('/');
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon > lastSlash) {
            return new ImageReference(trimmed.substring(0, lastColon), trimmed.substring(lastColon + 1));
        }
        return new ImageReference(trimmed, DEFAULT_TAG);
    }

    private static boolean isRegistryHost(String segment) {
        return segment.contains(".") || segment.contains(":") || "localhost".equals(segment);
    }

    public String fullReference() {
        return repositoryPath() + ":" + tag;
    }

    public String registryHost() {
        int slash = repository.indexOf('/');
        if (slash > 0) {
            String candidate = repository.substring(0, slash);
            if (isRegistryHost(candidate)) {
                return candidate;
            }
        }
        return DEFAULT_REGISTRY;
    }

    public String repositoryPath() {
        int slash = repository.indexOf('/');
        if (slash > 0) {
            String candidate = repository.substring(0, slash);
            if (isRegistryHost(candidate)) {
                return repository.substring(slash + 1);
            }
        }
        return repository;
    }
}
