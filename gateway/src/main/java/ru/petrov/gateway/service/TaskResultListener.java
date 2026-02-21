package ru.petrov.gateway.service;

import ru.petrov.gateway.model.TaskResultMessageDto;

public interface TaskResultListener {
    void handleTaskResult(TaskResultMessageDto result);
}