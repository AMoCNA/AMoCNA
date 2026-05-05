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
            bindings.getValue("target").stringValue(),
            new HashMap<>() // Parameters would be fetched in a second query or JOIN if needed
        );
    }
}
