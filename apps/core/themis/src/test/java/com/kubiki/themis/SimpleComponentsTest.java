package com.kubiki.themis;

import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.common.model.Protocol;
import com.kubiki.themis.config.ThemisProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Simple Components Tests")
class SimpleComponentsTest {

    @ParameterizedTest
    @EnumSource(Protocol.class)
    @DisplayName("Protocol enum values should be valid")
    void testProtocolEnum(Protocol protocol) {
        assertAll(
                () -> assertTrue(Protocol.values().length > 0),
                () -> assertEquals(protocol, Protocol.valueOf(protocol.name()))
        );
    }

    @ParameterizedTest
    @EnumSource(ExecutionStatus.class)
    @DisplayName("ExecutionStatus enum values should be valid")
    void testExecutionStatusEnum(ExecutionStatus status) {
        assertAll(
                () -> assertTrue(ExecutionStatus.values().length > 0),
                () -> assertEquals(status, ExecutionStatus.valueOf(status.name()))
        );
    }


    @Test
    @DisplayName("ThemisProperties and its nested records should work correctly")
    void testThemisProperties() {
        ThemisProperties.Secret secret = new ThemisProperties.Secret("token");
        ThemisProperties.Execution execution = new ThemisProperties.Execution(1000);
        
        ThemisProperties props = new ThemisProperties(secret, execution);
        
        assertAll(
                () -> assertEquals("token", props.secret().bearerToken()),
                () -> assertEquals(1000, props.execution().postConditionDelayMs()),
                () -> assertNotNull(props.toString()),
                () -> assertEquals(props, new ThemisProperties(
                        new ThemisProperties.Secret("token"),
                        new ThemisProperties.Execution(1000)))
        );
    }
}
