package ru.petrov.gateway.service;

import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.petrov.gateway.model.FileEntity;
import ru.petrov.gateway.repository.FileRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService implements StorageService {
    private final MinioClient minioClient;
    private final FileRepository fileRepository;

    @Value("${app.minio.bucket-raw}")
    private String bucketRaw;

    @Value("${app.minio.bucket-proc}")
    private String bucketProc;

    @Value("${app.minio.expiry-min:15}") // Если в конфиге пусто, будет 15 минут
    private int expiryMin;

    @Value("${app.minio.external-url:http://localhost:9005}")
    private String externalUrl;

    @Value("${app.minio.endpoint}")
    private String internalEndpoint;

    @PostConstruct
    public void init() throws Exception {
        ensureBucketExists(bucketRaw);
        ensureBucketExists(bucketProc);
    }

    private void ensureBucketExists(String bucketName) throws Exception {
        boolean found = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );

        if (!found) {
            log.info("Инициализация хранилища: создание бакета [{}]", bucketName);
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build()
            );
        } else {
            log.debug("Бакет [{}] уже существует", bucketName);
        }
    }

    @Override
    public FileEntity uploadFile(MultipartFile file) {
        Path tempFile = null;
        try {
            // Создаем временный файл
            tempFile = Files.createTempFile("ocr_ingest_", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // ОДИН ПРОХОД: Пишем на диск + Считаем хеш
            try (InputStream is = file.getInputStream();
                 OutputStream os = Files.newOutputStream(tempFile);
                 DigestInputStream dis = new DigestInputStream(is, digest)) {
                dis.transferTo(os);
            }

            String hash = Hex.encodeHexString(digest.digest());

            // ДЕДУПЛИКАЦИЯ: Ищем в БД
            Optional<FileEntity> existing = fileRepository.findBySha256Hash(hash);
            if (existing.isPresent()) {
                return existing.get();
            }

            // Льем в MinIO (длинная сеть вне транзакции)
            String path = uploadToMinio(file, hash, tempFile);

            // Сохраняем в БД (короткий INSERT, транзакция внутри .save())
            FileEntity entity = new FileEntity();
            entity.setSha256Hash(hash);
            entity.setStoragePath(path);
            entity.setSizeBytes(file.getSize());
            entity.setMimeType(file.getContentType());

            return fileRepository.save(entity);


        } catch (Exception e) {
            log.error("Failed to process file upload", e);
            throw new RuntimeException("File processing error", e);
        } finally {
            // 4. ЧИСТКА: Удаляем временный файл
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String uploadToMinio(MultipartFile file, String hash, Path tempFile) throws Exception {
        String objectName = String.format("%s/%s", LocalDate.now(), hash);
        try (InputStream is = Files.newInputStream(tempFile)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketRaw)
                            .object(objectName)
                            .stream(is, file.getSize(), -1)
                            .contentType(file.getContentType())
                            // Добавляем хеш в метаданные объекта в MinIO
                            .userMetadata(Map.of("sha256", hash))
                            .build()
            );
        }
        return objectName;
    }

    @Override
    @Transactional
    public FileEntity registerResult(String storagePath, String sha256, Long fileSize) {
        // ПРОВЕРКА ДЕДУПЛИКАЦИИ
        // Ищем: вдруг такой результат (или идентичный файл) уже регистрировался
        return fileRepository.findBySha256Hash(sha256)
                .map(existingFile -> {
                    log.info("Результат с хэшем {} уже в реестре. Переиспользуем ID {}", sha256, existingFile.getId());
                    return existingFile;
                })
                .orElseGet(() -> {
                    // РЕГИСТРАЦИЯ НОВОГО
                    // Если хэш уникальный — верим воркеру и создаем запись
                    log.info("Регистрация нового файла-результата в БД: {}", sha256);
                    FileEntity newFile = new FileEntity();
                    newFile.setStoragePath(storagePath);
                    newFile.setSha256Hash(sha256);
                    newFile.setSizeBytes(fileSize);
                    newFile.setMimeType("application/x-tar"); // Мы знаем, что воркер выдает архивы

                    return fileRepository.save(newFile);
                });
    }

    @Override
    public String getDownloadUrl(FileEntity file, boolean isResult) {
        String targetBucket = isResult ? bucketProc : bucketRaw;
        try {
            // Генерируем Presigned URL
            // Ссылка будет содержать временную подпись доступа к конкретному объекту
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(targetBucket)
                            .object(file.getStoragePath())
                            .expiry(expiryMin, TimeUnit.MINUTES)
                            .build()
            );


            // Заменяем внутренний адрес контейнера (minio:9000) на внешний (localhost:9005).
            // Это позволяет браузеру на хосте скачать файл, в то время как Java-ядро
            // продолжает работать с MinIO по скоростной внутренней сети Docker.
            return presignedUrl.replace(internalEndpoint, externalUrl);
        } catch (Exception e) {
            log.error("Ошибка MinIO [Bucket: {}]: файл={}, причина={}",
                    targetBucket, file.getSha256Hash(), e.getMessage());
            throw new RuntimeException("Storage failure during URL generation", e);
        }
    }

    @Override
    public InputStream getPartialStream(FileEntity file, long offset, long length) {
        throw new UnsupportedOperationException("Partial stream not implemented yet");
    }

    @Override
    public void deleteFileSafely(FileEntity file) {
        log.info("Stub: deleteFileSafely requested for {}", file.getSha256Hash());
    }
}
