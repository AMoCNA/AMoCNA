package com.kubiki.themis.grpc;

import com.kubiki.themis.execution.ActionDispatcher;
import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import io.grpc.stub.StreamObserver;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActionServiceImpl Tests")
class ActionServiceImplTest {

    private ActionServiceImpl actionService;

    @Mock
    private GraphDBGateway graphDBGateway;

    @Mock
    private ActionDispatcher actionDispatcher;

    @Mock
    private StreamObserver<ActionList> actionListObserver;

    @Mock
    private StreamObserver<ValidationResponse> validationResponseObserver;

    @Mock
    private StreamObserver<ExecutionStatus> executionStatusObserver;

    @BeforeEach
    void setUp() {
        actionService = new ActionServiceImpl(graphDBGateway, actionDispatcher);
    }

    @Test
    @DisplayName("Should return action list when getExecutableActions is called")
    void shouldReturnActionListWhenGetExecutableActionsIsCalled() {
        // Given
        String resourceId = "http://cnee#worker-1";
        ResourceRequest request = ResourceRequest.newBuilder().setResourceId(resourceId).build();
        ActionData.SimpleAction mockAction = new ActionData.SimpleAction(
                SimpleValueFactory.getInstance().createIRI("http://moa#action-1"),
                "DeletePod",
                Protocol.REST,
                "http://mgmt",
                SimpleValueFactory.getInstance().createIRI("http://cnee#pod-1"),
                Map.of(),
                HttpMethod.GET,
                null,
                List.of(),
                List.of()
        );
        when(graphDBGateway.findActionsForResource(SimpleValueFactory.getInstance().createIRI(resourceId))).thenReturn(List.of(mockAction));

        // When
        actionService.getExecutableActions(request, actionListObserver);

        // Then
        ArgumentCaptor<ActionList> captor = ArgumentCaptor.forClass(ActionList.class);
        verify(actionListObserver).onNext(captor.capture());
        verify(actionListObserver).onCompleted();

        ActionList response = captor.getValue();
        assertEquals(1, response.getActionsCount());
        assertEquals("http://moa#action-1", response.getActions(0).getId());
        assertEquals("SimpleAction", response.getActions(0).getType());
        assertEquals("DeletePod", response.getActions(0).getFunctionalIntent());
    }

    @Test
    @DisplayName("Should return valid response when validatePreconditions is called")
    void shouldReturnValidResponseWhenValidatePreconditionsIsCalled() {
        // Given
        ActionRequest request = ActionRequest.newBuilder().setActionId("http://moa#action-1").setTargetId("http://cnee#pod-1").build();

        // When
        actionService.validatePreconditions(request, validationResponseObserver);

        // Then
        ArgumentCaptor<ValidationResponse> captor = ArgumentCaptor.forClass(ValidationResponse.class);
        verify(validationResponseObserver).onNext(captor.capture());
        verify(validationResponseObserver).onCompleted();

        ValidationResponse response = captor.getValue();
        assertTrue(response.getValid());
    }

    @Test
    @DisplayName("Should return success status when executeRemediation is called")
    void shouldReturnSuccessStatusWhenExecuteRemediationIsCalled() {
        // Given
        ActionRequest request = ActionRequest.newBuilder().setActionId("http://moa#action-1").setTargetId("http://cnee#pod-1").build();
        ActionData mockAction = new ActionData.SimpleAction(
                SimpleValueFactory.getInstance().createIRI("http://moa#action-1"),
                "DeletePod",
                Protocol.REST,
                "http://mgmt",
                SimpleValueFactory.getInstance().createIRI("http://cnee#pod-1"),
                Map.of(),
                HttpMethod.GET,
                null,
                List.of(),
                List.of()
        );
        when(graphDBGateway.fetchActionStructure(SimpleValueFactory.getInstance().createIRI("http://moa#action-1"))).thenReturn(mockAction);
        when(actionDispatcher.dispatch(any(ActionData.class), any(UUID.class))).thenReturn(true);

        // When
        actionService.executeRemediation(request, executionStatusObserver);

        // Then
        verify(executionStatusObserver, times(2)).onNext(any(ExecutionStatus.class));
        verify(executionStatusObserver).onCompleted();

        ArgumentCaptor<ExecutionStatus> captor = ArgumentCaptor.forClass(ExecutionStatus.class);
        verify(executionStatusObserver, times(2)).onNext(captor.capture());
        
        List<ExecutionStatus> statuses = captor.getAllValues();
        assertEquals("IN_PROGRESS", statuses.get(0).getState());
        assertEquals("SUCCESS", statuses.get(statuses.size() - 1).getState());
    }
}
