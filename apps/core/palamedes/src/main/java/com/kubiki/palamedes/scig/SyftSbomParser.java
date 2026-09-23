package com.kubiki.palamedes.scig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses Syft JSON SBOM into package coordinates suitable for OSV queries.
 */
@Component
public class SyftSbomParser {

    private static final Logger log = LoggerFactory.getLogger(SyftSbomParser.class);

    private static final Map<String, String> SYFT_TYPE_TO_OSV = Map.ofEntries(
            Map.entry("npm", "npm"),
            Map.entry("python", "PyPI"),
            Map.entry("python-package", "PyPI"),
            Map.entry("go-module", "Go"),
            Map.entry("java-archive", "Maven"),
            Map.entry("maven", "Maven"),
            Map.entry("deb", "Debian"),
            Map.entry("dpkg", "Debian"),
            Map.entry("apk", "Alpine"),
            Map.entry("rpm", "Red Hat"),
            Map.entry("gem", "RubyGems"),
            Map.entry("rust-crate", "crates.io"),
            Map.entry("dotnet", "NuGet")
    );

    private final ObjectMapper objectMapper;

    public SyftSbomParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<SyftPackage> parsePackages(String syftJson) {
        if (syftJson == null || syftJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(syftJson);
            JsonNode artifacts = root.path("artifacts");
            if (!artifacts.isArray()) {
                log.warn("Syft SBOM has no artifacts array");
                return List.of();
            }

            Set<String> seen = new LinkedHashSet<>();
            List<SyftPackage> packages = new ArrayList<>();
            for (JsonNode artifact : artifacts) {
                String name = text(artifact, "name");
                String version = text(artifact, "version");
                String type = text(artifact, "type");
                if (name.isBlank() || version.isBlank() || type.isBlank()) {
                    continue;
                }
                String ecosystem = SYFT_TYPE_TO_OSV.get(type.toLowerCase(Locale.ROOT));
                if (ecosystem == null) {
                    continue;
                }
                String dedupe = ecosystem + "|" + name + "|" + version;
                if (!seen.add(dedupe)) {
                    continue;
                }
                String purl = text(artifact, "purl");
                packages.add(new SyftPackage(name, version, type, ecosystem, purl.isBlank() ? null : purl));
            }
            return List.copyOf(packages);
        } catch (Exception e) {
            log.error("Failed to parse Syft SBOM JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }
}
