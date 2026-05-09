package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ShellProtocolExecutor Unit Tests")
class ShellProtocolExecutorTest {

    private ShellProtocolExecutor shellProtocolExecutor;

    @BeforeEach
    void setUp() {
        shellProtocolExecutor = new ShellProtocolExecutor();
    }

    @Test
    @DisplayName("Should support SHELL protocol and reject others")
    void shouldSupportShellProtocol() {
        assertAll(
                () -> assertTrue(shellProtocolExecutor.supports(Protocol.SHELL), "Should support SHELL"),
                () -> assertFalse(shellProtocolExecutor.supports(Protocol.REST), "Should NOT support REST")
        );
    }

    @Test
    @DisplayName("Should execute valid shell command with hydration")
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
        assertTrue(result, "Shell command should exit with 0");
    }

    @Test
    @DisplayName("Should fail on invalid shell command")
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
        assertFalse(result, "Invalid shell command should return false");
    }

    @Test
    @DisplayName("Should fail when action is not a SimpleAction")
    void shouldFailWhenActionIsNotSimpleAction() {
        ActionData action = new ActionData.ComplexWorkflow(
                "test:generic",
                "GenericAction",
                java.util.Collections.emptyList(),
                java.util.Collections.emptyMap()
        );
        assertFalse(shellProtocolExecutor.execute(action, UUID.randomUUID()), "Non-SimpleAction should fail");
    }

    @Test
    @DisplayName("Should handle null instruction by returning false")
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

        assertFalse(shellProtocolExecutor.execute(action, UUID.randomUUID()), "Null instruction should result in execution failure");
    }

    @Test
    @DisplayName("Should handle empty instruction (success via /bin/sh -c '')")
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

        assertTrue(shellProtocolExecutor.execute(action, UUID.randomUUID()), "Empty instruction should return success");
    }
}
