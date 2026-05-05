package com.kubiki.themis.execution.impl;

import com.kubiki.themis.config.ThemisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class DeletePodExecutorTest {
    private DeletePodExecutor executor;
    private MockRestServiceServer mockServer;
    private String managementUrl = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        ThemisProperties.Kubernetes kubernetes = new ThemisProperties.Kubernetes(managementUrl);
        ThemisProperties properties = new ThemisProperties(null, kubernetes);
        
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        
        RestTemplateBuilder builder = new RestTemplateBuilder() {
            @Override
            public RestTemplate build() {
                return restTemplate;
            }
        };
        
        executor = new DeletePodExecutor(properties, builder);
    }

    @Test
    void shouldReturnFalseForInvalidTargetId() {
        assertFalse(executor.execute("invalid-id"));
    }

    @Test
    void shouldReturnTrueWhenDeleteSucceeds() {
        mockServer.expect(requestTo(managementUrl + "/kubernetes/management/pod/delete?namespace=ns&podName=pod"))
                .andRespond(withSuccess());
        
        assertTrue(executor.execute("ns/pod"));
        mockServer.verify();
    }

    @Test
    void shouldReturnFalseWhenDeleteFails() {
        mockServer.expect(requestTo(managementUrl + "/kubernetes/management/pod/delete?namespace=ns&podName=pod"))
                .andRespond(withServerError());
        
        assertFalse(executor.execute("ns/pod"));
        mockServer.verify();
    }

    @Test
    void shouldReturnCorrectActionType() {
        assertEquals("DeletePodAction", executor.getActionType());
    }
}
