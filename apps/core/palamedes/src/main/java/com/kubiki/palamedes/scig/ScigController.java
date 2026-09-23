package com.kubiki.palamedes.scig;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manual trigger for SCIG PoC scans (useful before Themis wiring).
 */
@RestController
@RequestMapping("/api/scig")
@ConditionalOnProperty(name = "palamedes.scig.enabled", havingValue = "true")
public class ScigController {

    private final ScigPlanner scigPlanner;
    private final SbomRedisClient sbomRedisClient;

    public ScigController(ScigPlanner scigPlanner, SbomRedisClient sbomRedisClient) {
        this.scigPlanner = scigPlanner;
        this.sbomRedisClient = sbomRedisClient;
    }

    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> scanAll() {
        boolean actionable = scigPlanner.scanAndDecide();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("imagesInRedis", sbomRedisClient.listScannedImages().size());
        body.put("actionable", actionable);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/scan/image")
    public ResponseEntity<ScigDecision> scanImage(
            @RequestParam String repository,
            @RequestParam String tag,
            @RequestParam(required = false) String namespace) {
        return ResponseEntity.ok(scigPlanner.evaluateImage(repository, tag, namespace));
    }
}
