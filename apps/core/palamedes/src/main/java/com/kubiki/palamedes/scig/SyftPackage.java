package com.kubiki.palamedes.scig;

/**
 * Package coordinate extracted from a Syft SBOM artifact.
 */
public record SyftPackage(
        String name,
        String version,
        String syftType,
        String osvEcosystem,
        String purl
) {
}
