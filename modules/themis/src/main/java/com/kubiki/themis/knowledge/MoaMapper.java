package com.kubiki.themis.knowledge;

import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.query.BindingSet;
import org.springframework.stereotype.Component;
import java.util.HashMap;

@Component
public class MoaMapper {
    public ActionData.SimpleAction mapSimpleAction(BindingSet bindings) {
        return new ActionData.SimpleAction(
            bindings.getValue("action").stringValue(),
            bindings.getValue("intent").stringValue(),
            bindings.hasBinding("protocol") ? bindings.getValue("protocol").stringValue() : "REST",
            bindings.hasBinding("instruction") ? bindings.getValue("instruction").stringValue() : "http://localhost:8080/mgmt?target={target}",
            bindings.getValue("target").stringValue(),
            new java.util.HashMap<>(),
            bindings.hasBinding("method") ? bindings.getValue("method").stringValue() : null,
            bindings.hasBinding("payload") ? bindings.getValue("payload").stringValue() : null,
            java.util.List.of(),
            java.util.List.of()
        );
    }
}
