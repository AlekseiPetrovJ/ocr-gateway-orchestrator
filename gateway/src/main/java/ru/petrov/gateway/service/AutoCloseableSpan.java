package ru.petrov.gateway.service;

import java.util.function.Consumer;

public class AutoCloseableSpan implements AutoCloseable {
    private final String traceId;
    private final String spanName;
    private final long startTime;
    private final Consumer<SpanData> onUpdate; // Коллбэк для отправки данных

    public AutoCloseableSpan(String traceId, String spanName, Consumer<SpanData> onUpdate) {
        this.traceId = traceId;
        this.spanName = spanName;
        this.startTime = System.currentTimeMillis();
        this.onUpdate = onUpdate;
    }

    @Override
    public void close() {
        long duration = System.currentTimeMillis() - startTime;
        // Передаем данные обратно в Tracer для отправки в Langfuse
        onUpdate.accept(new SpanData(traceId, spanName, startTime, duration));
    }

    public record SpanData(String traceId, String spanName, long startMillis, long duration) {}
}

