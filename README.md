# OCR-Gateway Highload

Архитектурный шлюз для оцифровки документов с упором на сбор данных для AI-аналитики.

## 🚀 Цель проекта
Создание масштабируемого конвейера (Java + Python + RabbitMQ) для распознавания документов с фиксацией метрик (Lineage) для последующего обучения Арбитра моделей.

## 🛠 Стек
- **Backend:** Java 21, Spring Boot 4, Hibernate  (JSONB support)
- **DB:** PostgreSQL (pgvector ready)
- **Storage:** MinIO (S3-compatible)
- **Broker:** RabbitMQ
- **Observability:** LangFuse

## 🧬 Архитектурные особенности
- **Content Addressable Storage:** Дедупликация файлов на основе SHA-256.
- **Async Pipeline:** Разделение процессов загрузки и обработки.
- **Model Lineage:** Сохранение версий моделей и таймингов каждого этапа в `steps_log`.
- **Hybrid Storage:** Метаданные в БД, тяжелые MD/JSON результаты в MinIO.

## 📊 Статус разработки
- [x] Проектирование БД сущностей (`FileEntity`, `TaskEntity`)
- [x] Выбор стратегии дедупликации
- [x] Реализация FileService и интеграция с MinIO
- [ ] Внедрение LangFuse
- [ ] Описание RabbitMQ контракта
- [ ] **[ocr-worker/README.md](ocr-worker/README.md):** API часть реализована (см. документацию), интеграция с RabbitMQ в работе.
- [ ] Внедрение кэширования (Redis)
- [ ] Валидация входных структур  (Hibernate Validator)
- [ ] Настройка мониторинга и алертинга (Spring Boot Actuator)
- [ ] CI/CD + SWARM

```mermaid
sequenceDiagram
    participant User as Пользователь / 1С
    participant Java as Java Gateway (Spring Boot)
    participant RMQ as RabbitMQ (pdf_tasks)
    participant Worker as Python Worker (Coordinator)
    participant S3 as MinIO (S3 Storage)
    participant LF as Langfuse (Distributed Trace)

    Note over User, LF: Инициализация задачи (Trace Start)
    User->>Java: POST /upload {file, profile}
    Java->>LF: 1. Start Root Trace (trace_id)
    Java->>S3: 2. Upload Raw PDF (bucket: raw)
    Java->>RMQ: 3. Publish Task {trace_id, task_id, s3_path, profile}
    Java-->>User: 202 Accepted (task_id)

    Note over RMQ, Worker: Обработка воркером
    RMQ->>Worker: 4. Consumer: Get Task (Prefetch 1)
    Worker->>LF: 5. Join Trace (trace_id, span: worker_exec)
    
    Worker->>S3: 6. Download PDF to /tmp
    
    rect rgb(240, 240, 240)
    Note right of Worker: Pipeline (Internal Spans)
    Worker->>LF: 7. Span: Cleanup (Ghostscript)
    Worker->>Worker: 8. GS Filter Text/BG
    Worker->>LF: 9. Span: Normalization (OCRmyPDF)
    Worker->>Worker: 10. Deskew & Rotate
    Worker->>LF: 11. Span: Parsing (Docling)
    Worker->>Worker: 12. Structure & Text Extraction
    end

    Worker->>Worker: 13. Packaging: TAR (no compression)
    Worker->>S3: 14. Upload TAR to processed/
    
    Worker->>LF: 15. Close Spans & Trace
    Worker->>RMQ: 16. basic_ack (Confirm)

    Note over Java, S3: Результат готов к выдаче в Java

```