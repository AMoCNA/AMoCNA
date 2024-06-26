package com.kubiki.controller.controllerlib.experimental.rabbitmq;

import lombok.NoArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@NoArgsConstructor
@ConditionalOnProperty(name = "actions.rabbit.enabled", havingValue = "true")
public class RabbitMQConfig {

    @Value("${actions.rabbitmq.exchange:kubiki.actions.exchange}")
    private String rabbitExchangeName;
    @Value("${actions.rabbitmq.queue:q.kubiki.actions}")
    private String rabbitQueueName;

    @Bean
    Queue createQueue() {
        return new Queue(rabbitQueueName);
    }

    @Bean
    CustomExchange createExchange() {
        return new CustomExchange(rabbitExchangeName, "x-delayed-message",
                true, false, Map.of("x-delayed-type", "direct"));
    }

    @Bean
    Binding bindQueueToExchange() {
        return BindingBuilder.bind(createQueue()).to(createExchange()).with(rabbitQueueName).noargs();
    }
}