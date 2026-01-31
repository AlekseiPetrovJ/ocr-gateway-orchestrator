package ru.petrov.ocr_gateway.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
public class OcrController {
    @PostMapping("/upload")
    public ResponseEntity<String> uploadDocument(@RequestParam("file") MultipartFile file) {
        // 1. Валидация (PDF/Image)
        // 2. Сохранение метаданных в БД
        // 3. Сохранение файла на диск/хранилище
        return ResponseEntity.ok("Файл принят: " + file.getOriginalFilename());
    }
}
