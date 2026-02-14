package ru.petrov.ocr_gateway.model;

/**
 * Итоговый результат выполнения задачи.
 *
 * @param taskId      ID задачи для идентификации.
 * @param downloadUrl Временная подписанная ссылка на TAR-архив в MinIO.
 * @param sizeBytes    Размер архива в байтах (чтобы юзер знал, что качает).
 * @param sha256      Контрольная сумма для проверки целостности на клиенте.
 */
public record TaskResultDto(
        Long taskId,
        String downloadUrl,
        Long sizeBytes,
        String sha256
) {}