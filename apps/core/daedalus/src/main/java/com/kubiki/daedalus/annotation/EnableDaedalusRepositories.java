package com.kubiki.daedalus.annotation;

import com.kubiki.daedalus.spring.DaedalusAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(DaedalusAutoConfiguration.class)
public @interface EnableDaedalusRepositories {
    String[] basePackages() default {};
}
