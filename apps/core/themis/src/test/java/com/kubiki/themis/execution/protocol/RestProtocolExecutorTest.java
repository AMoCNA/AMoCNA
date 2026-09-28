package com.kubiki.themis.execution.protocol;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.common.model.Protocol;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.model.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestProtocolExecutor Tests")
class RestProtocolExecutorTest {

    private RestProtocolExecutor restProtocolExecutor;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Mock
    private ResponseEntity<Void> responseEntity;

    @Mock
    private ThemisProperties themisProperties;

    @Mock
    private ThemisProperties.Secret secret;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(restClientBuilder.requestFactory(org.mockito.ArgumentMatchers.any())).thenReturn(restClientBuilder);
        org.mockito.Mockito.lenient().when(restClientBuilder.build()).thenReturn(restClient);
        restProtocolExecutor = new RestProtocolExecutor(restClientBuilder, themisProperties);
    }

    @Test
    @DisplayName("Should return true only for REST protocol when checking support")
    void shouldSupportRestProtocol() {
        assertTrue(restProtocolExecutor.supports(Protocol.REST));
        assertFalse(restProtocolExecutor.supports(Protocol.SHELL));
    }

    @Test
    @DisplayName("Should execute GET request when GET method is specified")
    void shouldExecuteGetRequest() {
        // Given
        ActionMessage action = new ActionMessage(
                "action-1",
                Protocol.REST,
                "http://localhost:8080/health",
                "GET",
                null,
                null,
                10,
                true,
                3,
                200
        );

        when(restClient.method(HttpMethod.GET)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(responseEntity);
        when(responseEntity.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        ExecutionResult result = restProtocolExecutor.executeStateless(action);

        // Then
        assertTrue(result.success());
        assertEquals(200, result.observedStatusCode());
        assertEquals(ExecutionStatus.COMPLETED, result.status());
        verify(requestBodyUriSpec).uri("http://localhost:8080/health");
    }

    @Test
    @DisplayName("Should execute POST request with payload when POST method and body are provided")
    void shouldExecutePostRequestWithPayload() {
        // Given
        ActionMessage action = new ActionMessage(
                "action-2",
                Protocol.REST,
                "http://localhost:8080/scale",
                "POST",
                "{\"replicas\": 3}",
                null,
                10,
                true,
                3,
                200
        );

        when(restClient.method(HttpMethod.POST)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(responseEntity);
        when(responseEntity.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        ExecutionResult result = restProtocolExecutor.executeStateless(action);

        // Then
        assertTrue(result.success());
        verify(requestBodyUriSpec).uri("http://localhost:8080/scale");
        verify(requestBodySpec).body("{\"replicas\": 3}");
    }

    @Test
    @DisplayName("Should inject Bearer Token when authMechanism is BearerToken")
    void shouldInjectBearerToken() {
        // Given
        ActionMessage action = new ActionMessage(
                "action-auth",
                Protocol.REST,
                "http://localhost:8080/secure",
                "GET",
                null,
                "BearerToken",
                10,
                true,
                3,
                200
        );

        when(themisProperties.secret()).thenReturn(secret);
        when(secret.bearerToken()).thenReturn("my-token");

        when(restClient.method(HttpMethod.GET)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq("Authorization"), eq("Bearer my-token"))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(responseEntity);
        when(responseEntity.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        ExecutionResult result = restProtocolExecutor.executeStateless(action);

        // Then
        assertTrue(result.success());
        verify(requestBodySpec).header("Authorization", "Bearer my-token");
    }
}
