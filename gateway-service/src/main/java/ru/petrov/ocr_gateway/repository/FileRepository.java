package ru.petrov.ocr_gateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.petrov.ocr_gateway.model.FileEntity;

import java.util.Optional;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
    Optional<FileEntity> findBySha256Hash(String sha256Hash);
}
