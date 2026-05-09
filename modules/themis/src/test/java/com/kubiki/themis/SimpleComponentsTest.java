package com.kubiki.themis;

import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.constants.OntologyConstants;
import com.kubiki.themis.model.ExecutionStatus;
import com.kubiki.themis.model.Protocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Simple Components Tests")
class SimpleComponentsTest {

    @ParameterizedTest
    @EnumSource(Protocol.class)
    @DisplayName("Protocol enum values should be valid")
    void testProtocolEnum(Protocol protocol) {
        assertAll(
                () -> assertEquals(3, Protocol.values().length),
                () -> assertEquals(protocol, Protocol.valueOf(protocol.name()))
        );
    }

    @ParameterizedTest
    @EnumSource(ExecutionStatus.class)
    @DisplayName("ExecutionStatus enum values should be valid")
    void testExecutionStatusEnum(ExecutionStatus status) {
        assertAll(
                () -> assertEquals(3, ExecutionStatus.values().length),
                () -> assertEquals(status, ExecutionStatus.valueOf(status.name()))
        );
    }

    @Test
    @DisplayName("OntologyConstants should have valid values and private constructor")
    void testOntologyConstants() throws Exception {
        assertAll(
                () -> assertNotNull(OntologyConstants.CLASS_PROMETHEUS_CONDITION),
                () -> assertNotNull(OntologyConstants.PROP_HAS_COMPENSATION),
                () -> {
                    // Test private constructor for coverage
                    Constructor<OntologyConstants> constructor = OntologyConstants.class.getDeclaredConstructor();
                    assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
                    constructor.setAccessible(true);
                    assertNotNull(constructor.newInstance());
                }
        );
    }

    @Test
    @DisplayName("ThemisProperties and its nested records should work correctly")
    void testThemisProperties() {
        assertAll(
                () -> {
                    ThemisProperties.GraphDB graphdb = new ThemisProperties.GraphDB("url", "repo", 1000);
                    assertAll(
                            () -> assertEquals("url", graphdb.url()),
                            () -> assertEquals("repo", graphdb.repositoryId()),
                            () -> assertEquals(1000, graphdb.timeoutMs()),
                            () -> assertNotNull(graphdb.toString()),
                            () -> assertEquals(new ThemisProperties.GraphDB("url", "repo", 1000), graphdb),
                            () -> assertEquals(graphdb.hashCode(), new ThemisProperties.GraphDB("url", "repo", 1000).hashCode())
                    );
                },
                () -> {
                    ThemisProperties.Ontology ontology = new ThemisProperties.Ontology("ns");
                    assertAll(
                            () -> assertEquals("ns", ontology.moamNamespace()),
                            () -> assertNotNull(ontology.toString()),
                            () -> assertEquals(new ThemisProperties.Ontology("ns"), ontology),
                            () -> assertEquals(ontology.hashCode(), new ThemisProperties.Ontology("ns").hashCode())
                    );
                },
                () -> {
                    ThemisProperties.Prometheus prometheus = new ThemisProperties.Prometheus("purl");
                    assertAll(
                            () -> assertEquals("purl", prometheus.url()),
                            () -> assertNotNull(prometheus.toString()),
                            () -> assertEquals(new ThemisProperties.Prometheus("purl"), prometheus),
                            () -> assertEquals(prometheus.hashCode(), new ThemisProperties.Prometheus("purl").hashCode())
                    );
                },
                () -> {
                    ThemisProperties.GraphDB graphdb = new ThemisProperties.GraphDB("url", "repo", 1000);
                    ThemisProperties.Ontology ontology = new ThemisProperties.Ontology("ns");
                    ThemisProperties.Prometheus prometheus = new ThemisProperties.Prometheus("purl");
                    ThemisProperties props = new ThemisProperties(graphdb, ontology, prometheus);
                    assertAll(
                            () -> assertEquals(graphdb, props.graphdb()),
                            () -> assertEquals(ontology, props.ontology()),
                            () -> assertEquals(prometheus, props.prometheus()),
                            () -> assertNotNull(props.toString()),
                            () -> assertEquals(props, new ThemisProperties(graphdb, ontology, prometheus)),
                            () -> assertEquals(props.hashCode(), new ThemisProperties(graphdb, ontology, prometheus).hashCode())
                    );
                }
        );
    }
}
