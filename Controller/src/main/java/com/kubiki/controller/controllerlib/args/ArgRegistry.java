package com.kubiki.controller.controllerlib.args;

import com.kubiki.controller.commons.annotations.complex.ComplexAction;
import com.kubiki.controller.commons.annotations.simple.Action;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.reflections.Reflections;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This files contains Action name -> Action Arg Class mappings
 */

@Service
@RequiredArgsConstructor
public class ArgRegistry {
    private static final String PROVIDED_SOURCE = "com.kubiki.controller.commons.actions";
    private static final String USER_ACTION_SOURCE_LOCATION_NAME = "actions.source";

    private final Environment env;
    private Map<String, Class<?>> actionNamesToActionArgs;

    @PostConstruct
    private void setup() {
        actionNamesToActionArgs =
                Stream.concat(
                        scanPackageForActions(PROVIDED_SOURCE).entrySet().stream(),
                        getUserDefinedActions().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Class<?> getArgClass(String name) {
       return actionNamesToActionArgs.get(name);
    }

    private Map<String, Class<?>> getUserDefinedActions() {
        return Optional.ofNullable(env.getProperty(USER_ACTION_SOURCE_LOCATION_NAME))
                .map(this::scanPackageForActions)
                .orElse(Map.of());
    }

    private Map<String, Class<?>> scanPackageForActions(String source) {
        Map<String, Class<?>> regular = new Reflections(source)
                .getTypesAnnotatedWith(Action.class)
                .stream().map(t -> t.getAnnotation(Action.class))
                .collect(Collectors.toMap(Action::name, Action::argClass));
        Map<String, Class<?>> complex = new Reflections(source)
                .getTypesAnnotatedWith(ComplexAction.class)
                .stream().map(t -> t.getAnnotation(ComplexAction.class))
                .collect(Collectors.toMap(ComplexAction::name, ComplexAction::argClass));
        return Stream.concat(
                        regular.entrySet().stream(),
                        complex.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
