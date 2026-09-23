package com.kubiki.palamedes.scig;

import java.util.List;

/**
 * Result of evaluating SCIG policies for one container image SBOM.
 */
public record ScigDecision(
        String repository,
        String tag,
        int packageCount,
        int packagesQueried,
        int findingCount,
        ScigSeverity maxSeverity,
        String policyName,
        ScigAction action,
        String targetImage,
        List<OsvFinding> topFindings,
        String note
) {
    public String imageRef() {
        return repository + ":" + tag;
    }
}
