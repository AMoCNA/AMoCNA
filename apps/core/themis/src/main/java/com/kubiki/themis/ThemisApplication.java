package com.kubiki.themis;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.daedalus.annotation.EnableDaedalusRepositories;
import com.kubiki.themis.config.ThemisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication(scanBasePackages = {"com.kubiki.themis", "com.kubiki.common"})
@EnableRetry
@EnableConfigurationProperties({ThemisProperties.class, AmocnaCommonProperties.class})
@EnableDaedalusRepositories(basePackages = "com.kubiki.themis.policy")
public class ThemisApplication {
    static void main(String[] args) {
        SpringApplication.run(ThemisApplication.class, args);
    }
}
