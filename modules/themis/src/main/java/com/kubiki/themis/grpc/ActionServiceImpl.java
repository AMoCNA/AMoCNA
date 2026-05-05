package com.kubiki.themis.grpc;

import com.kubiki.themis.execution.ActionDispatcher;
import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import java.util.List;
import java.util.UUID;
import java.util.Map;

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
        responseObserver.onNext(ValidationResponse.newBuilder()
                .setValid(true)
                .setMessage("Preconditions validated via GraphDB (placeholder)")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void executeRemediation(ActionRequest request, StreamObserver<ExecutionStatus> responseObserver) {
        // Generate UUID for this specific execution instance
        UUID executionId = UUID.randomUUID();
        
        // In a full implementation, we fetch the Ground Truth from GraphDB for the specific ActionID
        // This includes retrieving the protocol (REST/SHELL) and the instruction (URL template/script)
        ActionData groundTruthAction = new ActionData.SimpleAction(
            request.getActionId(),
            "DeletePodAction", // Intent
            "REST",            // Protocol from GraphDB
            "http://localhost:8080/kubernetes/management/pod/delete?namespace={ns}&podName={pod}", // Ground Truth Instruction
            request.getTargetId(),
            Map.of("ns", "default", "pod", "my-pod") // Parameters from GraphDB/Resource
        );

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
