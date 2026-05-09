package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
        when(restClientBuilder.build()).thenReturn(restClient);
        restProtocolExecutor = new RestProtocolExecutor(restClientBuilder);
    }

    @Test
    void shouldSupportRestProtocol() {
        assertTrue(restProtocolExecutor.supports(Protocol.REST));
        assertFalse(restProtocolExecutor.supports(Protocol.SHELL));
    }

    @Test
    void shouldExecuteGetRequest() {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                SimpleValueFactory.getInstance().createIRI("http://moa#DeletePod_1"),
                "DeletePodAction",
                Protocol.REST,
                "http://localhost:8080/delete?ns={ns}&pod={pod}",
                SimpleValueFactory.getInstance().createIRI("http://cnee#pod-1"),
                Map.of("ns", "prod", "pod", "nginx-v1"),
                HttpMethod.GET,
                null,
                java.util.List.of(),
                java.util.List.of()
        );
        UUID executionId = UUID.randomUUID();

        when(restClient.method(HttpMethod.GET)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);

        // When
        boolean result = restProtocolExecutor.execute(action, executionId);

        // Then
        assertTrue(result);
        verify(requestBodyUriSpec).uri("http://localhost:8080/delete?ns=prod&pod=nginx-v1");
    }

    @Test
    void shouldExecutePostRequestWithPayload() {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                SimpleValueFactory.getInstance().createIRI("http://moa#ScaleDeployment_1"),
                "ScaleDeploymentAction",
                Protocol.REST,
                "http://localhost:8080/scale",
                SimpleValueFactory.getInstance().createIRI("http://cnee#deploy-1"),
                Map.of("replicas", "3"),
                HttpMethod.POST,
                "{\"replicas\": {replicas}}",
                java.util.List.of(),
                java.util.List.of()
        );
        UUID executionId = UUID.randomUUID();

        when(restClient.method(HttpMethod.POST)).thenReturn(requestBodyUriSpec);
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
                SimpleValueFactory.getInstance().createIRI("http://moa#UpdateConfig_1"),
                "UpdateConfigAction",
                Protocol.REST,
                "http://localhost:8080/config",
                SimpleValueFactory.getInstance().createIRI("http://cnee#config-1"),
                Map.of(),
                HttpMethod.PUT,
                "{\"key\": \"value\"}",
                java.util.List.of(),
                java.util.List.of()
        );
        UUID executionId = UUID.randomUUID();

        when(restClient.method(HttpMethod.PUT)).thenReturn(requestBodyUriSpec);
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
                SimpleValueFactory.getInstance().createIRI("http://moa#RemoveResource_1"),
                "RemoveResourceAction",
                Protocol.REST,
                "http://localhost:8080/resource/{id}",
                SimpleValueFactory.getInstance().createIRI("http://cnee#res-1"),
                Map.of("id", "123"),
                HttpMethod.DELETE,
                null,
                java.util.List.of(),
                java.util.List.of()
        );
        UUID executionId = UUID.randomUUID();

        when(restClient.method(HttpMethod.DELETE)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);

        // When
        boolean result = restProtocolExecutor.execute(action, executionId);

        // Then
        assertTrue(result);
        verify(requestBodyUriSpec).uri("http://localhost:8080/resource/123");
    }
}
