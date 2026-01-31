package ru.petrov.ocr_gateway.model;

import java.util.Map;

public record TaskMessageDto(
        Long taskId,         // ID для обновления статуса в БД
        String storagePath,  // Путь в MinIO
        String sha256,       // Для проверки целостности воркером
        Map<String, Object> config // Профиль (lang, engine, etc.)
) {}
