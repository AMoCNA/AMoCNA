package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
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
        assertTrue(shellProtocolExecutor.supports(Protocol.SHELL));
        assertFalse(shellProtocolExecutor.supports(Protocol.REST));
    }

    @Test
    void shouldExecuteEchoCommand() {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "test:echo",
                "EchoAction",
                Protocol.SHELL,
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
                Protocol.SHELL,
                "non-existent-command-that-should-fail",
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

    @Test
    void shouldFailWhenActionIsNotSimpleAction() {
        ActionData action = org.mockito.Mockito.mock(ActionData.class);
        org.mockito.Mockito.when(action.id()).thenReturn("test:generic");
        assertFalse(shellProtocolExecutor.execute(action, UUID.randomUUID()));
    }

    @Test
    void shouldHandleNullInstruction() {
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "test:null",
                "NullAction",
                Protocol.SHELL,
                null,
                "test:resource",
                Map.of(),
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList()
        );

        // This will result in command being null, which causes ProcessBuilder to throw NPE
        assertFalse(shellProtocolExecutor.execute(action, UUID.randomUUID()));
    }

    @Test
    void shouldHandleEmptyInstruction() {
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "test:empty",
                "EmptyAction",
                Protocol.SHELL,
                "",
                "test:resource",
                Map.of(),
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList()
        );

        // Executing empty command via /bin/sh -c "" should return 0 (success)
        assertTrue(shellProtocolExecutor.execute(action, UUID.randomUUID()));
    }
}
