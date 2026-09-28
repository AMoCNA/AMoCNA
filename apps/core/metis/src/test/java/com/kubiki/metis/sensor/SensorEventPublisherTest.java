package com.kubiki.metis.sensor;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.ingestion.SensorEventProcessor;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.ingestion.model.ProcessResult;
import com.kubiki.metis.notification.PalamedesNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorEventPublisherTest {

    @Mock
    private SensorEventProcessor processor;

    @Mock
    private PalamedesNotifier notifier;

    private SensorEventPublisher publisher;

    private static SensorEvent sampleEvent() {
        return SensorEvent.newBuilder()
                .setEntityDiscovered(EntityDiscoveredEvent.newBuilder()
                        .setResourceIri("http://example.org/cnee#Pod_test")
                        .setOntologyType("http://example.org/cnee#Pod")
                        .setResourceId("test")
                        .setResourceName("test")
                        .build())
                .build();
    }

    @BeforeEach
    void setUp() {
        MetisProperties properties = new MetisProperties(
                new MetisProperties.GraphDB("http://localhost:7200", "amocna", 5000),
                new MetisProperties.Ontology("http://example.org/cnee#"),
                new MetisProperties.Sensor(true, List.of(), 1, 60_000)
        );
        publisher = new SensorEventPublisher(processor, notifier, properties);
    }

    @Test
    void flush_graphDbFailed_requeuesEventsForRetry() {
        SensorEvent event = sampleEvent();
        ProcessResult graphDbFailure = new ProcessResult(
                0, 1, List.of("Knowledge base unavailable"), null, true);
        ProcessResult success = new ProcessResult(
                1, 0, List.of(),
                HandlerResult.success("http://example.org/pod", "Pod", HandlerResult.CREATED, "update"),
                false);

        when(processor.processBatch(any(), anyString()))
                .thenReturn(graphDbFailure)
                .thenReturn(success);

        publisher.publish(event);
        ReflectionTestUtils.invokeMethod(publisher, "flush");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SensorEvent>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(processor, times(2)).processBatch(batchCaptor.capture(), anyString());

        List<SensorEvent> firstBatch = batchCaptor.getAllValues().get(0);
        List<SensorEvent> secondBatch = batchCaptor.getAllValues().get(1);
        assertThat(firstBatch).hasSize(1);
        assertThat(secondBatch).hasSize(1);
        assertThat(firstBatch.get(0).getEntityDiscovered().getResourceIri())
                .isEqualTo(secondBatch.get(0).getEntityDiscovered().getResourceIri());
    }
}
