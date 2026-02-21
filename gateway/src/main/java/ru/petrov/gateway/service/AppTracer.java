package ru.petrov.gateway.service;

public interface AppTracer {
    void startTaskTrace(String traceId, String fileName, String hash);
    // Возвращает "закрывашку", которая сама отправит Span в Langfuse
    AutoCloseableSpan startSpan(String traceId, String spanName);
    void sendError(String traceId, String name, String message);
}
