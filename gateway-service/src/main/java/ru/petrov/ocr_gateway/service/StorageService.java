package ru.petrov.ocr_gateway.service;

import org.springframework.web.multipart.MultipartFile;
import ru.petrov.ocr_gateway.model.FileEntity;

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
     * Генерация временной ссылки (Presigned URL) для прямого скачивания.
     * <p>
     * Метод создает подписанный URL, который позволяет клиенту забирать
     * файл напрямую из хранилища (MinIO/Nginx) в обход ресурсов Ядра.
     * <p>
     * Ссылка имеет ограниченный срок жизни (TTL), что предотвращает
     * несанкционированный доступ при перехвате URL.
     *
     * @param file сущность файла из реестра, для которого генерируется доступ
     * @return строка с полным URL и криптографической подписью доступа
     */
    String getDownloadUrl(FileEntity file);

    /**
     * Чтение части байтов (Range Request).
     * Основа для умного парсинга архивов без скачивания целиком.
     */
    InputStream getPartialStream(FileEntity file, long offset, long length);
}