package com.kubiki.palamedes.scig;

import java.util.List;

/**
 * Loaded SCIG policy document (from YAML).
 */
public record ScigPolicyDocument(
        boolean sbomRequired,
        List<PolicyRule> policies
) {
    public record PolicyRule(
            String name,
            Match match,
            ScigAction action,
            Remediation remediation
    ) {
    }

    public record Match(
            ScigSeverity severityAtLeast,
            List<String> namespaces
    ) {
    }

    public record Remediation(
            String targetImage
    ) {
    }
}
