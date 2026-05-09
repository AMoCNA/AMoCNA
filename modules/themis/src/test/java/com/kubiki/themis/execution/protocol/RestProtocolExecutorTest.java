package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestProtocolExecutor Unit Tests")
class RestProtocolExecutorTest {

    private RestProtocolExecutor restProtocolExecutor;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Mock
    private org.springframework.http.ResponseEntity<Void> responseEntity;

    private static Stream<Arguments> provideHttpMethods() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "http://localhost:8080/delete?ns=prod&pod=nginx-v1", null, "http://localhost:8080/delete?ns={ns}&pod={pod}", Map.of("ns", "prod", "pod", "nginx-v1")),
                Arguments.of(HttpMethod.POST, "http://localhost:8080/scale", "{\"replicas\": 3}", "http://localhost:8080/scale", Map.of("replicas", "3")),
                Arguments.of(HttpMethod.PUT, "http://localhost:8080/config", "{\"key\": \"value\"}", "http://localhost:8080/config", Map.of()),
                Arguments.of(HttpMethod.DELETE, "http://localhost:8080/resource/123", null, "http://localhost:8080/resource/{id}", Map.of("id", "123"))
        );
    }

    @BeforeEach
    void setUp() {
        when(restClientBuilder.build()).thenReturn(restClient);
        restProtocolExecutor = new RestProtocolExecutor(restClientBuilder);
        lenient().when(responseSpec.toBodilessEntity()).thenReturn(responseEntity);
    }

    @Test
    @DisplayName("Should support REST protocol and reject others")
    void shouldSupportRestProtocol() {
        assertAll(
                () -> assertTrue(restProtocolExecutor.supports(Protocol.REST), "Should support REST"),
                () -> assertFalse(restProtocolExecutor.supports(Protocol.SHELL), "Should NOT support SHELL")
        );
    }

    @ParameterizedTest(name = "Should execute {0} request to {1}")
    @MethodSource("provideHttpMethods")
    @DisplayName("Should execute standard HTTP methods")
    void shouldExecuteStandardHttpMethods(HttpMethod method, String expectedUrl, String payloadTemplate, String instruction, Map<String, String> data) {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "moa:TestAction",
                "TestAction",
                Protocol.REST,
                instruction,
                "res-1",
                data,
                method,
                payloadTemplate,
                java.util.List.of(),
                java.util.List.of()
        );
        UUID executionId = UUID.randomUUID();

        when(restClient.method(method)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        if (payloadTemplate != null) {
            when(requestBodyUriSpec.body(anyString())).thenReturn(requestBodyUriSpec);
        }
        when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);

        // When
        boolean result = restProtocolExecutor.execute(action, executionId);

        // Then
        assertAll(
                () -> assertTrue(result, "Execution should be successful"),
                () -> verify(requestBodyUriSpec).uri(expectedUrl)
        );
        if (payloadTemplate != null) {
            String expectedPayload = payloadTemplate;
            for (var entry : data.entrySet()) {
                expectedPayload = expectedPayload.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            verify(requestBodyUriSpec).body(expectedPayload);
        }
    }

    @Test
    @DisplayName("Should fail when action is not a SimpleAction")
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
    @DisplayName("Should fail when RestClient throws exception")
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
    @DisplayName("Should default to GET when method is null")
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

        assertAll(
                () -> assertTrue(restProtocolExecutor.execute(action, UUID.randomUUID())),
                () -> verify(restClient).method(HttpMethod.GET)
        );
    }

    @Test
    @DisplayName("Should handle empty payload by not calling body()")
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

        assertAll(
                () -> assertTrue(restProtocolExecutor.execute(action, UUID.randomUUID())),
                () -> verify(requestBodyUriSpec, never()).body(anyString())
        );
    }

    @Test
    @DisplayName("Should fail gracefully when instruction is null")
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

        // When instruction is null, url will be null.
        when(restClient.method(HttpMethod.GET)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri((String) null)).thenThrow(new IllegalArgumentException("URL is null"));

        assertFalse(restProtocolExecutor.execute(action, UUID.randomUUID()));
    }
}
