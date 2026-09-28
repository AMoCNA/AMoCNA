package com.kubiki.metis.knowledge;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.kubiki.metis.config.MetisProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphDbReadinessTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static GraphDbReadiness readinessForBaseUrl(String baseUrl) {
        return readinessForBaseUrl(baseUrl, 5_000, 100, 200);
    }

    private static GraphDbReadiness readinessForBaseUrl(
            String baseUrl, long maxWaitMs, long initialDelayMs, long maxDelayMs) {
        MetisProperties properties = new MetisProperties(
                new MetisProperties.GraphDB(baseUrl, "amocna", 5000),
                new MetisProperties.Ontology("http://example.org/cnee#"),
                new MetisProperties.Sensor(true, List.of(), 50, 500)
        );
        return GraphDbReadiness.forTest(properties, maxWaitMs, initialDelayMs, maxDelayMs);
    }

    @Test
    void awaitReady_succeedsWhenProtocolReturnsOk() {
        wireMock.stubFor(get(urlEqualTo("/protocol"))
                .willReturn(aResponse().withStatus(200)));

        GraphDbReadiness readiness = readinessForBaseUrl(wireMock.baseUrl());

        assertThatCode(readiness::awaitReady).doesNotThrowAnyException();
    }

    @Test
    void awaitReady_failsWhenProtocolNeverResponds() {
        wireMock.stubFor(get(urlEqualTo("/protocol"))
                .willReturn(aResponse().withStatus(503)));

        GraphDbReadiness readiness = readinessForBaseUrl(wireMock.baseUrl(), 2_000, 100, 200);

        assertThatThrownBy(readiness::awaitReady)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not become reachable");
    }
}
