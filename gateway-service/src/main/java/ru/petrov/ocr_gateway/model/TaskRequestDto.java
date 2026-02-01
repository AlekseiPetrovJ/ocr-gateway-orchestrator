package ru.petrov.ocr_gateway.model;

import java.util.Map;

public record TaskRequestDto(
        String profile,
        Map<String, Object> options // Для гибких настроек
) {}