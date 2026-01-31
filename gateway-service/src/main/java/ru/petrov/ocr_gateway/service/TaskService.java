package ru.petrov.ocr_gateway.service;

import org.springframework.web.multipart.MultipartFile;
import ru.petrov.ocr_gateway.model.TaskEntity;

import java.util.Map;

public interface TaskService {
    TaskEntity createAndDispatch(MultipartFile file, Map<String, Object> options);
}
