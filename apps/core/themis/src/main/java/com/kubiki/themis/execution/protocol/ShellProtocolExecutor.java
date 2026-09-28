package com.kubiki.themis.execution.protocol;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.common.model.Protocol;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.execution.ProtocolExecutor;
import com.kubiki.themis.model.ExecutionResult;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;

/**
 * Generic Shell Protocol Interpreter.
 * Executes hydrated Shell commands.
 */
@Component
public class ShellProtocolExecutor implements ProtocolExecutor {
    public static final String BIN_SH = "/bin/sh";
    private static final Logger log = LoggerFactory.getLogger(ShellProtocolExecutor.class);
    private static final int EXIT_CODE_FAILURE = 1;
    private static final int DEFAULT_TIMEOUT_FALLBACK = 30;

    private final MeterRegistry meterRegistry;
    private final ThemisProperties themisProperties;

    @Autowired
    public ShellProtocolExecutor(MeterRegistry meterRegistry, ThemisProperties themisProperties) {
        this.meterRegistry = meterRegistry;
        this.themisProperties = themisProperties;
    }

    public ShellProtocolExecutor(MeterRegistry meterRegistry) {
        this(meterRegistry, new ThemisProperties(new ThemisProperties.Secret(""), new ThemisProperties.Execution(0)));
    }

    @Override
    public boolean supports(Protocol protocol) {
        return Protocol.SHELL.equals(protocol);
    }

    @Override
    @Timed(value = "themis.execution.action", extraTags = {"protocol", "shell"}, description = "Time taken to execute shell action")
    public ExecutionResult executeStateless(ActionMessage action) {
        return executeCommand(action);
    }

    private ExecutionResult executeCommand(ActionMessage action) {
        String command = action.instruction();
        String actionId = action.actionId();
        int expectedStatusCode = action.expectedStatusCode();

        if (command == null || command.isBlank()) {
            log.error("Instruction is null or blank for action {}", actionId);
            return ExecutionResult.failure(EXIT_CODE_FAILURE, "Blank instruction", ExecutionStatus.FAILED_INTERNAL);
        }

        try {
            if (command.contains("FAIL_NOW")) {
                log.warn("Simulating failure for action {} due to FAIL_NOW keyword", actionId);
                return ExecutionResult.failure(EXIT_CODE_FAILURE, "Simulated failure", ExecutionStatus.FAILED_INTERNAL);
            }

            log.info("Executing shell command for action {}: {}", actionId, command);

            String shellPath = (themisProperties != null && themisProperties.execution() != null
                    && themisProperties.execution().defaultShellPath() != null
                    && !themisProperties.execution().defaultShellPath().isBlank())
                    ? themisProperties.execution().defaultShellPath()
                    : BIN_SH;

            ProcessBuilder pb = new ProcessBuilder();
            Process process = pb.command(shellPath, "-c", command).start();

            Thread.ofVirtual().start(() -> readStream(process.inputReader(), "STDOUT", actionId));
            Thread.ofVirtual().start(() -> readStream(process.errorReader(), "STDERR", actionId));

            int fallbackTimeout = (themisProperties != null && themisProperties.execution() != null)
                    ? themisProperties.execution().defaultTimeoutSeconds()
                    : DEFAULT_TIMEOUT_FALLBACK;
            int timeout = action.timeoutSeconds() > 0 ? action.timeoutSeconds() : fallbackTimeout;
            boolean completed = process.waitFor(timeout, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                log.error("Shell command {} timed out after {} seconds, destroying process", actionId, timeout);
                process.destroyForcibly();
                return ExecutionResult.failure(EXIT_CODE_FAILURE, "Shell command timed out after " + timeout + " seconds", ExecutionStatus.FAILED_TIMEOUT);
            }

            int exitCode = process.exitValue();
            log.info("Shell command {} exited with code {}", actionId, exitCode);

            if (exitCode == expectedStatusCode) {
                return ExecutionResult.success(exitCode);
            } else {
                return ExecutionResult.failure(exitCode, "Command failed with exit code " + exitCode, ExecutionStatus.FAILED_INTERNAL);
            }
        } catch (InterruptedException e) {
            log.error("Shell action {} was interrupted: {}", actionId, e.getMessage());
            Thread.currentThread().interrupt();
            return ExecutionResult.failure(EXIT_CODE_FAILURE, e.getMessage(), ExecutionStatus.FAILED_TIMEOUT);
        } catch (Exception e) {
            log.error("Failed to execute shell action {}: {}", actionId, e.getMessage());
            return ExecutionResult.failure(EXIT_CODE_FAILURE, e.getMessage(), ExecutionStatus.FAILED_INTERNAL);
        }
    }

    private void readStream(BufferedReader br, String type, String actionId) {
        MDC.put("actionId", actionId);
        MDC.put("protocol", "SHELL");
        try {
            String line;
            while ((line = br.readLine()) != null) {
                log.info("[{}] {}: {}", actionId, type, line);
            }
        } catch (Exception e) {
            log.error("Error reading {} for action {}: {}", type, actionId, e.getMessage());
        } finally {
            MDC.clear();
        }
    }
}
