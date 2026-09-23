package com.kubiki.palamedes.scig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SCIG PoC planner: Redis SBOM → OSV → YAML policy → decision (log / later Themis).
 *
 * <p>Does not yet dispatch Themis workflows for {@code delete_pod}; {@code patch_image}
 * and {@code fail_safe} are recorded as decisions for the next wiring step.
 */
@Service
@ConditionalOnProperty(name = "palamedes.scig.enabled", havingValue = "true")
public class ScigPlanner {

    private static final Logger log = LoggerFactory.getLogger(ScigPlanner.class);

    private final SbomRedisClient sbomRedisClient;
    private final SyftSbomParser syftSbomParser;
    private final OsvClient osvClient;
    private final ScigPolicyEngine policyEngine;
    private final int maxPackagesPerImage;
    private final List<String> preferredEcosystems;

    public ScigPlanner(
            SbomRedisClient sbomRedisClient,
            SyftSbomParser syftSbomParser,
            OsvClient osvClient,
            ScigPolicyEngine policyEngine,
            @Value("${palamedes.scig.max-packages-per-image:80}") int maxPackagesPerImage,
            @Value("${palamedes.scig.preferred-ecosystems:npm}") String preferredEcosystems) {
        this.sbomRedisClient = sbomRedisClient;
        this.syftSbomParser = syftSbomParser;
        this.osvClient = osvClient;
        this.policyEngine = policyEngine;
        this.maxPackagesPerImage = Math.max(1, maxPackagesPerImage);
        this.preferredEcosystems = List.of(preferredEcosystems.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * Scans all images that have SBOMs in Redis and evaluates policies.
     *
     * @return true if any non-{@link ScigAction#FAIL_SAFE} decision was produced
     */
    public boolean scanAndDecide() {
        List<SbomRedisClient.ImageRef> images = sbomRedisClient.listScannedImages();
        if (images.isEmpty()) {
            log.info("SCIG: no SBOMs found in Redis (keys sbom:meta:*)");
            return false;
        }

        log.info("SCIG: evaluating {} image(s) from Redis", images.size());
        boolean actionable = false;
        for (SbomRedisClient.ImageRef image : images) {
            ScigDecision decision = evaluateImage(image.repository(), image.tag(), null);
            logDecision(decision);
            if (decision.action() != ScigAction.FAIL_SAFE) {
                actionable = true;
            }
        }
        return actionable;
    }

    public ScigDecision evaluateImage(String repository, String tag, String namespace) {
        Optional<String> sbom = sbomRedisClient.getSbomJson(repository, tag);
        if (sbom.isEmpty()) {
            if (policyEngine.document().sbomRequired()) {
                return new ScigDecision(
                        repository, tag, 0, 0, 0, ScigSeverity.LOW,
                        "sbom-missing", ScigAction.FAIL_SAFE, null, List.of(),
                        "SBOM required but missing in Redis");
            }
            return new ScigDecision(
                    repository, tag, 0, 0, 0, ScigSeverity.LOW,
                    "sbom-missing", ScigAction.FAIL_SAFE, null, List.of(),
                    "No SBOM in Redis — skipped");
        }

        List<SyftPackage> allPackages = syftSbomParser.parsePackages(sbom.get());
        List<SyftPackage> toQuery = selectPackages(allPackages);
        List<OsvFinding> findings = osvClient.queryVulnerabilities(toQuery);

        ScigSeverity maxSeverity = findings.stream()
                .map(OsvFinding::severity)
                .max(Comparator.naturalOrder())
                .orElse(ScigSeverity.LOW);

        Optional<ScigPolicyDocument.PolicyRule> rule = policyEngine.evaluate(maxSeverity, namespace);
        if (rule.isEmpty()) {
            return new ScigDecision(
                    repository, tag, allPackages.size(), toQuery.size(), findings.size(), maxSeverity,
                    "no-match", ScigAction.FAIL_SAFE, null, topFindings(findings),
                    "No policy matched");
        }

        ScigPolicyDocument.PolicyRule matched = rule.get();
        String targetImage = matched.remediation() == null ? null : matched.remediation().targetImage();
        return new ScigDecision(
                repository, tag, allPackages.size(), toQuery.size(), findings.size(), maxSeverity,
                matched.name(), matched.action(), targetImage, topFindings(findings),
                "Matched policy " + matched.name());
    }

    private List<SyftPackage> selectPackages(List<SyftPackage> all) {
        List<SyftPackage> preferred = all.stream()
                .filter(p -> preferredEcosystems.isEmpty()
                        || preferredEcosystems.contains(p.osvEcosystem().toLowerCase(Locale.ROOT))
                        || preferredEcosystems.contains(p.syftType().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toCollection(ArrayList::new));

        List<SyftPackage> source = preferred.isEmpty() ? all : preferred;
        if (source.size() <= maxPackagesPerImage) {
            return source;
        }
        log.info("SCIG: capping OSV queries from {} to {} packages", source.size(), maxPackagesPerImage);
        return List.copyOf(source.subList(0, maxPackagesPerImage));
    }

    private static List<OsvFinding> topFindings(List<OsvFinding> findings) {
        return findings.stream().limit(5).toList();
    }

    private void logDecision(ScigDecision decision) {
        log.info(
                "SCIG decision: image={} packages={}/{} findings={} maxSeverity={} policy={} action={} target={} note={} top={}",
                decision.imageRef(),
                decision.packagesQueried(),
                decision.packageCount(),
                decision.findingCount(),
                decision.maxSeverity(),
                decision.policyName(),
                decision.action(),
                decision.targetImage(),
                decision.note(),
                decision.topFindings().stream()
                        .map(f -> f.vulnId() + ":" + f.severity())
                        .toList());
    }
}
