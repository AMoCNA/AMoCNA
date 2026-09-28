package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.IntentMetadata;
import org.eclipse.rdf4j.model.IRI;
import java.util.List;
import java.util.Map;

public interface IntentMetadataService {
    Map<IRI, IntentMetadata> fetchIntentMetadata(List<IRI> intentIds);
}
