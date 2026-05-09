package com.kubiki.themis.knowledge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void shouldCreateSuccess() {
        Result<String> result = Result.success("test");
        assertTrue(result.isSuccess());
        assertEquals("test", result.value());
        assertNull(result.error());
    }

    @Test
    void shouldCreateFailure() {
        Result<String> result = Result.failure("error message");
        assertFalse(result.isSuccess());
        assertEquals("error message", result.error());
        assertNull(result.value());
    }

    @Test
    void shouldMapSuccess() {
        Result<Integer> initial = Result.success(5);
        Result<String> mapped = initial.map(val -> "Number: " + val);
        assertTrue(mapped.isSuccess());
        assertEquals("Number: 5", mapped.value());
    }

    @Test
    void shouldNotMapFailure() {
        Result<Integer> initial = Result.failure("error");
        Result<String> mapped = initial.map(val -> "Number: " + val);
        assertFalse(mapped.isSuccess());
        assertEquals("error", mapped.error());
    }
}
