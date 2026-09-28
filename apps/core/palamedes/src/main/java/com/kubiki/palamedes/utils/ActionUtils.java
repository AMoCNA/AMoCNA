package com.kubiki.palamedes.utils;

import com.kubiki.palamedes.config.PalamedesProperties;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Scope("singleton")
@RequiredArgsConstructor
public final class ActionUtils {

    private final PalamedesProperties properties;


    public String generateActionId() {
        return properties.utilities().actionPrefix() + generateUUID();
    }

    public String generateStepId() {
        return properties.utilities().stepPrefix() + generateUUID();
    }

    public String generateCompensationId() {
        return properties.utilities().compensationPrefix() + generateUUID();
    }

    public String generateOutboxEventId(String namespace) {
        return namespace + "OutboxEvent-" + generateUUID();
    }

    private @NonNull String generateUUID() {
        return UUID.randomUUID().toString().substring(0, properties.utilities().sizeOfGeneratedUuid());
    }
}
