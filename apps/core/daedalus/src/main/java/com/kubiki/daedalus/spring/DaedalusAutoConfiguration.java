package com.kubiki.daedalus.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.daedalus.annotation.EnableDaedalusRepositories;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.core.DaedalusHydrator;
import com.kubiki.daedalus.core.DefaultDaedalusHydrator;
import com.kubiki.daedalus.core.Formatter;
import com.kubiki.daedalus.core.TemplateParser;
import com.kubiki.daedalus.core.format.CollectionFormatter;
import com.kubiki.daedalus.core.format.IriFormatter;
import com.kubiki.daedalus.core.format.LiteralFormatter;
import com.kubiki.daedalus.core.format.PlainFormatter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.kubiki.daedalus.core.DaedalusConstants.DEFAULT_BASE_PACKAGE;

@Configuration
public class DaedalusAutoConfiguration implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(GlobalTemplateContext.class.getName())) {
            registry.registerBeanDefinition(GlobalTemplateContext.class.getName(),
                    BeanDefinitionBuilder.genericBeanDefinition(GlobalTemplateContext.class).getBeanDefinition());
        }

        if (!registry.containsBeanDefinition(ObjectMapper.class.getName())) {
            registry.registerBeanDefinition(ObjectMapper.class.getName(),
                    BeanDefinitionBuilder.genericBeanDefinition(ObjectMapper.class).getBeanDefinition());
        }

        if (!registry.containsBeanDefinition(TemplateParser.class.getName())) {
            registry.registerBeanDefinition(TemplateParser.class.getName(),
                    BeanDefinitionBuilder.genericBeanDefinition(TemplateParser.class).getBeanDefinition());
        }

        // Register default formatters
        registerFormatter(registry, IriFormatter.class);
        registerFormatter(registry, LiteralFormatter.class);
        registerFormatter(registry, PlainFormatter.class);
        registerFormatter(registry, CollectionFormatter.class);

        if (!registry.containsBeanDefinition(Formatter.class.getName())) {
            registry.registerBeanDefinition(Formatter.class.getName(),
                    BeanDefinitionBuilder.genericBeanDefinition(Formatter.class).getBeanDefinition());
        }

        if (!registry.containsBeanDefinition(DaedalusHydrator.class.getName())) {
            registry.registerBeanDefinition(DaedalusHydrator.class.getName(),
                    BeanDefinitionBuilder.genericBeanDefinition(DefaultDaedalusHydrator.class).getBeanDefinition());
        }

        if (!registry.containsBeanDefinition(com.kubiki.daedalus.knowledge.SparqlClient.class.getName())) {
            AbstractBeanDefinition bd = BeanDefinitionBuilder.genericBeanDefinition(com.kubiki.daedalus.knowledge.SparqlClient.class).getBeanDefinition();
            bd.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
            registry.registerBeanDefinition(com.kubiki.daedalus.knowledge.SparqlClient.class.getName(), bd);
        }

        Set<String> basePackages = getBasePackages(importingClassMetadata);
        if (basePackages.isEmpty()) {
            basePackages.add(DEFAULT_BASE_PACKAGE);
        }

        DaedalusBeanRegistrar registrar = new DaedalusBeanRegistrar();
        for (String basePackage : basePackages) {
            registrar.registerRepositories(registry, null, basePackage);
        }
    }

    private Set<String> getBasePackages(AnnotationMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                metadata.getAnnotationAttributes(EnableDaedalusRepositories.class.getName()));
        if (attributes != null) {
            return new HashSet<>(Arrays.asList(attributes.getStringArray("basePackages")));
        }
        return new HashSet<>();
    }

    private void registerFormatter(BeanDefinitionRegistry registry, Class<?> formatterClass) {
        if (!registry.containsBeanDefinition(formatterClass.getName())) {
            registry.registerBeanDefinition(formatterClass.getName(),
                    BeanDefinitionBuilder.genericBeanDefinition(formatterClass).getBeanDefinition());
        }
    }
}
