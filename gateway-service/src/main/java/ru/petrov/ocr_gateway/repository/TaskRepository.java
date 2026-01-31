package ru.petrov.ocr_gateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.petrov.ocr_gateway.model.TaskEntity;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

}
