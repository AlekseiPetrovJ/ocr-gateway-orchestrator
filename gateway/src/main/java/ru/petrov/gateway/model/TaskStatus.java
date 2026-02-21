package ru.petrov.gateway.model;

public enum TaskStatus {
    PENDING,    // В очереди
    PROCESSING, // В работе
    COMPLETED,  // Успех
    FAILED      // Ошибка
}
