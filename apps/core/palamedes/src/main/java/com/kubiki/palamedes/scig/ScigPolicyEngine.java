package com.kubiki.palamedes.scig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads and evaluates ordered SCIG YAML policies (first match wins).
 */
@Component
@ConditionalOnProperty(name = "palamedes.scig.enabled", havingValue = "true")
public class ScigPolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(ScigPolicyEngine.class);

    private final ScigPolicyDocument document;

    public ScigPolicyEngine(
            ResourceLoader resourceLoader,
            ObjectMapper jsonMapper,
            @Value("${palamedes.scig.policies-location:classpath:scig/policies.yaml}") String location) {
        this.document = load(resourceLoader, location);
        log.info("SCIG: loaded {} policies from {}", document.policies().size(), location);
    }

    public ScigPolicyDocument document() {
        return document;
    }

    public Optional<ScigPolicyDocument.PolicyRule> evaluate(
            ScigSeverity maxSeverity,
            String namespace) {
        for (ScigPolicyDocument.PolicyRule rule : document.policies()) {
            if (!maxSeverity.atLeast(rule.match().severityAtLeast())) {
                continue;
            }
            List<String> namespaces = rule.match().namespaces();
            if (namespaces != null && !namespaces.isEmpty()) {
                if (namespace == null || namespace.isBlank()
                        || namespaces.stream().noneMatch(ns -> ns.equalsIgnoreCase(namespace))) {
                    continue;
                }
            }
            return Optional.of(rule);
        }
        return Optional.empty();
    }

    static ScigPolicyDocument load(ResourceLoader resourceLoader, String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                log.warn("SCIG policies not found at {}, using empty fail-safe defaults", location);
                return defaultDocument();
            }
            ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
            try (InputStream in = resource.getInputStream()) {
                JsonNode root = yaml.readTree(in);
                JsonNode scig = root.path("scig");
                boolean sbomRequired = scig.path("sbom-required").asBoolean(false)
                        || scig.path("sbomRequired").asBoolean(false);
                List<ScigPolicyDocument.PolicyRule> rules = new ArrayList<>();
                JsonNode policies = scig.path("policies");
                if (policies.isArray()) {
                    for (JsonNode node : policies) {
                        rules.add(parseRule(node));
                    }
                }
                return new ScigPolicyDocument(sbomRequired, List.copyOf(rules));
            }
        } catch (Exception e) {
            log.error("Failed to load SCIG policies from {}: {}", location, e.getMessage());
            return defaultDocument();
        }
    }

    private static ScigPolicyDocument.PolicyRule parseRule(JsonNode node) {
        String name = text(node, "name", "unnamed");
        JsonNode matchNode = node.path("match");
        ScigSeverity severity = ScigSeverity.parse(text(matchNode, "severityAtLeast", "LOW"));
        List<String> namespaces = null;
        JsonNode nsNode = matchNode.get("namespaces");
        if (nsNode != null && nsNode.isArray()) {
            namespaces = new ArrayList<>();
            for (JsonNode ns : nsNode) {
                namespaces.add(ns.asText());
            }
        }
        ScigAction action = ScigAction.parse(text(node, "action", "fail_safe"));
        JsonNode remNode = node.get("remediation");
        String targetImage = remNode == null ? null : text(remNode, "targetImage", null);
        if (targetImage != null && targetImage.isBlank()) {
            targetImage = null;
        }
        return new ScigPolicyDocument.PolicyRule(
                name,
                new ScigPolicyDocument.Match(severity, namespaces),
                action,
                new ScigPolicyDocument.Remediation(targetImage));
    }

    private static ScigPolicyDocument defaultDocument() {
        return new ScigPolicyDocument(false, List.of(
                new ScigPolicyDocument.PolicyRule(
                        "default-observe",
                        new ScigPolicyDocument.Match(ScigSeverity.LOW, null),
                        ScigAction.FAIL_SAFE,
                        new ScigPolicyDocument.Remediation(null))));
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        if (node == null || node.isMissingNode()) {
            return defaultValue;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        String text = value.asText(null);
        return text == null ? defaultValue : text.trim();
    }
}
