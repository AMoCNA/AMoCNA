package com.kubiki.palamedes.scig;

/**
 * Single vulnerability finding from OSV for a package in an image SBOM.
 */
public record OsvFinding(
        String vulnId,
        ScigSeverity severity,
        String packageName,
        String packageVersion,
        String ecosystem,
        String summary
) {
}
