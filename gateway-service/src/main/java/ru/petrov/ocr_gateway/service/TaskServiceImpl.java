package ru.petrov.ocr_gateway.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.petrov.ocr_gateway.exception.TaskDispatchException;
import ru.petrov.ocr_gateway.model.*;
import ru.petrov.ocr_gateway.repository.TaskRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {
    private final StorageService storageService;
    private final TaskRepository taskRepository;
    private final TaskPublisher taskPublisher;
    private final AppTracer tracer;

    // TODO: Интегрировать систему профилей для заполнения дефолтных опций

    @Override
    public TaskEntity createAndDispatch(MultipartFile file, ProcessingProfile profile, Map<String, Object> options) {
        //Фиксируем время только для аналитики кэша
        LocalDateTime requestStartTime = LocalDateTime.now();
        //python worker хочет без дефисов
        String traceId = UUID.randomUUID().toString().replace("-", "");

        // 1. Приземляем файл (IO-bound, вне транзакции)
        FileEntity fileEntity = storageService.uploadFile(file);

        // Дедупликация задачи
        Optional<TaskEntity> cache = findCompletedCache(fileEntity, profile);

        if (cache.isPresent()) {
            TaskEntity cached = cache.get();
            log.info("Используем результат уже готовой задачи {}", cached.getId());

            TaskEntity newTask = new TaskEntity();
            newTask.setTraceId(traceId);
            newTask.setSourceFile(fileEntity);
            newTask.setProfile(profile);
            newTask.setConfig(options);
            newTask.setResultFile(cached.getResultFile());
            newTask.setStepsLog(cached.getStepsLog());
            newTask.markCompleted(cached.getResultFile());

            newTask.setCreatedAt(requestStartTime);

            tracer.startTaskTrace(traceId, "cache-hit:" + profile.getValue(), fileEntity.getSha256Hash());

            // А теперь открываем и ТУТ ЖЕ закрываем Спан, чтобы данные улетели
            try (var ignored = tracer.startSpan(traceId, "cache-deduplication")) {
                // Тут пусто, мы просто зашли и вышли, чтобы сработал close()
                log.debug("Tracing cache hit for {}", traceId);
            }

            return taskRepository.save(newTask);
        }
        // Затем логируем в Langfuse
        try (var ignored = tracer.startSpan(traceId, "minio-upload")) {
            tracer.startTaskTrace(traceId, file.getOriginalFilename(), fileEntity.getSha256Hash());
        }

        // 2. Создаем таску в БД (Атомарный save() создаст свою мини-транзакцию)
        TaskEntity task = new TaskEntity();
        task.setTraceId(traceId);
        task.setSourceFile(fileEntity);
        task.setProfile(profile);
        task.setStatus(TaskStatus.PENDING);
        task.setConfig(options);
        TaskEntity savedTask = taskRepository.save(task);

        // 3. Формирование DTO
        TaskMessageDto message = new TaskMessageDto(
                savedTask.getId(),
                fileEntity.getStoragePath(),
                traceId,
                fileEntity.getSha256Hash(),
                profile,
                savedTask.getConfig()
        );

        // 4. Отправка (Ретраи работают внутри этого вызова)
        try {
            taskPublisher.publish(message);
        } catch (Exception e) {
            log.error("Failed to dispatch task {} to RabbitMQ. Error: {}", savedTask.getId(), e.getMessage());

            // Бросаем наше статусное исключение (оно вернет 503)
            throw new TaskDispatchException("Сервис временно недоступен: не удалось отправить задачу в очередь", e);
        }
        return savedTask;
    }

    private Optional<TaskEntity> findCompletedCache(FileEntity file, ProcessingProfile profile) {
        return taskRepository.findFirstBySourceFileAndProfileAndStatusOrderByCreatedAtDesc(
                file, profile, TaskStatus.COMPLETED
        );
    }

    @Override
    @Transactional
    public void completeTask(TaskResultMessageDto result) {
        TaskEntity task = taskRepository.findByIdWithLock(result.taskId())
                .orElseThrow(() -> new EntityNotFoundException());

        // 2. Идемпотентность (если Rabbit прислал дубль)
        if (task.getStatus() == TaskStatus.COMPLETED) {
            log.warn("Задача {} уже была завершена ранее", task.getId());
            return;
        }

        FileEntity resultFile = storageService.registerResult(
                result.storagePath(),
                result.sha256(),
                result.fileSize()
        );

        task.setResultFile(resultFile);
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());

        // Передаем метаданные от воркера (stepsLog)
        if (result.metadata() != null) {
            task.setStepsLog(List.of(result.metadata()));
        }

        taskRepository.save(task);
        log.info("Задача {} финализирована. Привязан файл: {}", task.getId(), resultFile.getId());
    }
}
