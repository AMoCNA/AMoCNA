package com.kubiki.palamedes.scig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically runs the SCIG PoC loop (Redis SBOM → OSV → YAML policies).
 */
@Component
@ConditionalOnProperty(name = "palamedes.scig.enabled", havingValue = "true")
public class ScigScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScigScanScheduler.class);

    private final ScigPlanner scigPlanner;

    public ScigScanScheduler(ScigPlanner scigPlanner) {
        this.scigPlanner = scigPlanner;
    }

    @Scheduled(fixedDelayString = "${palamedes.scig.scan-interval-ms:3600000}")
    public void scheduledScan() {
        log.info("SCIG: starting scheduled scan");
        try {
            scigPlanner.scanAndDecide();
        } catch (Exception e) {
            log.error("SCIG: scheduled scan failed: {}", e.getMessage(), e);
        }
    }
}
