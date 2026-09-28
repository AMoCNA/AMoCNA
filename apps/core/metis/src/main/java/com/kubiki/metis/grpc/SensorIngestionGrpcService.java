package com.kubiki.metis.grpc;

import com.kubiki.common.logging.MdcContext;
import com.kubiki.common.logging.MdcParam;
import com.kubiki.common.logging.ValidateSchema;
import com.kubiki.metis.ingestion.SensorEventProcessor;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.ingestion.model.ProcessResult;
import com.kubiki.metis.notification.PalamedesNotifier;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC service implementation for the {@code SensorIngestionService} contract.
 *
 * <p>Receives {@link SensorBatch} messages from external sensors, validates the
 * {@code correlation_id}, delegates event processing to {@link SensorEventProcessor},
 * and returns an {@link IngestResponse}.
 *
 * <p>Error handling:
 * <ul>
 *   <li>GraphDB unavailable → gRPC {@code UNAVAILABLE}</li>
 *   <li>Any other unexpected exception → gRPC {@code INTERNAL}</li>
 * </ul>
 */
@GrpcService
public class SensorIngestionGrpcService
        extends SensorIngestionServiceGrpc.SensorIngestionServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(SensorIngestionGrpcService.class);

    private static final int MAX_CORRELATION_ID_LENGTH = 128;

    private final SensorEventProcessor processor;
    private final PalamedesNotifier notifier;

    public SensorIngestionGrpcService(SensorEventProcessor processor,
                                      PalamedesNotifier notifier) {
        this.processor = processor;
        this.notifier = notifier;
    }

    @Override
    @MdcContext
    @ValidateSchema
    public void ingestBatch(
            @MdcParam(value = "correlationId", property = "correlationId") SensorBatch request,
            StreamObserver<IngestResponse> responseObserver) {
        String correlationId = request.getCorrelationId();
        try {
            if (correlationId.length() > MAX_CORRELATION_ID_LENGTH) {
                log.warn("Rejected batch: correlation_id exceeds {} characters [length={}]",
                        MAX_CORRELATION_ID_LENGTH, correlationId.length());
                responseObserver.onNext(IngestResponse.newBuilder()
                        .setAccepted(false)
                        .setCorrelationId(correlationId)
                        .setProcessedCount(0)
                        .setMessage("correlation_id must not exceed " + MAX_CORRELATION_ID_LENGTH + " characters")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            ProcessResult processResult = processor.processBatch(
                    request.getEventsList(), correlationId);

            if (processResult.graphDbFailed()) {
                String failureMsg = processResult.failureMessages().isEmpty()
                        ? "Knowledge base unavailable"
                        : processResult.failureMessages().get(0);
                responseObserver.onError(
                        Status.UNAVAILABLE
                                .withDescription("Knowledge base unavailable: " + failureMsg)
                                .asRuntimeException());
                return;
            }

            HandlerResult firstSuccess = processResult.firstSuccess();
            if (firstSuccess != null) {
                notifier.notify(
                        firstSuccess.resourceIri(),
                        firstSuccess.ontologyType(),
                        firstSuccess.changeKind(),
                        correlationId);
            }

            boolean batchWasEmpty = request.getEventsList().isEmpty();
            boolean accepted = processResult.processedCount() > 0 || batchWasEmpty;

            String message = processResult.failureMessages().isEmpty()
                    ? ""
                    : String.join("; ", processResult.failureMessages());

            IngestResponse response = IngestResponse.newBuilder()
                    .setAccepted(accepted)
                    .setCorrelationId(correlationId)
                    .setProcessedCount(processResult.processedCount())
                    .setMessage(message)
                    .build();

            log.info("Successfully ingested sensor batch [correlationId={}, processedCount={}, accepted={}]",
                    correlationId, processResult.processedCount(), accepted);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Unexpected error during batch ingestion: {}", e.getMessage(), e);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Internal error: " + e.getMessage())
                            .withCause(e)
                            .asRuntimeException());
        }
    }
}
