package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ShellProtocolExecutor Tests")
class ShellProtocolExecutorTest {

    private ShellProtocolExecutor shellProtocolExecutor;

    @BeforeEach
    void setUp() {
        shellProtocolExecutor = new ShellProtocolExecutor();
    }

    @Test
    @DisplayName("Should return true only for SHELL protocol when checking support")
    void shouldSupportShellProtocol() {
        assertTrue(shellProtocolExecutor.supports(Protocol.SHELL));
        assertFalse(shellProtocolExecutor.supports(Protocol.REST));
    }

    @Test
    @DisplayName("Should execute command successfully when command is valid")
    void shouldExecuteEchoCommand() {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                SimpleValueFactory.getInstance().createIRI("test:echo"),
                "EchoAction",
                Protocol.SHELL,
                "echo 'Hello {name}'",
                SimpleValueFactory.getInstance().createIRI("test:resource"),
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
    @DisplayName("Should return false when command is invalid or fails")
    void shouldFailOnInvalidCommand() {
        // Given
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                SimpleValueFactory.getInstance().createIRI("test:fail"),
                "FailAction",
                Protocol.SHELL,
                "non-existent-command",
                SimpleValueFactory.getInstance().createIRI("test:resource"),
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
