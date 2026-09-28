package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.AnomalyTarget;
import org.eclipse.rdf4j.model.IRI;
import java.util.List;

public interface ResourceDependencyService {
    void linkDependent(IRI dependent, IRI dependency);
    List<IRI> findDependents(IRI actionId);
    void clearResourceState(IRI resourceIri);
    boolean isDependentResource(IRI source, IRI target);
    List<AnomalyTarget> findAnomalies();
    List<AnomalyTarget> findRootCause(IRI startResource);
}
