package ru.petrov.ocr_gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ru.petrov.ocr_gateway.config.RabbitProperties;
import ru.petrov.ocr_gateway.model.TaskMessageDto;

@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitTaskPublisher implements TaskPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties rabbitProperties;

    @Override
    public void publish(TaskMessageDto message) {
        log.info("Publishing task {} to exchange: {}, routingKey: {}",
                message.taskId(),
                rabbitProperties.exchange(),
                rabbitProperties.routingKey());

        rabbitTemplate.convertAndSend(
                rabbitProperties.exchange(),
                rabbitProperties.routingKey(),
                message
        );
    }
}
