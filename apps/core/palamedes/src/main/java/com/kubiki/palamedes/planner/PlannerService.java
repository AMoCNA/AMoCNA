package com.kubiki.palamedes.planner;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.daedalus.core.DaedalusHydrator;
import com.kubiki.palamedes.model.ActionData;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PlannerService {
    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);
    private final DaedalusHydrator hydrator;

    public PlannerService(DaedalusHydrator hydrator) {
        this.hydrator = hydrator;
    }

    public String hydrate(String template, Map<String, String> data) {
        if (template == null) return null;
        if (data == null) return template;

        Map<String, Object> hydrationData = new HashMap<>(data);
        return hydrator.hydrate(template, hydrationData);
    }

    @Timed(value = "palamedes.planner.build.message", description = "Time taken to build action message")
    public ActionMessage buildActionMessage(ActionData.SimpleAction action, Map<String, String> contextData) {
        log.info("Planning message for action {} (protocol: {})", action.id(), action.protocol());

        String instruction = hydrate(action.instruction(), contextData);
        String payload = hydrate(action.payload(), contextData);

        return new ActionMessage(
                action.id().toString(),
                action.protocol(),
                instruction,
                action.method() != null ? action.method().name() : null,
                payload,
                action.authMechanism(),
                action.timeoutSeconds(),
                action.isIdempotent(),
                action.maxRetries(),
                action.expectedStatusCode(),
                action.priority()
        );
    }
}
