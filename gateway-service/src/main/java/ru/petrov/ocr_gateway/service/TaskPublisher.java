package ru.petrov.ocr_gateway.service;

import ru.petrov.ocr_gateway.model.TaskMessageDto;

public interface TaskPublisher {
    void publish(TaskMessageDto message);
}
