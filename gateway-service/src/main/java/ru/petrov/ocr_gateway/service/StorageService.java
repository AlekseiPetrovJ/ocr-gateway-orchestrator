package ru.petrov.ocr_gateway.service;

import org.springframework.web.multipart.MultipartFile;
import ru.petrov.ocr_gateway.model.FileEntity;

public interface StorageService {
    FileEntity uploadFile(MultipartFile file);
}