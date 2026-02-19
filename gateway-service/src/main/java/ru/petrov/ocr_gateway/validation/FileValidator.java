package ru.petrov.ocr_gateway.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RequiredArgsConstructor
public class FileValidator implements ConstraintValidator<ValidFile, MultipartFile> {

    @Value("${app.upload.max-size:20MB}")
    private DataSize maxSize;

    @Value("${app.upload.allowed-types}")
    private List<String> allowedTypes;

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        if (file == null || file.isEmpty()) return false;

        // Проверка размера
        if (file.getSize() > maxSize.toBytes()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Файл слишком велик. Максимум: " + maxSize)
                    .addConstraintViolation();
            return false;
        }

        // Проверка типа (MIME)
        if (!allowedTypes.contains(file.getContentType())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Тип " + file.getContentType() + " не поддерживается")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
