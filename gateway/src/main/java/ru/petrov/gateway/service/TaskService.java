package ru.petrov.gateway.service;

import org.springframework.web.multipart.MultipartFile;
import ru.petrov.gateway.model.ProcessingProfile;
import ru.petrov.gateway.model.TaskEntity;
import ru.petrov.gateway.model.TaskResultMessageDto;
import ru.petrov.gateway.model.TaskStatusResponseDto;

import java.util.Map;

public interface TaskService {
    TaskEntity createAndDispatch(MultipartFile file, ProcessingProfile profile, Map<String, Object> options);
    void completeTask(TaskResultMessageDto result);
    String getResultDownloadUrl(Long taskId);
    TaskStatusResponseDto getTaskStatus(Long taskId);
}
