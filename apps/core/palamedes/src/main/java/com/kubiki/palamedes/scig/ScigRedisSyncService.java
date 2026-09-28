package com.kubiki.palamedes.scig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.common.vulnerability.ScigRedisClient;
import com.kubiki.common.vulnerability.UpgradePolicy;
import com.kubiki.common.vulnerability.VulnerabilityCatalog;
import com.kubiki.common.vulnerability.VulnerabilityRecord;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Synchronizes Grype vulnerability scan reports stored by SCIG in Redis into Palamedes VulnerabilityCatalog.
 *
 * <p>Grype {@code fix.versions} are package versions, not container image tags. Dynamic records are
 * merged as detection evidence only ({@code fixedVersions} empty); curated catalog entries still
 * supply image-tag remediations.
 */
@Service
@RequiredArgsConstructor
public class ScigRedisSyncService {

    private static final Logger log = LoggerFactory.getLogger(ScigRedisSyncService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    /** Skip pathological Grype blobs that would stall the remediation path. */
    private static final int MAX_CVE_JSON_BYTES = 2_000_000;
    private static final int MAX_KEYS_PER_SYNC = 40;
    private static final int MAX_RECORDS_PER_SYNC = 2_000;

    private final VulnerabilityCatalog vulnerabilityCatalog;

    @Value("${scig.redis.host:redis.redis.svc.cluster.local}")
    private String redisHost;

    @Value("${scig.redis.port:6379}")
    private int redisPort;

    public void syncFromRedis() {
        ScigRedisClient client = new ScigRedisClient(redisHost, redisPort);
        if (!client.ping()) {
            log.debug("ScigRedisSyncService: Redis at {}:{} not reachable. Skipping dynamic SCIG sync.", redisHost, redisPort);
            return;
        }

        List<String> cveKeys = client.scanKeys("sbom:cve:*");
        if (cveKeys.isEmpty()) {
            log.debug("ScigRedisSyncService: No sbom:cve:* keys found in Redis.");
            return;
        }

        List<VulnerabilityRecord> dynamicRecords = new ArrayList<>();
        int keysConsidered = 0;
        for (String key : cveKeys) {
            if (keysConsidered >= MAX_KEYS_PER_SYNC || dynamicRecords.size() >= MAX_RECORDS_PER_SYNC) {
                break;
            }
            keysConsidered++;

            long size = client.strlen(key);
            if (size > MAX_CVE_JSON_BYTES) {
                log.warn("ScigRedisSyncService: Skipping oversized CVE key {} ({} bytes)", key, size);
                continue;
            }

            String json = client.get(key);
            if (json == null || json.isBlank()) {
                continue;
            }

            ParsedKey parsed = parseCveKey(key);
            if (parsed == null) {
                log.debug("ScigRedisSyncService: Skipping unparseable CVE key {}", key);
                continue;
            }

            try {
                JsonNode root = objectMapper.readTree(json);
                JsonNode matches = root.get("matches");
                if (matches == null || !matches.isArray()) {
                    continue;
                }
                for (JsonNode match : matches) {
                    if (dynamicRecords.size() >= MAX_RECORDS_PER_SYNC) {
                        break;
                    }
                    JsonNode vuln = match.get("vulnerability");
                    if (vuln == null) {
                        continue;
                    }

                    String cveId = vuln.has("id") ? vuln.get("id").asText() : "UNKNOWN";
                    String severity = vuln.has("severity") ? vuln.get("severity").asText() : "Medium";

                    // Do not map Grype package fix versions onto container image tags.
                    VulnerabilityRecord record = new VulnerabilityRecord(
                            cveId,
                            parsed.repository(),
                            List.of(parsed.tag()),
                            List.of(),
                            severity,
                            UpgradePolicy.PATCH
                    );
                    dynamicRecords.add(record);
                }
            } catch (Exception e) {
                log.warn("Failed to parse SCIG CVE JSON from Redis key {}: {}", key, e.getMessage());
            }
        }

        if (!dynamicRecords.isEmpty()) {
            vulnerabilityCatalog.mergeRecords(dynamicRecords);
            log.info("ScigRedisSyncService: Successfully synced {} CVE records from SCIG Redis scan reports", dynamicRecords.size());
        }
    }

    /**
     * Key format: {@code sbom:cve:{repository}:{tag}} where repository may contain {@code /}
     * and an optional registry host ({@code docker.io/...}).
     */
    static ParsedKey parseCveKey(String key) {
        if (key == null || !key.startsWith("sbom:cve:")) {
            return null;
        }
        String rest = key.substring("sbom:cve:".length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon <= 0 || lastColon == rest.length() - 1) {
            return null;
        }
        String repo = normalizeRepository(rest.substring(0, lastColon));
        String tag = rest.substring(lastColon + 1);
        if (repo.isBlank() || tag.isBlank()) {
            return null;
        }
        return new ParsedKey(repo, tag);
    }

    /** Align Redis image refs with Metis {@code cnee:resourceName} (path without registry host). */
    static String normalizeRepository(String repository) {
        if (repository == null || repository.isBlank()) {
            return "";
        }
        String trimmed = repository.trim();
        int slash = trimmed.indexOf('/');
        if (slash > 0) {
            String candidate = trimmed.substring(0, slash);
            if (candidate.contains(".") || candidate.contains(":") || "localhost".equalsIgnoreCase(candidate)) {
                return trimmed.substring(slash + 1);
            }
        }
        return trimmed;
    }

    record ParsedKey(String repository, String tag) {
    }
}
