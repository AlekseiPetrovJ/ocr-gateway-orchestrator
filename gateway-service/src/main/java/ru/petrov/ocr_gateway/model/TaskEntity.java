package ru.petrov.ocr_gateway.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "tasks")
@Data
public class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false, unique = true)
    private String traceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity sourceFile;

    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "current_stage")
    private String currentStage; // NORMALIZE, OCR, etc.

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> config; // Профиль: { "profile": "high_acc", "lang": "rus" }

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> stepsLog; // История: [{"step": "ocr", "ms": 1200, "ver": "v2"}]

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_file_id")
    private FileEntity resultFile; // Ссылка на результат, несжатыый tar в MinIO

    private Integer userRating; // 1-5 для "золотого сета"

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime completedAt;

    // Вспомогательный метод для завершения задачи
    public void markCompleted(FileEntity resultFile) {
        this.resultFile = resultFile;
        this.status = TaskStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.currentStage = "FINISHED";
    }
}