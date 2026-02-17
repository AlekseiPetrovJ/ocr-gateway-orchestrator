package ru.petrov.ocr_gateway.service;

import org.springframework.web.multipart.MultipartFile;
import ru.petrov.ocr_gateway.model.ProcessingProfile;
import ru.petrov.ocr_gateway.model.TaskEntity;

import java.util.Map;

public interface TaskService {
    TaskEntity createAndDispatch(MultipartFile file, ProcessingProfile profile, Map<String, Object> options);
}
