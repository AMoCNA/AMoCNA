package com.kubiki.metis.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ infrastructure for Metis → Palamedes graph-update notifications.
 *
 * <p>Declares the {@code amocna.graph.updates} queue bound to the shared
 * {@code amocna.topic.exchange} with routing key pattern {@code graph.updates.*}.
 * Palamedes (or any other consumer) listens on this queue.
 */
@Configuration
public class RabbitMQConfig {

    public static final String GRAPH_UPDATES_QUEUE = "amocna.graph.updates";
    public static final String VULNERABILITY_UPDATES_QUEUE = "amocna.vulnerability.updates";
    public static final String EXCHANGE = "amocna.topic.exchange";
    public static final String ROUTING_KEY = "graph.updates.metis";

    @Bean
    public Queue graphUpdatesQueue() {
        return new Queue(GRAPH_UPDATES_QUEUE, true);
    }

    @Bean
    public Queue vulnerabilityUpdatesQueue() {
        return new Queue(VULNERABILITY_UPDATES_QUEUE, true);
    }

    @Bean
    public TopicExchange amocnaExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Binding graphUpdatesBinding(Queue graphUpdatesQueue, TopicExchange amocnaExchange) {
        return BindingBuilder.bind(graphUpdatesQueue).to(amocnaExchange).with("graph.updates.*");
    }

    @Bean
    public Binding vulnerabilityUpdatesBinding(Queue vulnerabilityUpdatesQueue, TopicExchange amocnaExchange) {
        return BindingBuilder.bind(vulnerabilityUpdatesQueue).to(amocnaExchange).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
