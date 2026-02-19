package ru.petrov.ocr_gateway.service;

import org.springframework.web.multipart.MultipartFile;
import ru.petrov.ocr_gateway.model.ProcessingProfile;
import ru.petrov.ocr_gateway.model.TaskEntity;
import ru.petrov.ocr_gateway.model.TaskResultMessageDto;
import ru.petrov.ocr_gateway.model.TaskStatusResponseDto;

import java.util.Map;

public interface TaskService {
    TaskEntity createAndDispatch(MultipartFile file, ProcessingProfile profile, Map<String, Object> options);
    void completeTask(TaskResultMessageDto result);
    String getResultDownloadUrl(Long taskId);
    TaskStatusResponseDto getTaskStatus(Long taskId);
}
