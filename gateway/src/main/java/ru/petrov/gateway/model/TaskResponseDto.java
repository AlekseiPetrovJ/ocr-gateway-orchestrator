package ru.petrov.gateway.model;

import java.time.LocalDateTime;

/**
 * Мгновенный ответ системы, подтверждающий успешный прием файла и создание задачи.
 * Служит квитанцией для последующего отслеживания статуса обработки.
 *
 * @param taskId    Первичный ключ созданной задачи в базе данных (используется для поллинга).
 * @param status    Начальный статус задачи (обычно "PENDING" или "PROCESSING").
 * @param fileHash  SHA-256 хеш загруженного файла (подтверждение целостности и дедупликации).
 * @param createdAt Точное время регистрации задачи в системе.
 */
public record TaskResponseDto(
        Long taskId,
        TaskStatus status,
        String fileHash,
        LocalDateTime createdAt
) {}