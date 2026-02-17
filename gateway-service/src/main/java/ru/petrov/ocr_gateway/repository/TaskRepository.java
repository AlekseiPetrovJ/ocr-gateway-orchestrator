package ru.petrov.ocr_gateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.petrov.ocr_gateway.model.FileEntity;
import ru.petrov.ocr_gateway.model.ProcessingProfile;
import ru.petrov.ocr_gateway.model.TaskEntity;
import ru.petrov.ocr_gateway.model.TaskStatus;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
    Optional<TaskEntity> findFirstBySourceFileAndProfileAndStatusOrderByCreatedAtDesc(
            FileEntity sourceFile,
            ProcessingProfile profile,
            TaskStatus status
    );
}
