package com.kubiki.metis.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.kubiki.metis.grpc.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.support.TestPropertySourceUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.inProcessName=test-sensor-ingestion",
                "grpc.server.port=-1"
        }
)
@ActiveProfiles("test")
@ContextConfiguration(initializers = SensorIngestionGrpcServiceIT.WireMockInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SensorIngestionGrpcServiceIT {

    private static final String CNEE_NS =
            "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/";

    static WireMockServer wireMockServer;
    private static Repository inMemoryRepo;
    private final ValueFactory vf = SimpleValueFactory.getInstance();
    private io.grpc.ManagedChannel channel;
    private SensorIngestionServiceGrpc.SensorIngestionServiceBlockingStub stub;
    @MockitoBean
    private Repository realRepository;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        if (inMemoryRepo == null) {
            inMemoryRepo = new SailRepository(new MemoryStore());
            inMemoryRepo.init();
        }
        clearRepo();

        try {
            when(realRepository.getConnection()).thenAnswer(inv -> inMemoryRepo.getConnection());
            when(realRepository.getValueFactory()).thenReturn(vf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        wireMockServer.resetAll();
        channel = InProcessChannelBuilder.forName("test-sensor-ingestion").directExecutor().build();
        stub = SensorIngestionServiceGrpc.newBlockingStub(channel);
    }

    private void clearRepo() {
        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.begin();
            conn.clear();
            conn.commit();
        }
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
    }

    @Test
    void validEntityDiscoveredBatch_acceptedTrueProcessedCountOne() {
        SensorBatch batch = SensorBatch.newBuilder()
                .setCorrelationId("corr-valid-001")
                .addEvents(SensorEvent.newBuilder().setEntityDiscovered(
                        EntityDiscoveredEvent.newBuilder()
                                .setResourceIri("http://example.org/pod-1")
                                .setOntologyType(CNEE_NS + "ExecutionUnit")
                                .setResourceId("pod-1")
                                .setResourceName("my-pod").build()).build())
                .build();

        IngestResponse response = stub.ingestBatch(batch);
        assertThat(response.getAccepted()).isTrue();
        assertThat(response.getProcessedCount()).isEqualTo(1);
        assertThat(response.getCorrelationId()).isEqualTo("corr-valid-001");

        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            assertThat(conn.hasStatement(
                    vf.createIRI("http://example.org/pod-1"),
                    RDF.TYPE,
                    vf.createIRI(CNEE_NS + "ExecutionUnit"),
                    false)).isTrue();
        }
    }

    @Test
    void emptyBatch_acceptedTrueProcessedCountZero() {
        SensorBatch batch = SensorBatch.newBuilder().setCorrelationId("corr-empty-001").build();
        IngestResponse response = stub.ingestBatch(batch);
        assertThat(response.getAccepted()).isTrue();
        assertThat(response.getProcessedCount()).isEqualTo(0);
    }

    @Test
    void graphDbReturns503_grpcStatusUnavailable() {
        // Mock connection to throw exception simulating 503
        when(realRepository.getConnection()).thenThrow(new org.eclipse.rdf4j.repository.RepositoryException("Service Unavailable (503)"));

        SensorBatch batch = SensorBatch.newBuilder()
                .setCorrelationId("corr-503")
                .addEvents(SensorEvent.newBuilder().setEntityDiscovered(
                        EntityDiscoveredEvent.newBuilder()
                                .setResourceIri("http://example.org/pod-2")
                                .setOntologyType(CNEE_NS + "ExecutionUnit")
                                .setResourceId("pod-2")
                                .setResourceName("p2").build()).build())
                .build();

        assertThatThrownBy(() -> stub.ingestBatch(batch))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.INTERNAL));
    }

    @Test
    void correlationIdExceeds128Chars_acceptedFalse() {
        SensorBatch batch = SensorBatch.newBuilder().setCorrelationId("x".repeat(129)).build();
        IngestResponse response = stub.ingestBatch(batch);
        assertThat(response.getAccepted()).isFalse();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        org.springframework.amqp.rabbit.core.RabbitTemplate mockRabbitTemplate() {
            return mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
        }
    }

    static class WireMockInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            if (wireMockServer == null) {
                wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
                wireMockServer.start();
                WireMock.configureFor("localhost", wireMockServer.port());
            }
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(ctx,
                    "wiremock.port=" + wireMockServer.port(), "wiremock.grpc.port=0");
        }
    }
}
