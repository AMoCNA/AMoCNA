package com.kubiki.palamedes.scig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Queries <a href="https://osv.dev">OSV.dev</a> for vulnerabilities matching SBOM packages.
 */
@Component
@ConditionalOnProperty(name = "palamedes.scig.enabled", havingValue = "true")
public class OsvClient {

    private static final Logger log = LoggerFactory.getLogger(OsvClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public OsvClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${palamedes.scig.osv-base-url:https://api.osv.dev}") String baseUrl,
            @Value("${palamedes.scig.osv-batch-size:40}") int batchSize) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.batchSize = Math.max(1, batchSize);
    }

    public List<OsvFinding> queryVulnerabilities(List<SyftPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return List.of();
        }

        Map<String, OsvFinding> byVulnAndPkg = new LinkedHashMap<>();
        for (int offset = 0; offset < packages.size(); offset += batchSize) {
            List<SyftPackage> chunk = packages.subList(offset, Math.min(offset + batchSize, packages.size()));
            queryBatch(chunk, byVulnAndPkg);
        }

        List<OsvFinding> findings = new ArrayList<>(byVulnAndPkg.values());
        findings.sort(Comparator
                .comparing(OsvFinding::severity)
                .reversed()
                .thenComparing(OsvFinding::vulnId));
        return List.copyOf(findings);
    }

    private void queryBatch(List<SyftPackage> chunk, Map<String, OsvFinding> sink) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode queries = body.putArray("queries");
            for (SyftPackage pkg : chunk) {
                ObjectNode query = queries.addObject();
                query.put("version", pkg.version());
                ObjectNode packageNode = query.putObject("package");
                packageNode.put("name", pkg.name());
                packageNode.put("ecosystem", pkg.osvEcosystem());
            }

            String response = restClient.post()
                    .uri("/v1/querybatch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                return;
            }

            for (int i = 0; i < results.size() && i < chunk.size(); i++) {
                SyftPackage pkg = chunk.get(i);
                JsonNode vulns = results.get(i).path("vulns");
                if (!vulns.isArray()) {
                    continue;
                }
                for (JsonNode vuln : vulns) {
                    String id = text(vuln, "id");
                    if (id.isBlank()) {
                        continue;
                    }
                    ScigSeverity severity = severityOf(vuln);
                    String summary = text(vuln, "summary");
                    String key = id + "|" + pkg.osvEcosystem() + "|" + pkg.name() + "|" + pkg.version();
                    sink.putIfAbsent(key, new OsvFinding(
                            id, severity, pkg.name(), pkg.version(), pkg.osvEcosystem(), summary));
                }
            }
        } catch (Exception e) {
            log.warn("OSV querybatch failed for {} packages: {}", chunk.size(), e.getMessage());
        }
    }

    static ScigSeverity severityOf(JsonNode vuln) {
        JsonNode severityArray = vuln.get("severity");
        if (severityArray != null && severityArray.isArray()) {
            double maxScore = -1;
            for (JsonNode sev : severityArray) {
                String type = text(sev, "type").toUpperCase(Locale.ROOT);
                String scoreRaw = text(sev, "score");
                if (scoreRaw.isBlank()) {
                    continue;
                }
                // CVSS_V3 score is often a vector string; try numeric first
                try {
                    double score = Double.parseDouble(scoreRaw);
                    maxScore = Math.max(maxScore, score);
                    continue;
                } catch (NumberFormatException ignored) {
                    // fall through — may be a CVSS vector
                }
                if (type.contains("CVSS") && scoreRaw.contains("CVSS")) {
                    Double extracted = extractCvssBaseScore(scoreRaw);
                    if (extracted != null) {
                        maxScore = Math.max(maxScore, extracted);
                    }
                }
            }
            if (maxScore >= 0) {
                return fromCvss(maxScore);
            }
        }

        JsonNode dbSpecific = vuln.get("database_specific");
        if (dbSpecific != null) {
            String sev = text(dbSpecific, "severity");
            if (!sev.isBlank()) {
                try {
                    return ScigSeverity.parse(sev.replace("SEVERITY_", ""));
                } catch (IllegalArgumentException ignored) {
                    // continue
                }
            }
        }
        return ScigSeverity.MEDIUM;
    }

    private static Double extractCvssBaseScore(String vectorOrScore) {
        // Some OSV payloads put base score in database_specific; vector parsing is best-effort.
        int idx = vectorOrScore.indexOf('/');
        if (idx > 0) {
            String maybe = vectorOrScore.substring(0, idx).replace(',', '.');
            try {
                return Double.parseDouble(maybe);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static ScigSeverity fromCvss(double score) {
        if (score >= 9.0) {
            return ScigSeverity.CRITICAL;
        }
        if (score >= 7.0) {
            return ScigSeverity.HIGH;
        }
        if (score >= 4.0) {
            return ScigSeverity.MEDIUM;
        }
        return ScigSeverity.LOW;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }
}
