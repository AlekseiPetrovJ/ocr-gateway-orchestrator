package ru.petrov.ocr_gateway.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.petrov.ocr_gateway.model.TaskEntity;
import ru.petrov.ocr_gateway.model.TaskRequestDto;
import ru.petrov.ocr_gateway.model.TaskResponseDto;
import ru.petrov.ocr_gateway.service.TaskService;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TaskResponseDto> processFile(
            @RequestPart("file") MultipartFile file,
            @RequestPart("request") TaskRequestDto request) {
        // 1. Валидация (PDF/Image)

        // Прокидываем в сервис файл и мапу опций из DTO
        TaskEntity task = taskService.createAndDispatch(file, request.profile(), request.options());

        return ResponseEntity.ok(new TaskResponseDto(
                task.getId(),
                task.getStatus(),
                task.getSourceFile().getSha256Hash(),
                task.getCreatedAt()
        ));
    }
}