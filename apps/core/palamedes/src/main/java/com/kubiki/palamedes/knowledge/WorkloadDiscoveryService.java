package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.ImageUpdateTarget;
import com.kubiki.palamedes.model.RegistryAuthTarget;
import org.eclipse.rdf4j.model.IRI;
import java.util.List;
import java.util.Optional;

public interface WorkloadDiscoveryService {
    Optional<ImageUpdateTarget> findWorkloadDetails(IRI workloadIri);
    List<ImageUpdateTarget> findVulnerableWorkloads(String vulnerablePairs);
    List<RegistryAuthTarget> findRegistryAuthFailures();
}
