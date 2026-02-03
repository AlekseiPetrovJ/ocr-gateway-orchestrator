package ru.petrov.ocr_gateway.service;

import com.langfuse.client.LangfuseClient;
import com.langfuse.client.resources.ingestion.requests.IngestionRequest;
import com.langfuse.client.resources.ingestion.types.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LangfuseTracer implements AppTracer{

    private final LangfuseClient langfuse;

    @Override
    public void startTaskTrace(String traceId, String fileName, String hash) {
        try {
            // Создаем Body с метаданными
            TraceBody body = TraceBody.builder()
                    .name("ocr-ingestion")
                    .metadata(Optional.of(Map.of(
                            "filename", fileName,
                            "file_hash", hash
                    )))
                    .build();

            // Оборачиваем в Event (с timestamp в String!)
            TraceEvent traceEvent = TraceEvent.builder()
                    .id(traceId)
                    .timestamp(OffsetDateTime.now().toString())
                    .body(body)
                    .build();

            // Финальный инжест
            langfuse.ingestion().batch(IngestionRequest.builder()
                    .batch(List.of(IngestionEvent.traceCreate(traceEvent)))
                    .build());

            log.info("Langfuse trace initialized: {}", traceId);
        } catch (Exception e) {
            log.error("Failed to send trace to Langfuse: {}", e.getMessage());
        }
    }

    @Override
    public AutoCloseableSpan startSpan(String traceId, String spanName) {
        return new AutoCloseableSpan(traceId, spanName, (data) -> {
            try {
                // 1. Собираем "тело" спана. Именно здесь привязываем к TraceID
                OffsetDateTime startTime = Instant.ofEpochMilli(data.startMillis())
                        .atOffset(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.MILLIS);

                OffsetDateTime endTime = startTime.plus(data.duration(), ChronoUnit.MILLIS);

                // 2. Собираем Body
                CreateSpanBody body = CreateSpanBody.builder()
                        .traceId(Optional.of(data.traceId())) // Явно оборачиваем!
                        .name(Optional.of(data.spanName()))
                        .startTime(startTime)
                        .endTime(endTime)
                        .build();

// 2. Собираем Event (Конверт)
                CreateSpanEvent event = CreateSpanEvent.builder()
                        .id(UUID.randomUUID().toString())
                        .timestamp(OffsetDateTime.now().toString())
                        // Проверь, есть ли метод .traceId() прямо здесь у билдера Event
                        // Если есть - обязательно добавь!
                        .body(body)
                        .build();                                      // _FinalStage

                // 3. Отправляем
                langfuse.ingestion().batch(IngestionRequest.builder()
                        .batch(List.of(IngestionEvent.spanCreate(event)))
                        .build());

                log.info("📊 Span [{}] sent: {}ms", data.spanName(), data.duration());
                log.info("TRACE_CHECK: TraceID={}, SpanName={}, Duration={}ms", data.traceId(), data.spanName(), data.duration());

            } catch (Exception e) {
                log.error("❌ Failed to send span to Langfuse: {}", e.getMessage());
            }
        });
    }

}
