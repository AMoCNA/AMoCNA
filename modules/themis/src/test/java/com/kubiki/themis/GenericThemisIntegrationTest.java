package com.kubiki.themis;

import com.kubiki.themis.grpc.ActionServiceGrpc;
import com.kubiki.themis.grpc.ActionRequest;
import com.kubiki.themis.grpc.ExecutionStatus;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Iterator;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
public class GenericThemisIntegrationTest {

    static WireMockServer wireMockServer = new WireMockServer(0);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("themis.graphdb.url", () -> "http://localhost:7200");
        registry.add("themis.graphdb.repositoryId", () -> "amocna");
    }

    @BeforeAll
    static void setup() throws Exception {
        wireMockServer.start();
        initializeGraphDB();
    }

    static void initializeGraphDB() throws Exception {
        String url = "http://localhost:7200";
        
        // Create repository via REST API
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        String repoConfig = """
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>.
                @prefix rep: <http://www.openrdf.org/config/repository#>.
                @prefix sr: <http://www.openrdf.org/config/repository/sail#>.
                @prefix sail: <http://www.openrdf.org/config/sail#>.
                @prefix graphdb: <http://www.ontotext.com/config/graphdb#>.
                [] a rep:Repository ;
                    rep:repositoryID "amocna" ;
                    rdfs:label "AMoCNA Repository" ;
                    rep:repositoryImpl [
                        rep:repositoryType "graphdb:SailRepository" ;
                        sr:sailImpl [
                            sail:sailType "graphdb:Sail" ;
                            graphdb:ruleset "owl-horizon" ;
                            graphdb:repository-type "file-repository" ;
                        ]
                    ].
                """;

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url + "/rest/repositories"))
                .header("Content-Type", "text/turtle")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(repoConfig))
                .build();
        
        // Retry until GraphDB is ready
        int retries = 5;
        while (retries > 0) {
            try {
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 400 || response.statusCode() == 409) { // 409 means already exists
                    break;
                }
            } catch (Exception e) {
                Thread.sleep(2000);
            }
            retries--;
        }

        // Wait a bit for repo to be fully initialized
        Thread.sleep(2000);
    }

    private void loadTestData() throws Exception {
        String moaNs = "http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#";
        String testAction = "@prefix moa: <" + moaNs + "> .\n" +
                "@prefix : <http://example.org/test#> .\n" +
                ":res1 a moa:Resource .\n" +
                ":action1 a moa:RestartAction ;\n" +
                "    a moa:AutonomicAction ;\n" +
                "    moa:targetsEntity :res1 ;\n" +
                "    moa:executionProtocol \"REST\" ;\n" +
                "    moa:executionInstruction \"http://localhost:" + wireMockServer.port() + "/remediate\" ;\n" +
                "    moa:httpMethod \"POST\" .\n";

        org.eclipse.rdf4j.repository.Repository repo = new org.eclipse.rdf4j.repository.http.HTTPRepository(
                "http://localhost:7200", "amocna");
        repo.init();
        try (org.eclipse.rdf4j.repository.RepositoryConnection conn = repo.getConnection()) {
            conn.clear();
            // Load ontology from file
            java.io.File ontologyFile = new java.io.File("../../ontology/MoaMont.rdf");
            if (!ontologyFile.exists()) {
                ontologyFile = new java.io.File("ontology/MoaMont.rdf"); // Try project root
            }
            conn.add(ontologyFile, moaNs, org.eclipse.rdf4j.rio.RDFFormat.RDFXML);
            // Load test data
            conn.add(new java.io.StringReader(testAction), moaNs, org.eclipse.rdf4j.rio.RDFFormat.TURTLE);
        }
        repo.shutDown();
    }

    @Test
    void testEndToEndRemediation() throws Exception {
        loadTestData();

        // Stub WireMock
        wireMockServer.stubFor(post(urlEqualTo("/remediate"))
                .willReturn(aResponse().withStatus(200)));

        // Trigger via gRPC
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                .usePlaintext()
                .build();
        ActionServiceGrpc.ActionServiceBlockingStub stub = ActionServiceGrpc.newBlockingStub(channel);

        ActionRequest request = ActionRequest.newBuilder()
                .setActionId("http://example.org/test#action1")
                .setTargetId("http://example.org/test#res1")
                .build();

        Iterator<ExecutionStatus> response = stub.executeRemediation(request);
        
        boolean successFound = false;
        while (response.hasNext()) {
            ExecutionStatus status = response.next();
            System.out.println("Status: " + status.getState() + " - " + status.getMessage());
            if ("SUCCESS".equals(status.getState())) {
                successFound = true;
            }
        }

        assertTrue(successFound, "Execution should finish with SUCCESS");

        // Verify WireMock
        wireMockServer.verify(postRequestedFor(urlEqualTo("/remediate")));

        // Verify GraphDB status update
        verifyGraphDBStatus("http://example.org/test#action1", "SUCCESS");

        channel.shutdown();
    }

    private void verifyGraphDBStatus(String actionId, String expectedState) {
        String moaNs = "http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#";
        org.eclipse.rdf4j.repository.Repository repo = new org.eclipse.rdf4j.repository.http.HTTPRepository(
                "http://localhost:7200", "amocna");
        repo.init();
        try (org.eclipse.rdf4j.repository.RepositoryConnection conn = repo.getConnection()) {
            String sparql = "PREFIX moa: <" + moaNs + "> " +
                    "SELECT ?status WHERE { <" + actionId + "> moa:hasExecutionStatus ?status }";
            org.eclipse.rdf4j.query.TupleQuery query = conn.prepareTupleQuery(sparql);
            try (org.eclipse.rdf4j.query.TupleQueryResult result = query.evaluate()) {
                assertTrue(result.hasNext(), "Status should be present in GraphDB");
                assertEquals(expectedState, result.next().getValue("status").stringValue());
            }
        }
        repo.shutDown();
    }
}
