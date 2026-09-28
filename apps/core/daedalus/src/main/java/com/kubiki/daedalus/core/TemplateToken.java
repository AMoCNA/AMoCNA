package com.kubiki.daedalus.core;

public sealed interface TemplateToken {
    record StaticToken(String text) implements TemplateToken {
    }

    record VariableToken(String name, String defaultValue) implements TemplateToken {
    }
}
