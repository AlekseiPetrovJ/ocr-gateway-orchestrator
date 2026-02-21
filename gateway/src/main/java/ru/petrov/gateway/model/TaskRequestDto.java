package ru.petrov.gateway.model;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Запрос пользователя на создание новой задачи обработки документа.
 * <p>
 * Содержит инструкции для Docling-воркера по глубине и специфике парсинга.
 *
 * @param profile Стратегия обработки документа.
 *                Доступные значения:
 *                "parse" (парсинг),
 *                "clean_parse" (очистка артефактов + парсинг),
 *                "norm_parse" (нормализация структуры + парсинг),
 *                "full" (очистка + нормализаця + парсинг).
 * @param options Карта дополнительных настроек для воркера (например, {"lang": "rus"}).
 */
public record TaskRequestDto(
        @NotNull(message = "Необходимо указать профиль обработки (например, parse)")
        ProcessingProfile profile,
        Map<String, Object> options // Для гибких настроек
) {}