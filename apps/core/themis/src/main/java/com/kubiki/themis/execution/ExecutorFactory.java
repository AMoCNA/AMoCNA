package com.kubiki.themis.execution;

import com.kubiki.common.model.Protocol;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ExecutorFactory {
    private final List<ProtocolExecutor> executors;

    public Optional<ProtocolExecutor> getExecutor(Protocol protocol) {
        return executors.stream()
                .filter(e -> e.supports(protocol))
                .findFirst();
    }
}
