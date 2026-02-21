package ru.petrov.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RabbitProperties.class)
public class RabbitConfig {

    private final RabbitProperties properties;

    // 1. Создаем обменник (точка входа для сообщений)
    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(properties.exchange());
    }

    // 2. Создаем очередь (где сообщения будут лежать до прихода воркера)
    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(properties.queues().incoming())
                .build();
    }

    // 3. Соединяем их (Binding) по ключу
    @Bean
    public Binding taskBinding(Queue taskQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(taskQueue)
                .to(taskExchange)
                .with(properties.routingKey());
    }

    // 4. Настройка JSON-конвертера (чтобы Record превращался в JSON автоматически)
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}