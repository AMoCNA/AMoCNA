package com.kubiki.palamedes.knowledge;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionHydrationPayloadTest {

    @Test
    void roundTrip_preservesImageUpdateVariables() {
        Map<String, String> original = Map.of(
                "containerName", "front-end",
                "imageRepository", "weaveworksdemos/frontend",
                "targetVersion", "0.3.1",
                "namespace", "sock-shop");

        String serialized = ActionHydrationPayload.serialize(original);
        Map<String, String> restored = ActionHydrationPayload.deserialize(serialized);

        assertThat(restored).containsAllEntriesOf(original);
    }
}
