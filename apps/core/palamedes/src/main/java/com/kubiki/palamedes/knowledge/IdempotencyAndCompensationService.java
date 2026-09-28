package com.kubiki.palamedes.knowledge;

import org.eclipse.rdf4j.model.IRI;
import java.util.List;
import java.util.Map;

public interface IdempotencyAndCompensationService {
    Map<IRI, Boolean> fetchIdempotencyStates(List<IRI> actionIds);
    boolean isIdempotencyWindowOpen(IRI target, IRI intent);
    IRI findCompensation(IRI actionId);
}
