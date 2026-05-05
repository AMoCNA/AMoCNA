package com.kubiki.themis.grpc;

import com.kubiki.themis.execution.ActionExecutor;
import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.saga.SagaEngine;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@GrpcService
public class ActionServiceImpl extends ActionServiceGrpc.ActionServiceImplBase {

    private final GraphDBGateway graphDBGateway;
    private final Map<String, ActionExecutor> executors;

    public ActionServiceImpl(GraphDBGateway graphDBGateway, List<ActionExecutor> executorList) {
        this.graphDBGateway = graphDBGateway;
        this.executors = executorList.stream()
                .collect(Collectors.toMap(ActionExecutor::getActionType, Function.identity()));
    }

    @Override
    public void getExecutableActions(ResourceRequest request, StreamObserver<ActionList> responseObserver) {
        List<ActionData> actions = graphDBGateway.findActionsForResource(request.getResourceId());
        
        ActionList.Builder listBuilder = ActionList.newBuilder();
        for (ActionData action : actions) {
            // Simple mapping for demonstration, real logic would fetch full Action details from GraphDB
            listBuilder.addActions(Action.newBuilder()
                    .setId(action.id())
                    .setType("SimpleAction")
                    .setFunctionalIntent(action.functionalIntent())
                    .build());
        }
        
        responseObserver.onNext(listBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void validatePreconditions(ActionRequest request, StreamObserver<ValidationResponse> responseObserver) {
        // Semantic validation logic would go here
        responseObserver.onNext(ValidationResponse.newBuilder()
                .setValid(true)
                .setMessage("Preconditions satisfied (Semantic placeholder)")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void executeRemediation(ActionRequest request, StreamObserver<ExecutionStatus> responseObserver) {
        // Logic to resolve ActionID to Step list would be here
        // For now, mapping hard-coded "DeletePodAction" as individual
        
        String actionName = "DeletePodAction"; // Simplification for MVP
        ActionExecutor executor = executors.get(actionName);
        
        if (executor != null) {
            SagaEngine saga = new SagaEngine();
            saga.addStep(new SagaEngine.Step(actionName, executor, request.getTargetId()));
            
            responseObserver.onNext(ExecutionStatus.newBuilder()
                    .setStep(actionName)
                    .setState("IN_PROGRESS")
                    .setMessage("Starting execution")
                    .build());
            
            boolean success = saga.run();
            
            responseObserver.onNext(ExecutionStatus.newBuilder()
                    .setStep(actionName)
                    .setState(success ? "SUCCESS" : "REVERTED")
                    .setMessage(success ? "Action completed" : "Action failed and compensated")
                    .build());
        } else {
            responseObserver.onNext(ExecutionStatus.newBuilder()
                    .setStep("Unknown")
                    .setState("FAILED")
                    .setMessage("No executor found for action")
                    .build());
        }
        
        responseObserver.onCompleted();
    }
}
