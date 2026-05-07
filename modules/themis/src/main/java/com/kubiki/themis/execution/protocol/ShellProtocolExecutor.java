package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.constants.ProtocolConstants;
import com.kubiki.themis.execution.ProtocolExecutor;
import com.kubiki.themis.model.ActionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;

@Component
public class ShellProtocolExecutor implements ProtocolExecutor {
    private static final Logger log = LoggerFactory.getLogger(ShellProtocolExecutor.class);

    @Override
    public boolean supports(String protocol) {
        return ProtocolConstants.SHELL.equalsIgnoreCase(protocol);
    }

    @Override
    public boolean execute(ActionData action, UUID executionId) {
        if (!(action instanceof ActionData.SimpleAction simpleAction)) {
            log.error("Action {} is not a SimpleAction", action.id());
            return false;
        }

        String command = hydrate(simpleAction.instruction(), simpleAction.data());
        log.info("Executing shell command for execution {}: {}", executionId, command);

        try {
            Process process = new ProcessBuilder("/bin/sh", "-c", command).start();

            // Capture output in separate threads to avoid hanging
            Thread.ofVirtual().start(() -> readStream(process.getInputStream(), "STDOUT", action.id()));
            Thread.ofVirtual().start(() -> readStream(process.getErrorStream(), "STDERR", action.id()));

            int exitCode = process.waitFor();
            log.info("Shell command {} exited with code {}", action.id(), exitCode);

            return exitCode == 0;
        } catch (Exception e) {
            log.error("Failed to execute shell action {}: {}", action.id(), e.getMessage());
            return false;
        }
    }

    private void readStream(java.io.InputStream is, String type, String actionId) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[{}] {}: {}", actionId, type, line);
            }
        } catch (Exception e) {
            log.error("Error reading {} for action {}: {}", type, actionId, e.getMessage());
        }
    }

    private String hydrate(String template, java.util.Map<String, String> data) {
        if (template == null) return null;
        String result = template;
        for (var entry : data.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
