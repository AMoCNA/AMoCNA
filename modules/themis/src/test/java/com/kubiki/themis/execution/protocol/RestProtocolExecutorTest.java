package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.model.ActionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestProtocolExecutorTest {

    private RestProtocolExecutor restProtocolExecutor;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        restProtocolExecutor = new RestProtocolExecutor(restTemplateBuilder);
    }

    @Test
    void shouldHydrateAndExecuteRestCall() {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "moa:DeletePod_1",
                "DeletePodAction",
                "REST",
                "http://localhost:8080/delete?ns={ns}&pod={pod}",
                "cnee:pod-1",
                Map.of("ns", "prod", "pod", "nginx-v1")
        );
        UUID executionId = UUID.randomUUID();

        // When
        boolean result = restProtocolExecutor.execute(action, executionId);

        // Then
        assertTrue(result);
        verify(restTemplate).getForObject("http://localhost:8080/delete?ns=prod&pod=nginx-v1", String.class);
    }
}
