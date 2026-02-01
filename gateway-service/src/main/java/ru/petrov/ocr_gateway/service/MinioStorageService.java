package ru.petrov.ocr_gateway.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.petrov.ocr_gateway.model.FileEntity;
import ru.petrov.ocr_gateway.repository.FileRepository;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService implements StorageService {
    private final MinioClient minioClient;
    private final FileRepository fileRepository;

    @Value("${app.minio.bucket}")
    private String bucket;

    @PostConstruct
    public void init() throws Exception {
        // Создаем бакет при старте, если его нет
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    @Override
    public FileEntity uploadFile(MultipartFile file) {
        Path tempFile = null;
        try {
            // 1. Создаем временный файл
            tempFile = Files.createTempFile("ocr_ingest_", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 2. ОДИН ПРОХОД: Пишем на диск + Считаем хеш
            try (InputStream is = file.getInputStream();
                 OutputStream os = Files.newOutputStream(tempFile);
                 DigestInputStream dis = new DigestInputStream(is, digest)) {
                dis.transferTo(os);
            }

            String hash = Hex.encodeHexString(digest.digest());

            // 3. ДЕДУПЛИКАЦИЯ: Ищем в БД
            Optional<FileEntity> existing = fileRepository.findBySha256Hash(hash);
            if (existing.isPresent()) {
                return existing.get();
            }

            // 2. Льем в MinIO (длинная сеть вне транзакции)
            String path = uploadToMinio(file, hash, tempFile);

            // 3. Сохраняем в БД (короткий INSERT, транзакция внутри .save())
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
                            .bucket(bucket)
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
}
