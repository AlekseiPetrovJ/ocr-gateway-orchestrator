package ru.petrov.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public record RabbitProperties(
        String exchange,
        String routingKey,
        Queues queues
) {
    public record Queues(
            String incoming,
            String results
    ) {}
}