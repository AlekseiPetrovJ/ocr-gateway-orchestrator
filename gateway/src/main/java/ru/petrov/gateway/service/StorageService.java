package ru.petrov.gateway.service;

import org.springframework.web.multipart.MultipartFile;
import ru.petrov.gateway.model.FileEntity;

import java.io.InputStream;

public interface StorageService {
    /**
     * Загрузка входящего файла.
     * <p>
     * Выполняет: расчет хеша, проверку на существование в БД (дедупликация),
     * сохранение во временный файл и заливку в объектное хранилище.
     *
     * @param file объект MultipartFile из контроллера
     * @return FileEntity существующая или новая запись в реестре
     */
    FileEntity uploadFile(MultipartFile file);

    /**
     * Удаление с проверкой дедупликации.
     * <p>
     * ПРАВИЛО: Физическое удаление из MinIO и зануление storagePath
     * происходит ТОЛЬКО если на этот FileEntity больше не ссылается
     * ни одна живая задача в БД.
     *
     * @param file сущность файла для очистки
     */
    void deleteFileSafely(FileEntity file);

    /**
     * Генерация временной ссылки (Presigned URL).
     *
     * @param file     сущность файла
     * @param isResult true, если файл из бакета результатов, false - из исходников
     * @return подписанный URL
     */
    String getDownloadUrl(FileEntity file, boolean isResult);

    /**
     * Чтение части байтов (Range Request).
     * Основа для умного парсинга архивов без скачивания целиком.
     */
    InputStream getPartialStream(FileEntity file, long offset, long length);

    /**
     * Регистрация результата обработки от воркера.
     * <p>
     * В отличие от uploadFile, этот метод НЕ скачивает файл из хранилища,
     * а фиксирует метаданные на основе отчета воркера.
     * <p>
     * <b>Гарантии:</b>
     * <ul>
     *     <li>Идемпотентность: повторный вызов с тем же хешем не создает дубликат в БД.</li>
     *     <li>Дедупликация: если идентичный результат уже был зарегистрирован другой задачей,
     *     будет возвращена существующая сущность FileEntity.</li>
     * </ul>
     *
     * @param storagePath путь к объекту в MinIO (из отчета воркера)
     * @param sha256      хеш-сумма финального архива
     * @param fileSize    размер архива в байтах
     * @return FileEntity (новая или существующая запись из реестра)
     */
    FileEntity registerResult(String storagePath, String sha256, Long fileSize);
}