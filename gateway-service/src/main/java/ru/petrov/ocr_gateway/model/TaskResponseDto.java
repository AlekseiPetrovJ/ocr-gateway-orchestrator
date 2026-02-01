package ru.petrov.ocr_gateway.model;

import java.time.LocalDateTime;

public record TaskResponseDto(
        Long taskId,
        String status,
        String fileHash,
        LocalDateTime createdAt
) {
}