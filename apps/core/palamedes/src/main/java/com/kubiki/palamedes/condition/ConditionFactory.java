package com.kubiki.palamedes.condition;

import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConditionFactory {
    private final List<ConditionStrategy> strategies;

    public Optional<ConditionStrategy> getStrategy(IRI conditionType) {
        return strategies.stream()
                .filter(s -> s.supports(conditionType))
                .findFirst();
    }
}
