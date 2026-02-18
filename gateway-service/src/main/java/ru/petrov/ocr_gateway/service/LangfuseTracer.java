package ru.petrov.ocr_gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LangfuseTracer implements AppTracer {
    private final RestTemplate restTemplate;

    @Value("${app.langfuse.public-key}")
    private String publicKey;

    @Value("${app.langfuse.secret-key}")
    private String secretKey;

    @Value("${app.langfuse.host}")
    private String host;

    @Async
    @Override
    public void startTaskTrace(String traceId, String fileName, String hash) {
        Map<String, Object> body = Map.of(
                "id", traceId,
                "name", "ocr-ingestion",
                "metadata", Map.of("filename", fileName, "hash", hash)
        );
        sendRawEvent("trace-create", traceId, body);
    }

    @Override
    public AutoCloseableSpan startSpan(String traceId, String spanName) {
        return new AutoCloseableSpan(traceId, spanName, (data) -> {
            Map<String, Object> body = Map.of(
                    "id", UUID.randomUUID().toString().replace("-", ""),
                    "traceId", data.traceId(),
                    "name", data.spanName(),
                    "startTime", Instant.ofEpochMilli(data.startMillis()).toString(),
                    "endTime", Instant.ofEpochMilli(data.startMillis() + data.duration()).toString()
            );

            sendRawEvent("span-create", data.traceId(), body);
        });
    }

    @Override
    @Async
    public void sendError(String traceId, String name, String message) {
        sendRawEvent("span-create", traceId, Map.of(
                "id", UUID.randomUUID().toString().replace("-", ""),
                "traceId", traceId,
                "name", name,
                "level", "ERROR", // Вот она, магия!
                "statusMessage", message,
                "startTime", Instant.now().toString(),
                "endTime", Instant.now().toString()
        ));
    }

    @Async
    public void sendRawEvent(String type, String traceId, Object body) {
        try {
            Map<String, Object> event = Map.of(
                    "id", UUID.randomUUID().toString().replace("-", ""),
                    "type", type,
                    "timestamp", Instant.now().toString(),
                    "body", body
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(publicKey, secretKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                    Map.of("batch", List.of(event)),
                    headers
            );

            restTemplate.postForEntity(host + "/api/public/ingestion", request, String.class);
            log.info("✅ Event [{}] sent for trace: {}", type, traceId);
        } catch (Exception e) {
            log.error("❌ Failed to send event [{}]: {}", type, e.getMessage());
        }
    }
}
