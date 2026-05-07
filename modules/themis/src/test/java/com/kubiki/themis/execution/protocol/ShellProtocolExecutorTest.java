package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.model.ActionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellProtocolExecutorTest {

    private ShellProtocolExecutor shellProtocolExecutor;

    @BeforeEach
    void setUp() {
        shellProtocolExecutor = new ShellProtocolExecutor();
    }

    @Test
    void shouldSupportShellProtocol() {
        assertTrue(shellProtocolExecutor.supports("SHELL"));
        assertFalse(shellProtocolExecutor.supports("REST"));
    }

    @Test
    void shouldExecuteEchoCommand() {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "test:echo",
                "EchoAction",
                "SHELL",
                "echo 'Hello {name}'",
                "test:resource",
                Map.of("name", "Themis"),
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList()
        );
        UUID executionId = UUID.randomUUID();

        // When
        boolean result = shellProtocolExecutor.execute(action, executionId);

        // Then
        assertTrue(result);
    }

    @Test
    void shouldFailOnInvalidCommand() {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "test:fail",
                "FailAction",
                "SHELL",
                "non-existent-command",
                "test:resource",
                Map.of(),
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList()
        );
        UUID executionId = UUID.randomUUID();

        // When
        boolean result = shellProtocolExecutor.execute(action, executionId);

        // Then
        assertFalse(result);
    }
}
