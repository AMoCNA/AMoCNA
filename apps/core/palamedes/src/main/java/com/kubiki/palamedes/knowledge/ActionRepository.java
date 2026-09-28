package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActiveActionSummary;
import com.kubiki.palamedes.model.WorkflowState;
import org.eclipse.rdf4j.model.IRI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ActionRepository {
    void transitionState(IRI actionId, String stateFragment);
    void createActionWorkflow(IRI resourceIri, IRI intentIri, String actionId);
    void materializeActionInstance(IRI actionIri, ActionData template, IRI target, IRI parentIri);
    WorkflowState getState(IRI actionIri);
    Map<IRI, WorkflowState> getStates(Collection<IRI> actionIris);
    ActionData fetchActionStructure(IRI actionId);
    Map<IRI, ActionData> fetchActionStructures(List<IRI> actionIds);
    List<ActiveActionSummary> findActiveActions();
    void updateExecutionStatus(IRI actionId, String status);
    IRI findParent(IRI childIri);
    List<IRI> findChildren(IRI parentIri);
    java.time.Instant getLastTransitionTimestamp(IRI actionIri);
}
