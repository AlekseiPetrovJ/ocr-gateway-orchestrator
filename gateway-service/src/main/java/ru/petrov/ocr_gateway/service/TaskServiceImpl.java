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

        // Приземляем файл (IO-bound, вне транзакции)
        FileEntity fileEntity = storageService.uploadFile(file);

        // Дедупликация задачи
        Optional<TaskEntity> cache = findCache(fileEntity, profile, List.of(TaskStatus.COMPLETED));

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

        // Умное ожидание: ищем тех, кто уже в процессе обработки
        Optional<TaskEntity> activeTask = findCache(fileEntity, profile, List.of(TaskStatus.PENDING, TaskStatus.PROCESSING));

        if (activeTask.isPresent()) {
            TaskEntity active = activeTask.get();
            log.info("Присоседились к активной задаче {}. Ждем завершения.", active.getId());

            TaskEntity newTask = new TaskEntity();
            newTask.setTraceId(traceId);
            newTask.setSourceFile(fileEntity);
            newTask.setProfile(profile);
            newTask.setConfig(options);
            newTask.setCreatedAt(requestStartTime);

            newTask.setStatus(active.getStatus());

            tracer.startTaskTrace(traceId, "active-wait:" + profile.getValue(), fileEntity.getSha256Hash());

            // В RabbitMQ НЕ ШЛЕМ! Просто сохраняем в базу.
            return taskRepository.save(newTask);
        }

        // Затем логируем в Langfuse
        try (var ignored = tracer.startSpan(traceId, "minio-upload")) {
            tracer.startTaskTrace(traceId, file.getOriginalFilename(), fileEntity.getSha256Hash());
        }

        // Создаем таску в БД (Атомарный save() создаст свою мини-транзакцию)
        TaskEntity task = new TaskEntity();
        task.setTraceId(traceId);
        task.setSourceFile(fileEntity);
        task.setProfile(profile);
        task.setStatus(TaskStatus.PENDING);
        task.setConfig(options);
        TaskEntity savedTask = taskRepository.save(task);

        // Формирование DTO
        TaskMessageDto message = new TaskMessageDto(
                savedTask.getId(),
                fileEntity.getStoragePath(),
                traceId,
                fileEntity.getSha256Hash(),
                profile,
                savedTask.getConfig()
        );

        // Отправка (Ретраи работают внутри этого вызова)
        try {
            taskPublisher.publish(message);
        } catch (Exception e) {
            log.error("Failed to dispatch task {} to RabbitMQ. Error: {}", savedTask.getId(), e.getMessage());

            // Бросаем наше статусное исключение (оно вернет 503)
            throw new TaskDispatchException("Сервис временно недоступен: не удалось отправить задачу в очередь", e);
        }
        return savedTask;
    }

    private Optional<TaskEntity> findCache(FileEntity file, ProcessingProfile profile, List<TaskStatus> statuses) {
        return taskRepository.findFirstBySourceFileAndProfileAndStatusInOrderByIdDesc(file, profile, statuses);
    }

    @Override
    @Transactional
    public void completeTask(TaskResultMessageDto result) {
        TaskEntity pioneer = taskRepository.findByIdWithLock(result.taskId())
                .orElseThrow(() -> new EntityNotFoundException("Pioneer task not found: " + result.taskId()));

        // Идемпотентность (если Rabbit прислал дубль)
        if (pioneer.getStatus() == TaskStatus.COMPLETED) {
            log.warn("Задача {} уже была завершена ранее", pioneer.getId());
            return;
        }

        FileEntity resultFile = storageService.registerResult(
                result.storagePath(),
                result.sha256(),
                result.fileSize()
        );

        pioneer.setResultFile(resultFile);
        pioneer.setStatus(TaskStatus.COMPLETED);
        pioneer.setCompletedAt(LocalDateTime.now());

        // Передаем метаданные от воркера (stepsLog)
        if (result.metadata() != null) {
            pioneer.setStepsLog(List.of(result.metadata()));
        }

        taskRepository.save(pioneer);
        // Ищем всех, кто присоседился к этому хэшу и профилю
        List<TaskEntity> followers = taskRepository.findAllBySourceFileAndProfileAndStatusInAndIdNot(
                pioneer.getSourceFile(),
                pioneer.getProfile(),
                List.of(TaskStatus.PENDING, TaskStatus.PROCESSING),
                pioneer.getId() // Кроме самого лидера
        );

        if (!followers.isEmpty()) {
            log.info("Коллективный финиш: закрываем еще {} задач для файла {}",
                    followers.size(), pioneer.getSourceFile().getSha256Hash());

            followers.forEach(follower -> {
                follower.setResultFile(resultFile);
                follower.setStatus(TaskStatus.COMPLETED);
                follower.setCompletedAt(LocalDateTime.now());
                if (result.metadata() != null) {
                    follower.setStepsLog(List.of(result.metadata()));
                }
            });
            taskRepository.saveAll(followers);
        }

        log.info("Всего задач финализировано: {}. Привязан файл: {}",
                followers.size() + 1, resultFile.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public TaskStatusResponseDto getTaskStatus(Long taskId) {
        TaskEntity task = findTaskById(taskId);

        if (task.getStatus() != TaskStatus.COMPLETED) {
            return new TaskStatusResponseDto(task.getId(), task.getStatus(), task.getCurrentStage(), null, null);
        }

        FileEntity file = ensureResultReady(task);

        return new TaskStatusResponseDto(
                task.getId(), task.getStatus(), task.getCurrentStage(),
                file.getSizeBytes(), file.getSha256Hash()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public String getResultDownloadUrl(Long taskId) {
        TaskEntity task = findTaskById(taskId);
        FileEntity file = ensureResultReady(task);
        log.info("Выдача доступа к результату задачи {}", taskId);
        return storageService.getDownloadUrl(file, true);
    }

    private FileEntity ensureResultReady(TaskEntity task) {
        if (task.getStatus() != TaskStatus.COMPLETED) {
            throw new IllegalStateException("Результат еще не готов. Текущий статус: " + task.getStatus());
        }

        FileEntity resultFile = task.getResultFile();
        if (resultFile == null) {
            throw new IllegalStateException("Критическая ошибка целостности: статус COMPLETED, но файл не привязан. TaskID: " + task.getId());
        }
        return resultFile;
    }

    private TaskEntity findTaskById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Задача с ID " + taskId + " не найдена"));
    }
}
