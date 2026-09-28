package com.kubiki.palamedes.pipeline;

import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActiveActionSummary;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MapePipelineTest {
    @Mock
    private MapePipe pipe1;
    @Mock
    private MapePipe pipe2;
    @Mock
    private GraphDBGateway gateway;
    @Mock
    private PalamedesProperties properties;
    @Mock
    private PalamedesProperties.Engine engine;

    @Test
    void shouldExecutePipeline() {
        when(properties.engine()).thenReturn(engine);
        when(engine.batchSize()).thenReturn(50);

        MapePipeline pipeline = new MapePipeline(List.of(pipe1, pipe2), gateway, properties);

        IRI actionId = SimpleValueFactory.getInstance().createIRI("http://test/action1");
        var action = new ActiveActionSummary(
                actionId, null, "resource1", "State_Initial");

        when(gateway.findActiveActions()).thenReturn(List.of(action));
        ActionData.SimpleAction mockActionData = mock(ActionData.SimpleAction.class);
        when(gateway.fetchActionStructures(anyList())).thenReturn(Map.of(actionId, mockActionData));
        when(pipe1.process(any())).thenReturn(true);
        when(pipe2.process(any())).thenReturn(true);

        pipeline.run();

        verify(pipe1).process(any());
        verify(pipe2).process(any());
    }
}
