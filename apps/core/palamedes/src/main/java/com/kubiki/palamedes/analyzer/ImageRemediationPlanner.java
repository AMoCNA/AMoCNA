package com.kubiki.palamedes.analyzer;

import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.common.vulnerability.UpgradePolicy;
import com.kubiki.common.vulnerability.VulnerabilityCatalog;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.knowledge.ActionHydrationService;
import com.kubiki.palamedes.knowledge.ActionRepository;
import com.kubiki.palamedes.knowledge.WorkloadDiscoveryService;
import com.kubiki.palamedes.model.ImageUpdateTarget;
import com.kubiki.palamedes.scig.ScigRedisSyncService;
import com.kubiki.palamedes.service.RemediationFilterService;
import com.kubiki.palamedes.utils.ActionUtils;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ImageRemediationPlanner {

    private static final Logger log = LoggerFactory.getLogger(ImageRemediationPlanner.class);
    private static final String IMAGE_UPDATE_INTENT = "ImageUpdateIntent";

    private final WorkloadDiscoveryService workloadDiscoveryService;
    private final ActionRepository actionRepository;
    private final ActionHydrationService actionHydrationService;
    private final ActionUtils utils;
    private final VulnerabilityCatalog vulnerabilityCatalog;
    private final PalamedesProperties properties;
    private final OntologyRegistry ontologyRegistry;
    private final RemediationFilterService filterService;
    private final ScigRedisSyncService scigRedisSyncService;

    public static String parseNamespaceFromDeploymentIri(IRI deploymentIri) {
        String local = URLDecoder.decode(deploymentIri.getLocalName(), StandardCharsets.UTF_8);
        int kindSep = local.indexOf('_');
        if (kindSep < 0) {
            return "default";
        }
        String rest = local.substring(kindSep + 1);
        int nsSep = rest.indexOf('_');
        if (nsSep < 0) {
            return "default";
        }
        return rest.substring(0, nsSep);
    }

    /**
     * Matches sensed {@code Image} entities in GraphDB against the CVE catalog and plans
     * {@code ImageUpdateIntent} workflows for affected deployments. No anomaly state is written to GraphDB.
     */
    public boolean scanCatalogAndPlan() {
        IRI intentIri = ontologyRegistry.actionsOntology(IMAGE_UPDATE_INTENT);
        if (!filterService.isIntentAllowed(intentIri.getLocalName())) {
            log.debug("ImageRemediationPlanner: Skipping scan - {} is filtered out.", intentIri.getLocalName());
            return false;
        }

        // Sync fresh CVE findings from SCIG Redis scan reports before running topology match
        try {
            scigRedisSyncService.syncFromRedis();
        } catch (Exception e) {
            log.warn("Failed to sync SCIG Redis vulnerabilities: {}", e.getMessage());
        }

        String vulnerablePairs = vulnerabilityCatalog.toSparqlValuesClause();
        if (vulnerablePairs.isBlank()) {
            log.debug("ImageRemediationPlanner: Catalog has no affected image versions to scan");
            return false;
        }

        log.info("ImageRemediationPlanner: Querying topology for catalog-vulnerable image versions...");
        UpgradePolicy upgradePolicy = UpgradePolicy.valueOf(
                properties.vulnerability().upgradePolicy().toUpperCase());

        List<ImageUpdateTarget> targets = workloadDiscoveryService.findVulnerableWorkloads(vulnerablePairs);
        if (targets.isEmpty()) {
            log.info("ImageRemediationPlanner: No workloads running catalog-vulnerable images");
            return false;
        }

        log.info("ImageRemediationPlanner: Found {} workloads running catalog-vulnerable images", targets.size());

        Map<IRI, ImageUpdateTarget> uniqueByDeployment = new LinkedHashMap<>();
        for (ImageUpdateTarget target : targets) {
            var cves = vulnerabilityCatalog.lookup(target.imageRepository(), target.currentVersion());
            if (!cves.isEmpty()) {
                log.warn("ImageRemediationPlanner: Detected vulnerable image {}:{} with {} active CVEs in catalog",
                        target.imageRepository(), target.currentVersion(), cves.size());
            }

            Optional<String> fixVersion = vulnerabilityCatalog.selectFixVersion(
                    target.imageRepository(), target.currentVersion(), upgradePolicy);
            if (fixVersion.isEmpty()) {
                log.warn("Catalog match for {}:{} but no fix under policy {}",
                        target.imageRepository(), target.currentVersion(), upgradePolicy);
                continue;
            }
            uniqueByDeployment.putIfAbsent(target.deploymentIri(), target.withTargetVersion(fixVersion.get()));
        }

        if (uniqueByDeployment.isEmpty()) {
            log.info("ImageRemediationPlanner: No valid upgrade path found for vulnerable workloads");
            return false;
        }

        for (ImageUpdateTarget target : uniqueByDeployment.values()) {
            String actionId = utils.generateActionId();
            log.info("ImageRemediationPlanner: Planning image update action {} for deployment {}/{} (current: {}:{}, target: {})",
                    actionId, target.namespace(), target.deploymentName(),
                    target.imageRepository(), target.currentVersion(), target.targetVersion());
            actionRepository.createActionWorkflow(target.deploymentIri(), intentIri, actionId);
            actionHydrationService.storeActionHydration(actionId, Map.of(
                    "containerName", target.containerName(),
                    "imageRepository", target.imageRepository(),
                    "targetVersion", target.targetVersion(),
                    "namespace", target.namespace()));
            log.info("Planned image update for deployment {}/{} (service: {}) -> {}:{}",
                    target.namespace(), target.deploymentName(),
                    target.serviceName() != null ? target.serviceName() : "n/a",
                    target.imageRepository(), target.targetVersion());
        }

        log.info("ImageRemediationPlanner: Completed vulnerability scan. Planned {} remediations.", uniqueByDeployment.size());
        return true;
    }
}
