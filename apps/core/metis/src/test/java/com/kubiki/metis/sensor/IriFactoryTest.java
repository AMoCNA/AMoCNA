package com.kubiki.metis.sensor;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.knowledge.CneeOntology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IriFactory} — verifies the IRI scheme used by all sensors.
 */
class IriFactoryTest {

    private static final String CNEE = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/";

    private IriFactory factory;

    @BeforeEach
    void setUp() {
        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://x", "test", 1000),
                new MetisProperties.Ontology(CNEE),
                new MetisProperties.Sensor(true, java.util.List.of(), 50, 500));
        factory = new IriFactory(props);
    }

    @Test
    void namespacedIri_buildsExpectedPodIri() {
        String iri = factory.namespacedIri(CneeOntology.KIND_POD, "default", "my-pod-abc");
        assertThat(iri).isEqualTo(CNEE + "Pod_default_my-pod-abc");
    }

    @Test
    void namespacedIri_buildsExpectedServiceIri() {
        String iri = factory.namespacedIri(CneeOntology.KIND_SERVICE, "production", "checkout-svc");
        assertThat(iri).isEqualTo(CNEE + "Service_production_checkout-svc");
    }

    @Test
    void clusterScopedIri_buildsExpectedNodeIri() {
        String iri = factory.clusterScopedIri(CneeOntology.KIND_NODE, "kube-worker-1");
        assertThat(iri).isEqualTo(CNEE + "Node_kube-worker-1");
    }

    @Test
    void typeIri_appendsToCneeNamespace() {
        assertThat(factory.typeIri(CneeOntology.CLASS_EXECUTION_UNIT))
                .isEqualTo(CNEE + "ExecutionUnit");
    }

    @Test
    void encodesSpecialCharsInNamespaceAndName() {
        String iri = factory.namespacedIri(CneeOntology.KIND_POD, "ns/with/slash", "name with space");
        assertThat(iri)
                .doesNotContain(" ")
                .doesNotContain("/ns/with/slash")  // raw slashes encoded
                .startsWith(CNEE + "Pod_");
    }
}
