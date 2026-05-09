package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
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
                "moa:DeletePod_1",
                "DeletePodAction",
                Protocol.REST,
                "http://localhost:8080/delete?ns={ns}&pod={pod}",
                "cnee:pod-1",
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
                "moa:ScaleDeployment_1",
                "ScaleDeploymentAction",
                Protocol.REST,
                "http://localhost:8080/scale",
                "cnee:deploy-1",
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
                "moa:UpdateConfig_1",
                "UpdateConfigAction",
                Protocol.REST,
                "http://localhost:8080/config",
                "cnee:config-1",
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
                "moa:RemoveResource_1",
                "RemoveResourceAction",
                Protocol.REST,
                "http://localhost:8080/resource/{id}",
                "cnee:res-1",
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

    @Test
    void shouldFailWhenActionIsNotSimpleAction() {
        ActionData action = new ActionData.ComplexWorkflow(
                "moa:GenericAction",
                "GenericAction",
                java.util.Collections.emptyList(),
                java.util.Collections.emptyMap()
        );
        assertFalse(restProtocolExecutor.execute(action, UUID.randomUUID()));
    }

    @Test
    void shouldFailOnRestClientException() {
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "moa:Fail_1",
                "FailAction",
                Protocol.REST,
                "http://localhost:8080/fail",
                "res-1",
                Map.of(),
                HttpMethod.GET,
                null,
                java.util.List.of(),
                java.util.List.of()
        );

        when(restClient.method(HttpMethod.GET)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve()).thenThrow(new RuntimeException("Connection Refused"));

        assertFalse(restProtocolExecutor.execute(action, UUID.randomUUID()));
    }

    @Test
    void shouldDefaultToGetWhenMethodIsNull() {
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "moa:DefaultGet_1",
                "DefaultGetAction",
                Protocol.REST,
                "http://localhost:8080/get",
                "res-1",
                Map.of(),
                null,
                null,
                java.util.List.of(),
                java.util.List.of()
        );

        when(restClient.method(HttpMethod.GET)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);

        assertTrue(restProtocolExecutor.execute(action, UUID.randomUUID()));
        verify(restClient).method(HttpMethod.GET);
    }

    @Test
    void shouldHandleEmptyPayload() {
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "moa:EmptyPayload_1",
                "EmptyPayloadAction",
                Protocol.REST,
                "http://localhost:8080/post",
                "res-1",
                Map.of(),
                HttpMethod.POST,
                "",
                java.util.List.of(),
                java.util.List.of()
        );

        when(restClient.method(HttpMethod.POST)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);

        assertTrue(restProtocolExecutor.execute(action, UUID.randomUUID()));
        verify(requestBodyUriSpec, never()).body(anyString());
    }

    @Test
    void shouldHandleNullInstruction() {
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "moa:NullInstruction_1",
                "NullInstructionAction",
                Protocol.REST,
                null,
                "res-1",
                Map.of(),
                HttpMethod.GET,
                null,
                java.util.List.of(),
                java.util.List.of()
        );

        // When instruction is null, url will be null. RestClient might throw exception.
        when(restClient.method(HttpMethod.GET)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri((String) null)).thenThrow(new IllegalArgumentException("URL is null"));

        assertFalse(restProtocolExecutor.execute(action, UUID.randomUUID()));
    }
}
