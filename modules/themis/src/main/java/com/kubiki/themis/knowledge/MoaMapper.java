package com.kubiki.themis.knowledge;

import com.kubiki.themis.constants.ProtocolConstants;
import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.query.BindingSet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MoaMapper {

    public ActionData mapAction(String actionId, Map<String, List<BindingSet>> allBindings) {
        List<BindingSet> bindings = allBindings.get(actionId);
        if (bindings == null || bindings.isEmpty()) return null;

        BindingSet first = bindings.get(0);
        String intent = first.getValue("intent").stringValue();

        if (intent.endsWith("ComplexWorkflow")) {
            List<ActionData> steps = new ArrayList<>();
            Map<String, ActionData> compensations = new HashMap<>();

            for (BindingSet bs : bindings) {
                if (bs.hasBinding("step")) {
                    String stepId = bs.getValue("step").stringValue();
                    // Avoid infinite loops
                    if (!stepId.equals(actionId)) {
                        ActionData stepData = mapAction(stepId, allBindings);
                        if (stepData != null && !steps.contains(stepData)) {
                            steps.add(stepData);
                        }

                        if (bs.hasBinding("compensation")) {
                            String compId = bs.getValue("compensation").stringValue();
                            ActionData compData = mapAction(compId, allBindings);
                            if (compData != null) {
                                compensations.put(stepId, compData);
                            }
                        }
                    }
                }
            }
            return new ActionData.ComplexWorkflow(actionId, intent, steps, compensations);
        } else {
            return mapSimpleActionGroup(bindings); // keep existing logic for SimpleAction
        }
    }

    public ActionData.SimpleAction mapSimpleAction(BindingSet bindings) {
        return mapSimpleActionGroup(List.of(bindings));
    }

    public ActionData.SimpleAction mapSimpleActionGroup(List<BindingSet> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }

        BindingSet first = bindings.get(0);

        Map<String, ActionData.ConditionData> preConditions = new HashMap<>();
        Map<String, ActionData.ConditionData> postConditions = new HashMap<>();

        for (BindingSet bs : bindings) {
            if (bs.hasBinding("preId")) {
                String id = bs.getValue("preId").stringValue();
                preConditions.putIfAbsent(id, new ActionData.ConditionData(
                        id,
                        bs.hasBinding("preType") ? bs.getValue("preType").stringValue() : null,
                        bs.hasBinding("prePolicy") ? bs.getValue("prePolicy").stringValue() : null
                ));
            }
            if (bs.hasBinding("postId")) {
                String id = bs.getValue("postId").stringValue();
                postConditions.putIfAbsent(id, new ActionData.ConditionData(
                        id,
                        bs.hasBinding("postType") ? bs.getValue("postType").stringValue() : null,
                        bs.hasBinding("postPolicy") ? bs.getValue("postPolicy").stringValue() : null
                ));
            }
        }

        return new ActionData.SimpleAction(
                first.getValue("action").stringValue(),
                first.getValue("intent").stringValue(),
                first.hasBinding("protocol") ? first.getValue("protocol").stringValue() : ProtocolConstants.REST,
                first.hasBinding("instruction") ? first.getValue("instruction").stringValue() : "http://localhost:8080/mgmt?target={target}",
                first.getValue("target").stringValue(),
                new HashMap<>(),
                first.hasBinding("method") ? first.getValue("method").stringValue() : null,
                first.hasBinding("payload") ? first.getValue("payload").stringValue() : null,
                List.copyOf(preConditions.values()),
                List.copyOf(postConditions.values())
        );
    }
}
