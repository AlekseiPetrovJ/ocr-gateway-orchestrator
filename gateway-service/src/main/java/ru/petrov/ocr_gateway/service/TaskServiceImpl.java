package ru.petrov.ocr_gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.petrov.ocr_gateway.exception.TaskDispatchException;
import ru.petrov.ocr_gateway.model.FileEntity;
import ru.petrov.ocr_gateway.model.TaskEntity;
import ru.petrov.ocr_gateway.model.TaskMessageDto;
import ru.petrov.ocr_gateway.model.TaskStatus;
import ru.petrov.ocr_gateway.repository.TaskRepository;

import javax.naming.ServiceUnavailableException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {
    private final StorageService storageService;
    private final TaskRepository taskRepository;
    private final TaskPublisher taskPublisher;


    @Override
    public TaskEntity createAndDispatch(MultipartFile file, Map<String, Object> options) {

        // 1. Приземляем файл (IO-bound, вне транзакции)
        FileEntity fileEntity = storageService.uploadFile(file);

        // 2. Создаем таску в БД (Атомарный save() создаст свою мини-транзакцию)
        TaskEntity task = new TaskEntity();
        task.setFile(fileEntity);
        task.setStatus(TaskStatus.PENDING);
        task.setConfig(options);
        TaskEntity savedTask = taskRepository.save(task);

        // 3. Формирование DTO
        TaskMessageDto message = new TaskMessageDto(
                savedTask.getId(),
                fileEntity.getStoragePath(),
                fileEntity.getSha256Hash(),
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

}
