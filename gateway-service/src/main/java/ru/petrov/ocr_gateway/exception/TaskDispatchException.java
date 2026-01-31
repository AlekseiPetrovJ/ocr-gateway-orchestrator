package ru.petrov.ocr_gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class TaskDispatchException extends RuntimeException{
    public TaskDispatchException(String message) {
        super(message);
    }

    public TaskDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
