package com.kubiki.daedalus.spring;

import com.kubiki.daedalus.annotation.DaedalusRepository;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.core.Formatter;
import com.kubiki.daedalus.proxy.DaedalusInvocationHandler;
import lombok.SneakyThrows;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Proxy;

public class DaedalusBeanRegistrar {

    @SneakyThrows
    public void registerRepositories(BeanDefinitionRegistry registry, GlobalTemplateContext context, String basePackage) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(DaedalusRepository.class));

        for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
            Class<?> clazz = Class.forName(bd.getBeanClassName());
            String beanName = clazz.getSimpleName();
            if (registry.containsBeanDefinition(beanName)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Class<Object> repoClass = (Class<Object>) clazz;

            registry.registerBeanDefinition(beanName, BeanDefinitionBuilder.genericBeanDefinition(repoClass, () -> {
                GlobalTemplateContext effectiveContext = context;
                Formatter effectiveFormatter = null;
                org.eclipse.rdf4j.repository.Repository effectiveRepository = null;
                if (registry instanceof BeanFactory bf) {
                    if (effectiveContext == null) {
                        try {
                            effectiveContext = bf.getBean(GlobalTemplateContext.class);
                        } catch (Exception e) {
                            effectiveContext = (GlobalTemplateContext) bf.getBean(GlobalTemplateContext.class.getName());
                        }
                    }
                    try {
                        effectiveFormatter = bf.getBean(Formatter.class);
                    } catch (Exception e) {
                        effectiveFormatter = (Formatter) bf.getBean(Formatter.class.getName());
                    }
                    try {
                        effectiveRepository = bf.getBean(org.eclipse.rdf4j.repository.Repository.class);
                    } catch (Exception e) {
                        // Repository might be missing if using Daedalus only for templating
                    }
                }
                return Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new DaedalusInvocationHandler(clazz, effectiveContext, effectiveFormatter, effectiveRepository));
            }).getBeanDefinition());
        }
    }
}
