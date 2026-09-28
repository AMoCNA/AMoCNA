package com.kubiki.palamedes.analyzer.hydration;

import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.common.vulnerability.UpgradePolicy;
import com.kubiki.common.vulnerability.VulnerabilityCatalog;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.AnomalyTarget;
import com.kubiki.palamedes.model.ImageUpdateTarget;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Specialized hydrator for ImageUpdateIntent.
 */
@Order(10)
@Component
@RequiredArgsConstructor
public class ImageUpdateActionHydrator extends BaseActionHydrator {
    private static final Logger log = LoggerFactory.getLogger(ImageUpdateActionHydrator.class);
    private static final String IMAGE_UPDATE_INTENT = "ImageUpdateIntent";

    private final GraphDBGateway gateway;
    private final VulnerabilityCatalog vulnerabilityCatalog;
    private final PalamedesProperties palamedesProperties;
    private final OntologyRegistry ontologyRegistry;

    @Override
    public boolean supports(IRI intentIri) {
        return intentIri.equals(ontologyRegistry.actionsOntology(IMAGE_UPDATE_INTENT));
    }

    @Override
    public Map<String, String> hydrate(AnomalyTarget target) {
        Map<String, String> params = getBaseParameters(target);

        UpgradePolicy upgradePolicy = UpgradePolicy.valueOf(
                palamedesProperties.vulnerability().upgradePolicy().toUpperCase());

        Optional<ImageUpdateTarget> details = gateway.findWorkloadDetails(target.resourceIri());
        details.ifPresent(d -> {
            params.put("containerName", d.containerName());
            params.put("imageRepository", d.imageRepository());
            log.info("ImageUpdateActionHydrator: Hydrating details for container {} in repository {}",
                    d.containerName(), d.imageRepository());

            vulnerabilityCatalog.selectFixVersion(d.imageRepository(), d.currentVersion(), upgradePolicy)
                    .ifPresent(v -> {
                        params.put("targetVersion", v);
                        log.info("ImageUpdateActionHydrator: Selected fix version {} for repository {} (current version: {})",
                                v, d.imageRepository(), d.currentVersion());
                    });
        });

        return params;
    }
}
