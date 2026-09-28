package com.kubiki.palamedes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ACTION_QUEUE = "amocna.action.queue";
    public static final String STATUS_QUEUE = "amocna.status.queue";
    public static final String GRAPH_UPDATES_QUEUE = "amocna.graph.updates";
    public static final String VULNERABILITY_UPDATES_QUEUE = "amocna.vulnerability.updates";
    public static final String EXCHANGE = "amocna.direct.exchange";
    public static final String TOPIC_EXCHANGE = "amocna.topic.exchange";
    public static final String ACTION_ROUTING_KEY = "action";
    public static final String STATUS_ROUTING_KEY = "status";
    public static final String GRAPH_UPDATES_ROUTING_KEY_PATTERN = "graph.updates.*";
    public static final String VULNERABILITY_ROUTING_KEY = "graph.updates.metis";

    @Bean
    public Queue actionQueue() {
        return QueueBuilder.durable(ACTION_QUEUE)
                .maxPriority(10)
                .build();
    }

    @Bean
    public Queue statusQueue() {
        return new Queue(STATUS_QUEUE);
    }

    @Bean
    public Queue graphUpdatesQueue() {
        return new Queue(GRAPH_UPDATES_QUEUE, true);
    }

    @Bean
    public Queue vulnerabilityUpdatesQueue() {
        return new Queue(VULNERABILITY_UPDATES_QUEUE, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE);
    }

    @Bean
    public Binding actionBinding(Queue actionQueue, DirectExchange exchange) {
        return BindingBuilder.bind(actionQueue).to(exchange).with(ACTION_ROUTING_KEY);
    }

    @Bean
    public Binding statusBinding(Queue statusQueue, DirectExchange exchange) {
        return BindingBuilder.bind(statusQueue).to(exchange).with(STATUS_ROUTING_KEY);
    }

    @Bean
    public Binding graphUpdatesBinding(Queue graphUpdatesQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(graphUpdatesQueue).to(topicExchange).with(GRAPH_UPDATES_ROUTING_KEY_PATTERN);
    }

    @Bean
    public Binding vulnerabilityUpdatesBinding(Queue vulnerabilityUpdatesQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(vulnerabilityUpdatesQueue).to(topicExchange).with(VULNERABILITY_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }
}
