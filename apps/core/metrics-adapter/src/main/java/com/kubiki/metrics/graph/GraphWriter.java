package com.kubiki.metrics.graph;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.common.logging.LogLoopStep;
import com.kubiki.common.logging.LoopPhase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.manager.RemoteRepositoryManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphWriter {
    private final SparqlRepository sparqlRepository;
    private final AmocnaCommonProperties properties;
    private RemoteRepositoryManager manager;
    private Repository repository;


    @PostConstruct
    public void init() {
        String graphDbUrl = properties.graphdb().url();
        String repositoryId = properties.graphdb().repositoryId();
        try {
            manager = new RemoteRepositoryManager(graphDbUrl);
            manager.init();
            this.repository = manager.getRepository(repositoryId);
            log.info("Initialized GraphDB repository: {} at {}", repositoryId, graphDbUrl);
        } catch (Exception e) {
            log.error("Failed to initialize GraphDB repository manager at {}", graphDbUrl, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (manager != null) {
            log.info("Shutting down GraphDB repository manager");
            manager.shutDown();
        }
    }

    @LogLoopStep(
            phase = LoopPhase.MONITOR,
            step = "Anomaly Instantiated",
            resource = "#targetResourceIri",
            details = "'anomalyType=' + #anomalyTypeIri",
            debugOnly = true
    )
    public void instantiateAnomaly(String targetResourceIri, String anomalyTypeIri) {
        if (repository == null) {
            log.error("Cannot instantiate anomaly: repository is not initialized");
            return;
        }

        String anomalyIri = anomalyTypeIri + "_" + UUID.randomUUID();
        String timestamp = Instant.now().toString();

        try (RepositoryConnection conn = repository.getConnection()) {
            String updateQuery = sparqlRepository.instantiateAnomaly(targetResourceIri, anomalyIri, anomalyTypeIri, timestamp);
            conn.prepareUpdate(updateQuery).execute();
        } catch (Exception e) {
            log.error("Failed to instantiate anomaly in GraphDB for resource {}", targetResourceIri, e);
            if (e instanceof InterruptedException || (e.getCause() != null && e.getCause() instanceof InterruptedException)) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @LogLoopStep(
            phase = LoopPhase.MONITOR,
            step = "Anomaly Cleared",
            resource = "#targetResourceIri",
            details = "'anomalyType=' + #anomalyTypeIri",
            debugOnly = true
    )
    public void clearAnomalies(String targetResourceIri, String anomalyTypeIri) {
        if (repository == null) {
            log.error("Cannot clear anomalies: repository is not initialized");
            return;
        }

        try (RepositoryConnection conn = repository.getConnection()) {
            String updateQuery = sparqlRepository.clearAnomalies(targetResourceIri, anomalyTypeIri);
            conn.prepareUpdate(updateQuery).execute();
        } catch (Exception e) {
            log.error("Failed to clear anomalies of type {} in GraphDB for resource {}", anomalyTypeIri, targetResourceIri, e);
            if (e instanceof InterruptedException || (e.getCause() != null && e.getCause() instanceof InterruptedException)) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
