package com.kubiki.palamedes.config;

public record QueryDefinition(
        String name,
        String nodeQueryTemplate,
        String fallbackQuery
) {
}
