package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.model.ActionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RestProtocolExecutorTest {

    private RestProtocolExecutor restProtocolExecutor;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Mock
    private org.springframework.http.ResponseEntity<Void> responseEntity;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(restClientBuilder.build()).thenReturn(restClient);
        restProtocolExecutor = new RestProtocolExecutor(restClientBuilder);
        when(responseSpec.toBodilessEntity()).thenReturn(responseEntity);
    }

    @Test
    void shouldSupportRestProtocol() {
        assertTrue(restProtocolExecutor.supports("REST"));
        assertFalse(restProtocolExecutor.supports("SHELL"));
    }

    @Test
    void shouldExecuteGetRequest() {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "moa:DeletePod_1",
                "DeletePodAction",
                "REST",
                "http://localhost:8080/delete?ns={ns}&pod={pod}",
                "cnee:pod-1",
                Map.of("ns", "prod", "pod", "nginx-v1"),
                "GET",
                null,
                java.util.List.of(),
                java.util.List.of()
        );
        UUID executionId = UUID.randomUUID();

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);

        // When
        boolean result = restProtocolExecutor.execute(action, executionId);

        // Then
        assertTrue(result);
        verify(requestHeadersUriSpec).uri("http://localhost:8080/delete?ns=prod&pod=nginx-v1");
    }

    @Test
    void shouldExecutePostRequestWithPayload() {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "moa:ScaleDeployment_1",
                "ScaleDeploymentAction",
                "REST",
                "http://localhost:8080/scale",
                "cnee:deploy-1",
                Map.of("replicas", "3"),
                "POST",
                "{\"replicas\": {replicas}}",
                java.util.List.of(),
                java.util.List.of()
        );
        UUID executionId = UUID.randomUUID();

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.body(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);

        // When
        boolean result = restProtocolExecutor.execute(action, executionId);

        // Then
        assertTrue(result);
        verify(requestBodyUriSpec).uri("http://localhost:8080/scale");
        verify(requestBodyUriSpec).body("{\"replicas\": 3}");
    }

    @Test
    void shouldExecutePutRequestWithPayload() {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "moa:UpdateConfig_1",
                "UpdateConfigAction",
                "REST",
                "http://localhost:8080/config",
                "cnee:config-1",
                Map.of(),
                "PUT",
                "{\"key\": \"value\"}",
                java.util.List.of(),
                java.util.List.of()
        );
        UUID executionId = UUID.randomUUID();

        when(restClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.body(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);

        // When
        boolean result = restProtocolExecutor.execute(action, executionId);

        // Then
        assertTrue(result);
        verify(requestBodyUriSpec).uri("http://localhost:8080/config");
        verify(requestBodyUriSpec).body("{\"key\": \"value\"}");
    }

    @Test
    void shouldExecuteDeleteRequest() {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "moa:RemoveResource_1",
                "RemoveResourceAction",
                "REST",
                "http://localhost:8080/resource/{id}",
                "cnee:res-1",
                Map.of("id", "123"),
                "DELETE",
                null,
                java.util.List.of(),
                java.util.List.of()
        );
        UUID executionId = UUID.randomUUID();

        when(restClient.delete()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);

        // When
        boolean result = restProtocolExecutor.execute(action, executionId);

        // Then
        assertTrue(result);
        verify(requestHeadersUriSpec).uri("http://localhost:8080/resource/123");
    }
}
