package com.kubiki.palamedes.knowledge;

import org.eclipse.rdf4j.model.IRI;
import java.util.List;
import java.util.Map;

public interface ActionHydrationService {
    void storeActionHydration(String actionId, Map<String, String> parameters);
    Map<IRI, Map<String, String>> fetchActionHydrations(List<IRI> actionIds);
}
