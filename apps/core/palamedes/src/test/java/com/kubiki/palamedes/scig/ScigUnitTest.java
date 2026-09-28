package com.kubiki.palamedes.scig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScigUnitTest {

    @Nested
    class PolicyEngine {
        @Test
        void loadsClasspathPoliciesAndMatchesFirstRule() {
            ScigPolicyEngine engine = new ScigPolicyEngine(
                    new DefaultResourceLoader(),
                    new ObjectMapper(),
                    "classpath:scig/policies.yaml");

            Optional<ScigPolicyDocument.PolicyRule> critical =
                    engine.evaluate(ScigSeverity.CRITICAL, null);
            assertThat(critical).isPresent();
            assertThat(critical.get().name()).isEqualTo("critical-quarantine");
            assertThat(critical.get().action()).isEqualTo(ScigAction.DELETE_POD);

            Optional<ScigPolicyDocument.PolicyRule> high =
                    engine.evaluate(ScigSeverity.HIGH, null);
            assertThat(high).isPresent();
            assertThat(high.get().name()).isEqualTo("high-patch");
            assertThat(high.get().action()).isEqualTo(ScigAction.PATCH_IMAGE);
            assertThat(high.get().remediation().targetImage())
                    .isEqualTo("docker.io/weaveworksdemos/front-end:0.3.12");

            Optional<ScigPolicyDocument.PolicyRule> low =
                    engine.evaluate(ScigSeverity.LOW, null);
            assertThat(low).isPresent();
            assertThat(low.get().action()).isEqualTo(ScigAction.FAIL_SAFE);
        }
    }

    @Nested
    class SyftParser {
        @Test
        void parsesNpmArtifactsAndSkipsUnknownTypes() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            ArrayNode artifacts = root.putArray("artifacts");

            ObjectNode npm = artifacts.addObject();
            npm.put("name", "lodash");
            npm.put("version", "4.17.15");
            npm.put("type", "npm");
            npm.put("purl", "pkg:npm/lodash@4.17.15");

            ObjectNode unknown = artifacts.addObject();
            unknown.put("name", "something");
            unknown.put("version", "1.0");
            unknown.put("type", "binary");

            ObjectNode dup = artifacts.addObject();
            dup.put("name", "lodash");
            dup.put("version", "4.17.15");
            dup.put("type", "npm");

            SyftSbomParser parser = new SyftSbomParser(mapper);
            List<SyftPackage> packages = parser.parsePackages(mapper.writeValueAsString(root));

            assertThat(packages).hasSize(1);
            assertThat(packages.getFirst().name()).isEqualTo("lodash");
            assertThat(packages.getFirst().osvEcosystem()).isEqualTo("npm");
        }
    }

    @Nested
    class OsvSeverity {
        @Test
        void mapsCvssScoresToSeverityBands() {
            assertThat(OsvClient.fromCvss(9.1)).isEqualTo(ScigSeverity.CRITICAL);
            assertThat(OsvClient.fromCvss(7.5)).isEqualTo(ScigSeverity.HIGH);
            assertThat(OsvClient.fromCvss(5.0)).isEqualTo(ScigSeverity.MEDIUM);
            assertThat(OsvClient.fromCvss(1.2)).isEqualTo(ScigSeverity.LOW);
        }
    }
}
