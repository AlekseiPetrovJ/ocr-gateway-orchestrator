package ru.petrov.ocr_gateway.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "files")
@Data
@EntityListeners(AuditingEntityListener.class)
public class FileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @NotBlank
    @Column(name = "sha256_hash", nullable = false, unique = true, length = 64)
    private String sha256Hash;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
