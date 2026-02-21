package ru.petrov.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.petrov.gateway.model.TaskResultMessageDto;

@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitTaskResultListener implements TaskResultListener{
    private final TaskService taskService;
    private final AppTracer tracer;

    @Override
    @RabbitListener(queues = "${app.rabbitmq.queues.results}")
    public void handleTaskResult(TaskResultMessageDto message) {
        try (var ignored = tracer.startSpan(message.traceId(), "gateway-finalize")) {
            log.info("Получен результат задачи {} для трейса {}", message.taskId(), message.traceId());
            taskService.completeTask(message);
        } catch (Exception e) {
            log.error("Ошибка финализации задачи {}: {}", message.taskId(), e.getMessage());
            tracer.sendError(message.traceId(), "FinalizeFailed", e.getMessage());
            // Важно: пробрасываем дальше, чтобы RabbitMQ не удалил сообщение
            throw e;
        }
    }
}