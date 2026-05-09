package com.kubiki.themis;

import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.constants.OntologyConstants;
import com.kubiki.themis.model.ExecutionStatus;
import com.kubiki.themis.model.Protocol;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class SimpleComponentsTest {

    @Test
    void testProtocolEnum() {
        assertEquals(3, Protocol.values().length);
        assertEquals(Protocol.REST, Protocol.valueOf("REST"));
        assertEquals(Protocol.SHELL, Protocol.valueOf("SHELL"));
        assertEquals(Protocol.GRPC, Protocol.valueOf("GRPC"));
    }

    @Test
    void testExecutionStatusEnum() {
        assertEquals(3, ExecutionStatus.values().length);
        assertEquals(ExecutionStatus.IN_PROGRESS, ExecutionStatus.valueOf("IN_PROGRESS"));
        assertEquals(ExecutionStatus.SUCCESS, ExecutionStatus.valueOf("SUCCESS"));
        assertEquals(ExecutionStatus.FAILED, ExecutionStatus.valueOf("FAILED"));
    }

    @Test
    void testOntologyConstants() throws Exception {
        assertNotNull(OntologyConstants.CLASS_PROMETHEUS_CONDITION);
        assertNotNull(OntologyConstants.PROP_HAS_COMPENSATION);
        
        // Test private constructor for coverage
        Constructor<OntologyConstants> constructor = OntologyConstants.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    @Test
    void testThemisProperties() {
        ThemisProperties.GraphDB graphdb = new ThemisProperties.GraphDB("url", "repo", 1000);
        assertEquals("url", graphdb.url());
        assertEquals("repo", graphdb.repositoryId());
        assertEquals(1000, graphdb.timeoutMs());

        ThemisProperties.Ontology ontology = new ThemisProperties.Ontology("ns");
        assertEquals("ns", ontology.moaNamespace());

        ThemisProperties.Prometheus prometheus = new ThemisProperties.Prometheus("purl");
        assertEquals("purl", prometheus.url());

        ThemisProperties props = new ThemisProperties(graphdb, ontology, prometheus);
        assertEquals(graphdb, props.graphdb());
        assertEquals(ontology, props.ontology());
        assertEquals(prometheus, props.prometheus());
    }
}
