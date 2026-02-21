package ru.petrov.gateway.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.petrov.gateway.model.TaskEntity;
import ru.petrov.gateway.model.TaskRequestDto;
import ru.petrov.gateway.model.TaskResponseDto;
import ru.petrov.gateway.model.TaskStatusResponseDto;
import ru.petrov.gateway.service.TaskService;
import ru.petrov.gateway.validation.ValidFile;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Slf4j
@Validated
public class TaskController {

    private final TaskService taskService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TaskResponseDto> processFile(
            @ValidFile @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("request") TaskRequestDto request) {

        log.info("Принят файл {} для профиля {}", file.getOriginalFilename(), request.profile());

        // Прокидываем в сервис файл и мапу опций из DTO
        TaskEntity task = taskService.createAndDispatch(file, request.profile(), request.options());

        return ResponseEntity.ok(new TaskResponseDto(
                task.getId(),
                task.getStatus(),
                task.getSourceFile().getSha256Hash(),
                task.getCreatedAt()
        ));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskStatusResponseDto> getStatus(@PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.getTaskStatus(taskId));
    }

    @GetMapping("/{taskId}/download")
    public ResponseEntity<Void> download(@PathVariable Long taskId) {
        String presignedUrl = taskService.getResultDownloadUrl(taskId);

        return ResponseEntity.status(HttpStatus.FOUND) // 302
                .location(URI.create(presignedUrl))
                .build();
    }
}