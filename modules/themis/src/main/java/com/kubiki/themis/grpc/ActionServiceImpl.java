package com.kubiki.themis.grpc;

import com.kubiki.themis.execution.ActionDispatcher;
import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.UUID;

@GrpcService
public class ActionServiceImpl extends ActionServiceGrpc.ActionServiceImplBase {

    private final GraphDBGateway graphDBGateway;
    private final ActionDispatcher actionDispatcher;

    public ActionServiceImpl(GraphDBGateway graphDBGateway, ActionDispatcher actionDispatcher) {
        this.graphDBGateway = graphDBGateway;
        this.actionDispatcher = actionDispatcher;
    }

    @Override
    public void getExecutableActions(ResourceRequest request, StreamObserver<ActionList> responseObserver) {
        try {
            List<? extends ActionData> actions = graphDBGateway.findActionsForResource(request.getResourceId());

            ActionList.Builder listBuilder = ActionList.newBuilder();
            for (ActionData action : actions) {
                listBuilder.addActions(Action.newBuilder()
                        .setId(action.id())
                        .setType(action instanceof ActionData.SimpleAction ? "SimpleAction" : "ComplexWorkflow")
                        .setFunctionalIntent(action.functionalIntent())
                        .build());
            }

            responseObserver.onNext(listBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.UNAVAILABLE
                    .withDescription("GraphDB Knowledge Base is not reachable: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void validatePreconditions(ActionRequest request, StreamObserver<ValidationResponse> responseObserver) {
        responseObserver.onNext(ValidationResponse.newBuilder()
                .setValid(true)
                .setMessage("Preconditions validated via GraphDB (placeholder)")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void executeRemediation(ActionRequest request, StreamObserver<ExecutionStatus> responseObserver) {
        UUID executionId = UUID.randomUUID();

        // Fetch the Ground Truth from GraphDB for the specific ActionID
        ActionData groundTruthAction = graphDBGateway.fetchActionStructure(request.getActionId());

        if (groundTruthAction == null) {
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription("Action structure not found in Knowledge Base: " + request.getActionId())
                    .asRuntimeException());
            return;
        }

        responseObserver.onNext(ExecutionStatus.newBuilder()
                .setStep(request.getActionId())
                .setState("IN_PROGRESS")
                .setMessage("Ingesting Ground Truth and executing instance: " + executionId)
                .build());

        boolean success = actionDispatcher.dispatch(groundTruthAction, executionId);

        responseObserver.onNext(ExecutionStatus.newBuilder()
                .setStep(request.getActionId())
                .setState(success ? "SUCCESS" : "FAILED")
                .setMessage(success ? "Autonomic remediation completed" : "Action failed")
                .build());

        responseObserver.onCompleted();
    }
}
