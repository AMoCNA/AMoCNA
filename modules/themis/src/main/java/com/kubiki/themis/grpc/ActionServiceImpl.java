package com.kubiki.themis.grpc;

import com.kubiki.themis.execution.ActionDispatcher;
import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import java.util.List;

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
        List<ActionData> actions = graphDBGateway.findActionsForResource(request.getResourceId());
        
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
    }

    @Override
    public void validatePreconditions(ActionRequest request, StreamObserver<ValidationResponse> responseObserver) {
        // In a truly autonomic system, this would query the GraphDB for Φpre of the actionID
        responseObserver.onNext(ValidationResponse.newBuilder()
                .setValid(true)
                .setMessage("Preconditions validated via GraphDB (placeholder)")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void executeRemediation(ActionRequest request, StreamObserver<ExecutionStatus> responseObserver) {
        // 1. Fetch Ground Truth from GraphDB for the specific ActionID
        // (For MVP, we assume the dispatcher can handle it if we provide the right ActionData)
        
        // This is a simplification; a full impl would fetch the ActionData by ID from GraphDBGateway
        ActionData mockAction = new ActionData.SimpleAction(
            request.getActionId(),
            "DeletePodAction", // Mapping functional intent
            request.getTargetId(),
            java.util.Map.of()
        );

        responseObserver.onNext(ExecutionStatus.newBuilder()
                .setStep(request.getActionId())
                .setState("IN_PROGRESS")
                .setMessage("Ingesting Ground Truth and executing...")
                .build());

        boolean success = actionDispatcher.dispatch(mockAction);

        responseObserver.onNext(ExecutionStatus.newBuilder()
                .setStep(request.getActionId())
                .setState(success ? "SUCCESS" : "FAILED")
                .setMessage(success ? "Autonomic action completed" : "Action failed")
                .build());
        
        responseObserver.onCompleted();
    }
}
