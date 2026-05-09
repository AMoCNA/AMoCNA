package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestProtocolExecutorSliceTest {

    private RestProtocolExecutor executor;
    private MockRestServiceServer server;
    private static final SimpleValueFactory VF = SimpleValueFactory.getInstance();

    @BeforeEach
    void setup() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        executor = new RestProtocolExecutor(restClientBuilder);
    }

    @Test
    void testRestExecution() {
        String testUrl = "http://api.test/remediate";
        server.expect(requestTo(testUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        ActionData.SimpleAction action = new ActionData.SimpleAction(
                VF.createIRI("http://moa#test-id"),
                "RemediateAction",
                Protocol.REST,
                testUrl,
                VF.createIRI("http://target-resource"),
                Map.of(),
                HttpMethod.POST,
                "{}",
                Collections.emptyList(),
                Collections.emptyList()
        );

        boolean result = executor.execute(action, UUID.randomUUID());

        assertTrue(result);
        server.verify();
    }
}
