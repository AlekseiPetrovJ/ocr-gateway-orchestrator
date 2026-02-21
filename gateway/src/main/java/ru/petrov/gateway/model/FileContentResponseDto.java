package ru.petrov.gateway.model;

/**
 * Контейнер для передачи содержимого конкретного файла из архива.
 * Используется при частичном просмотре (например, отображение Markdown).
 *
 * @param fileName    Имя оригинального файла (например, "summary.md").
 * @param contentType MIME-тип контента (text/markdown, image/png и т.д.).
 * @param sizeBytes   Размер фрагмента в байтах.
 * @param content     Сырые байты файла (Base64 или бинарный поток).
 */
public record FileContentResponseDto(
        String fileName,
        String contentType,
        long sizeBytes,
        byte[] content
) {}