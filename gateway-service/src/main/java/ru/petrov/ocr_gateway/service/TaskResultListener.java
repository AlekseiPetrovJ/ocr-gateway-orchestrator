package ru.petrov.ocr_gateway.service;

import ru.petrov.ocr_gateway.model.TaskResultMessageDto;

public interface TaskResultListener {
    void handleTaskResult(TaskResultMessageDto result);
}