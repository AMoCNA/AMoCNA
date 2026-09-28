package com.kubiki.palamedes.aspect;

import com.kubiki.common.logging.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.junit.jupiter.api.Assertions.*;

class SagaStepAspectTest {

    private TestService proxy;
    private TestService target;

    @BeforeEach
    void setUp() {
        target = new TestService();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        SagaStepAspect aspect = new SagaStepAspect();
        factory.addAspect(aspect);
        proxy = factory.getProxy();
    }

    @Test
    void shouldExecuteSuccessfullyOnFirstAttemptWithoutRetries() {
        // Force success on the first call
        target.setTargetCallsToSucceed(1);

        String result = proxy.executeStep("hello");

        assertEquals("Success hello", result);
        assertEquals(1, target.getCalls());
        assertFalse(target.isCompensationCalled());
    }

    @Test
    void shouldRetryOnConfiguredExceptionAndEventuallySucceed() {
        // Force success on the 3rd attempt
        target.setTargetCallsToSucceed(3);

        String result = proxy.executeStep("hello");

        assertEquals("Success hello", result);
        assertEquals(3, target.getCalls());
        assertFalse(target.isCompensationCalled());
    }

    @Test
    void shouldTriggerCompensationAndThrowExceptionWhenRetriesAreExhausted() {
        // Force success on the 4th attempt (which exceeds maxRetries of 2, meaning max 3 attempts total)
        target.setTargetCallsToSucceed(4);

        assertThrows(RuntimeException.class, () -> proxy.executeStep("hello"));

        assertEquals(3, target.getCalls()); // 1 original + 2 retries = 3 attempts
        assertTrue(target.isCompensationCalled());
    }

    @Test
    void shouldNotRetryOnNonConfiguredExceptionAndImmediatelyCompensate() {
        assertThrows(NullPointerException.class, () -> proxy.executeNonRetryable("hello"));

        assertEquals(1, target.getCalls()); // Fails on 1st call, exception is NullPointerException which is not IllegalArgumentException
        assertTrue(target.isCompensationCalled());
    }

    static class TestService {
        private int calls = 0;
        private boolean compensationCalled = false;
        private int targetCallsToSucceed = 1;

        public int getCalls() {
            return calls;
        }

        public boolean isCompensationCalled() {
            return compensationCalled;
        }

        public void setTargetCallsToSucceed(int target) {
            this.targetCallsToSucceed = target;
        }

        @SagaStep(
            name = "TEST_STEP",
            compensationMethod = "rollbackStep",
            maxRetries = 2,
            backoffMs = 50,
            retryOn = {RuntimeException.class}
        )
        public String executeStep(String input) {
            calls++;
            if (calls < targetCallsToSucceed) {
                throw new RuntimeException("Transient failure");
            }
            return "Success " + input;
        }

        @SagaStep(
            name = "TEST_STEP_NON_RETRYABLE",
            compensationMethod = "rollbackStep",
            maxRetries = 3,
            backoffMs = 10,
            retryOn = {IllegalArgumentException.class}
        )
        public String executeNonRetryable(String input) {
            calls++;
            throw new NullPointerException("Null failure");
        }

        public void rollbackStep(String input) {
            compensationCalled = true;
        }
    }
}
