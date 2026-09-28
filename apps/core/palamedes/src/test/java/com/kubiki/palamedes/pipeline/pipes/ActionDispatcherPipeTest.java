package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.palamedes.pipeline.WorkflowContext;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionDispatcherPipeTest {

    private static final SimpleValueFactory VF = SimpleValueFactory.getInstance();

    @Test
    void hydrationFromContext_excludesPipelineKeysAndIncludesPlannerPayload() {
        IRI actionIri = VF.createIRI("http://example.org/moam#action-1");
        WorkflowContext context = new WorkflowContext(actionIri, null);
        context.metadata().put("currentState", "State_Validated");
        context.metadata().put("resourceName", "front-end");
        context.metadata().put("containerName", "front-end");
        context.metadata().put("imageRepository", "weaveworksdemos/frontend");
        context.metadata().put("targetVersion", "0.3.1");
        context.metadata().put("namespace", "sock-shop");

        Map<String, String> hydration = ActionDispatcherPipe.hydrationFromContext(context);

        assertThat(hydration).doesNotContainKey("currentState");
        assertThat(hydration).containsEntry("resourceName", "front-end");
        assertThat(hydration).containsEntry("targetVersion", "0.3.1");
    }
}
