package ru.petrov.ocr_gateway.model;

import java.util.Map;

/**
 * Сообщение от воркера с результатами обработки документа.
 * Содержит данные для создания записи в реестре файлов и обновления статуса задачи.
 *
 * @param taskId      ID задачи для мгновенного поиска и обновления в Postgres.
 * @param status      Результат обработки (SUCCESS, ERROR, FAILED).
 * @param storagePath Путь к результирующему TAR-архиву в бакете MinIO.
 * @param sha256      SHA-256 хеш архива для дедупликации и контроля целостности.
 * @param fileSize    Размер архива в байтах (необходим для логики Range Requests, скачивания частями).
 * @param metadata    Пакет дополнительных артефактов (версия движка и т.п.),
 *                    сохраняемый в историю задачи (stepsLog).
 */
public record TaskResultMessageDto(
        Long taskId,
        String status,
        String storagePath,
        String sha256,
        Long fileSize,
        Map<String, Object> metadata
) {}