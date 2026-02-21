package ru.petrov.gateway.service;

import ru.petrov.gateway.model.TaskMessageDto;

public interface TaskPublisher {
    void publish(TaskMessageDto message);
}
