package com.kubiki.palamedes.scig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(name = "palamedes.scig.enabled", havingValue = "true")
public class ScigRedisConfig {

    @Bean
    LettuceConnectionFactory scigRedisConnectionFactory(
            @Value("${palamedes.scig.redis-host:localhost}") String host,
            @Value("${palamedes.scig.redis-port:6379}") int port) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory scigRedisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(scigRedisConnectionFactory);
        return template;
    }
}
