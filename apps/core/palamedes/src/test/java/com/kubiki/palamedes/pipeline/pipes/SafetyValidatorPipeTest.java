package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.palamedes.condition.ConditionFactory;
import com.kubiki.palamedes.condition.ConditionStrategy;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SafetyValidatorPipeTest {
    @Mock
    private StateRepository stateRepository;
    @Mock
    private ConditionFactory conditionFactory;
    @Mock
    private GraphDBGateway gateway;
    @Mock
    private WorkflowStateMapper mapper;

    @InjectMocks
    private SafetyValidatorPipe pipe;

    @Test
    void shouldPassValidation() {
        ActionData.Condition cond = new ActionData.Condition(null, null, null);
        ActionData data = mock(ActionData.SimpleAction.class);
        when(data.preConditions()).thenReturn(List.of(cond));

        WorkflowContext context = new WorkflowContext(SimpleValueFactory.getInstance().createIRI("http://test/1"), data);
        context.metadata().put("currentState", "State_Planned");

        when(mapper.getFragment(WorkflowState.PLANNED)).thenReturn("State_Planned");
        when(mapper.getFragment(WorkflowState.VALIDATED)).thenReturn("State_Validated");

        ConditionStrategy strategy = mock(ConditionStrategy.class);
        when(strategy.evaluate(any())).thenReturn(true);
        when(conditionFactory.getStrategy(any())).thenReturn(Optional.of(strategy));
        when(stateRepository.transition(any(), any(), any())).thenReturn(true);

        boolean result = pipe.process(context);

        assertTrue(result);
        verify(stateRepository).transition(any(), eq(WorkflowState.PLANNED), eq(WorkflowState.VALIDATED));
    }
}