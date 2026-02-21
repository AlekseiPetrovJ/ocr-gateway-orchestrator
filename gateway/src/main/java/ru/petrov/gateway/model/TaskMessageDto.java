package ru.petrov.gateway.model;

import java.util.Map;

/**
 * Сообщение (команда) для воркера на выполнение обработки файла.
 * Содержит все необходимые данные, чтобы воркер не обращался к основной БД.
 *
 * @param taskId      Первичный ключ задачи в БД для быстрого ответа воркера.
 * @param storagePath Путь к исходному файлу в хранилище MinIO.
 * @param traceId     Сквозной идентификатор для мониторинга (Langfuse, OpenTelemetry).
 * @param sha256      Контрольная сумма исходного файла для верификации воркером перед обработкой.
 * @param profile     Профиль ("parse", "clean_parse" и т.д.)
 * @param config      Конфигурация задачи, опции (язык, параметры OCR).
 */
public record TaskMessageDto(
        Long taskId,
        String storagePath,
        String traceId,
        String sha256,
        ProcessingProfile profile,
        Map<String, Object> config
) {}
