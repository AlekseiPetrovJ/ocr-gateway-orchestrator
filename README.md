# OCR-Gateway: Шлюз распределенной оцифровки документов

Архитектурное решение для массового распознавания и структурирования документов. Система спроектирована как высоконадежный конвейер для подготовки данных для AI-аналитики и обучения нейросетей.

## 🚀 Цель проекта
Создание масштабируемой системы (Java + Python + RabbitMQ), которая берет на себя всю рутину по приему, очистке и глубокому парсингу тяжелых PDF, обеспечивая экономию ресурсов за счет умной дедупликации.

## 🧬 Ключевые возможности
*   **Умная дедупликация (SHA-256):** Система узнает файлы, которые уже обрабатывались. Повторный запрос выдает результат мгновенно (0 мс), не нагружая нейросети.
*   **Асинхронный конвейер:** Java-шлюз моментально принимает файл и отдает ID задачи. Тяжелая обработка (OCR, анализ структуры) идет в фоне на кластере Python-воркеров.
*   **Сквозной контроль (Tracing):** Благодаря интеграции с **Langfuse**, виден весь путь документа — от REST-контроллера до каждой секунды работы нейросети внутри Docker-контейнера.
*   **Прямая выдача (302 Redirect):** Шлюз не проксирует байты через себя. Клиент получает временную Presigned-ссылку и скачивает результат напрямую из S3-хранилища (MinIO).


## 🛠 Стек технологий
*   **Orchestrator (Ядро):** Java 21, Spring Boot 4
*   **Worker (Исполнитель):** Python 3.11 (Docling, OCRmyPDF, Ghostscript)
*   **База данных:** PostgreSQL (состояния задач и метаданные)
*   **Хранилище:** MinIO (S3-совместимое)
*   **Шина данных:** RabbitMQ (асинхронные очереди)
*   **Observability:** Langfuse (Distributed Tracing)

## 📊 Текущий статус проекта
- [x] Проектирование БД и сущностей (`FileEntity`, `TaskEntity`)
- [x] Логика дедупликации (Pioneer/Follower паттерн)
- [x] Интеграция с объектным хранилищем MinIO
- [x] **Внедрение Langfuse** (распределенный трейсинг Java ↔ Python)
- [x] **Очереди RabbitMQ** (асинхронный обмен задачами и статусами)
- [x] **Python Worker:** Оркестрация пайплайна (Cleanup -> OCR -> Docling). CPU/GPU ready
- [x] **Hybrid Storage:** Метаданные в БД, тяжелые архивы (JSON/MD/Images) в S3
- [ ] Внедрение кэширования (Redis) — *в бэклоге*
- [ ] Автоматизация бакетов MinIO (Terraform/Java-init) — *в бэклоге*


## 📊 Статус разработки
- [x] Проектирование БД сущностей (`FileEntity`, `TaskEntity`)
- [x] Выбор стратегии дедупликации
- [x] Реализация FileService и интеграция с MinIO
- [x] Внедрение LangFuse
- [x] Описание RabbitMQ контракта
- [x] **[ocr-worker/README.md](ocr-worker/README.md):** API часть реализована (см. документацию), интеграция с RabbitMQ в работе.
- [ ] Внедрение кэширования (Redis)
- [ ] Валидация входных структур  (Hibernate Validator)
- [ ] Настройка мониторинга и алертинга (Spring Boot Actuator)
- [ ] CI/CD + SWARM

```mermaid
sequenceDiagram
    autonumber
    participant User as Клиент / Другая система
    participant Java as Java Gateway (Orchestrator)
    participant DB as Postgres (JPA / Locks)
    participant RMQ as RabbitMQ (Input/Output)
    participant Worker as Python Worker (Coordinator)
    participant S3 as MinIO (S3 Storage)
    participant LF as Langfuse (Tracing)

    Note over User, LF: Фаза 1: Прием и Схлопывание (Deduplication)
    User->>Java: POST /upload {file, profile}
    Java->>LF: Start Trace
    
    rect rgb(230, 245, 255)
    Note right of Java: SELECT FOR UPDATE (Блокировка хэша)
    Java->>DB: 1. Check Hash (Deduplication)
    alt Status: COMPLETED / PROCESSING
        DB-->>Java: Возврат существующей задачи
        Java-->>User: 200 OK / 202 Accepted (Мгновенный хит)
    else Status: NEW (Новый файл)
        Java->>LF: Span: minio-upload
        Java->>S3: 2. fput_object (ocr-raw)
        Java->>DB: 3. Save Task (Status: ACCEPTED)
        Java->>RMQ: 4. Publish {taskId, traceId, profile, sha256}
        Java-->>User: 202 Accepted (taskId)
    end
    end

    Note over RMQ, Worker: Фаза 2: Оркестрация AI-ядра (Worker)
    RMQ->>Worker: 5. Consume (Prefetch 1)
    Worker->>LF: 6. Join Trace (WorkerProcess:parse)
    
    rect rgb(245, 245, 245)
    Note right of Worker: Idempotency & Integrity
    Worker->>S3: 7. Span: io_s3_check_and_download
    opt Если TAR уже в S3
        Worker->>RMQ: _send_response(SUCCESS, hit: s3_stat)
    end
    Worker->>S3: 8. Span: io_s3_download
    Worker->>LF: 9. Span: integrity_hash_check (SHA256)
    end

    rect rgb(255, 255, 240)
    Note right of Worker: _execute_pipeline (Core)
    opt profile: clean
        Worker->>LF: Span: cleanup_gs
    end
    opt profile: norm
        Worker->>LF: Span: normalization_ocr
    end
    Worker->>LF: 10. Span: parsing_docling (AI Engine)
    end

    Worker->>LF: 11. Span: io_pack_tar
    Worker->>S3: 12. Span: io_s3_upload (result.tar + meta: hash)
    Worker->>RMQ: 13. _send_response(Status: COMPLETED)
    Worker->>LF: 14. End Trace & Flush
    Worker->>RMQ: 15. basic_ack

    Note over User, S3: Фаза 3: Финализация (Gateway)
    RMQ->>Java: 16. Consume queue_output
    Java->>LF: 17. Span: gateway-finalize
    Java->>DB: 18. Update Task Status: COMPLETED
    
    User->>Java: 19. GET /download/{taskId}
    Java->>S3: 20. Generate Presigned URL
    Java-->>User: 302 Found (Redirect to MinIO)


```