package com.kubiki.palamedes.analyzer;

import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.common.vulnerability.VulnerabilityCatalog;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ImageUpdateTarget;
import com.kubiki.palamedes.scig.ScigRedisSyncService;
import com.kubiki.palamedes.service.RemediationFilterService;
import com.kubiki.palamedes.utils.ActionUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageRemediationPlannerTest {

    private static final SimpleValueFactory VF = SimpleValueFactory.getInstance();

    @Mock
    private GraphDBGateway gateway;
    @Mock
    private ActionUtils utils;
    @Mock
    private VulnerabilityCatalog vulnerabilityCatalog;
    @Mock
    private PalamedesProperties properties;
    @Mock
    private OntologyRegistry ontologyRegistry;
    @Mock
    private RemediationFilterService filterService;
    @Mock
    private ScigRedisSyncService scigRedisSyncService;
    @Mock
    private PalamedesProperties.Vulnerability vulnerabilityProps;

    private ImageRemediationPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new ImageRemediationPlanner(
                gateway, gateway, gateway, utils, vulnerabilityCatalog, properties, ontologyRegistry, filterService, scigRedisSyncService);
    }

    @Test
    void parseNamespaceFromDeploymentIri_extractsNamespace() {
        IRI deployment = VF.createIRI(
                "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#Deployment_sock-shop_front-end");
        assertThat(ImageRemediationPlanner.parseNamespaceFromDeploymentIri(deployment))
                .isEqualTo("sock-shop");
    }

    @Test
    void scanCatalogAndPlan_queriesGraphOnceAndPlansRemediation() {
        IRI intentIri = VF.createIRI("http://example.org/ImageUpdateIntent");
        IRI deploymentIri = VF.createIRI("http://example.org/Deployment_sock-shop_front-end");
        ImageUpdateTarget target = new ImageUpdateTarget(
                deploymentIri, "front-end", "sock-shop", "front-end",
                "weaveworksdemos/front-end", "0.3.0", null, null, null);

        when(ontologyRegistry.actionsOntology("ImageUpdateIntent")).thenReturn(intentIri);
        when(filterService.isIntentAllowed("ImageUpdateIntent")).thenReturn(true);
        when(vulnerabilityCatalog.toSparqlValuesClause())
                .thenReturn("(\"weaveworksdemos/front-end\" \"0.3.0\")");
        when(properties.vulnerability()).thenReturn(vulnerabilityProps);
        when(vulnerabilityProps.upgradePolicy()).thenReturn("PATCH");
        when(gateway.findVulnerableWorkloads(anyString())).thenReturn(List.of(target));
        when(vulnerabilityCatalog.lookup("weaveworksdemos/front-end", "0.3.0"))
                .thenReturn(List.of());
        when(vulnerabilityCatalog.selectFixVersion("weaveworksdemos/front-end", "0.3.0",
                com.kubiki.common.vulnerability.UpgradePolicy.PATCH))
                .thenReturn(java.util.Optional.of("0.3.12"));
        when(utils.generateActionId()).thenReturn("action-1");

        boolean planned = planner.scanCatalogAndPlan();

        assertThat(planned).isTrue();
        verify(scigRedisSyncService).syncFromRedis();
        verify(gateway).findVulnerableWorkloads("(\"weaveworksdemos/front-end\" \"0.3.0\")");
        verify(gateway).createActionWorkflow(deploymentIri, intentIri, "action-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> hydrationCaptor = ArgumentCaptor.forClass(Map.class);
        verify(gateway).storeActionHydration(eq("action-1"), hydrationCaptor.capture());
        assertThat(hydrationCaptor.getValue()).containsEntry("targetVersion", "0.3.12");
    }

    @Test
    void scanCatalogAndPlan_skipsWhenCatalogHasNoPairs() {
        when(ontologyRegistry.actionsOntology("ImageUpdateIntent"))
                .thenReturn(VF.createIRI("http://example.org/ImageUpdateIntent"));
        when(filterService.isIntentAllowed("ImageUpdateIntent")).thenReturn(true);
        when(vulnerabilityCatalog.toSparqlValuesClause()).thenReturn("");

        assertThat(planner.scanCatalogAndPlan()).isFalse();
        verify(scigRedisSyncService).syncFromRedis();
        verify(gateway, never()).findVulnerableWorkloads(anyString());
    }
}
