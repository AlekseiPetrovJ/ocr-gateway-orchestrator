package ru.petrov.ocr_gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Облегченный статус задачи для частого опроса (polling) клиентом.
 *
 * @param taskId       Первичный ключ задачи для быстрого поиска в Postgres.
 * @param status       Текущее состояние задачи.
 *                     Принимает значения из {@link TaskStatus}.
 * @param currentStage Технический этап внутри воркера (например, "OCR", "CLEANUP").
 *                     Позволяет отображать детальный прогресс в интерфейсе.
 * @param sizeBytes    Заполняется при COMPLETED.
 * @param sha256       Заполняется при COMPLETED.
 */
public record TaskStatusResponseDto(
        Long taskId,
        TaskStatus status,
        String currentStage,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long sizeBytes,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String sha256
) {}