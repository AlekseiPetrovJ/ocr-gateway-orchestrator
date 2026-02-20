import json
import shutil
import logging
import subprocess
import time
import os
import pika
import hashlib
from pathlib import Path
from minio import Minio
from langfuse import Langfuse
from unittest.mock import MagicMock

from docling_parser import DoclingParser
from preprocessor import run_cleanup, run_normalization
from processing_profile import ProcessingProfile
from config import settings

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger("Coordinator")

def calculate_sha256(file_path):
    """Расчет SHA-256 для проверки целостности файла"""
    sha256_hash = hashlib.sha256()
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

def _calculate_hash_from_stream(self, stream):
    """Универсальный расчет SHA-256 из любого потока (MinIO или файл)"""
    sha256_hash = hashlib.sha256()
    # Читаем кусками по 4КБ, чтобы не забить оперативку
    for byte_block in iter(lambda: stream.read(4096), b""):
        sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

class Coordinator:
    def __init__(self, parser_instance: DoclingParser):
        """Инициализация синглтонов при старте контейнера"""
        self.parser = parser_instance
        # Инициализация Langfuse
        self.langfuse = Langfuse(
            public_key=settings.lf_public_key,
            secret_key=settings.lf_secret_key,
            host=settings.lf_host
        )

        # S3 Хранилище
        self.s3 = Minio(settings.s3_endpoint, settings.s3_access, settings.s3_secret, secure=False)

        # Настройка RabbitMQ
        params = pika.URLParameters(settings.rabbit_url)
        params.heartbeat = settings.rmq_heartbeat
        self.connection = pika.BlockingConnection(params)
        self.channel = self.connection.channel()

        # Объявление очередей
        self.channel.queue_declare(queue=settings.queue_input, durable=True)
        self.channel.queue_declare(queue=settings.queue_output, durable=True)
        self.channel.basic_qos(prefetch_count=1)

    def _send_response(self, task_id, trace_id, status, storage_path, sha256, file_size, metadata):
        """Отправка тикета-ответа (Event) обратно в шину"""
        payload = {
            "taskId": task_id,
            "traceId": trace_id,
            "status": status,
            "storagePath": storage_path,
            "sha256": sha256,
            "fileSize": file_size,
            "metadata": metadata or {} # Твой пакет артефактов
        }

        # Отправляем JSON строку
        self.channel.basic_publish(
            exchange='',
            routing_key=settings.queue_output,
            body=json.dumps(payload),
            properties=pika.BasicProperties(delivery_mode=2, content_type='application/json')
        )

    def _execute_pipeline(self, profile: ProcessingProfile, input_pdf: Path, out_dir: Path, trace):
        """Оркестрация цепочки. Каждый этап — отдельный Span в Langfuse."""
        current_file = input_pdf

        # 1. Очистка (Ghostscript)
        if "clean" in profile.value:
            span = trace.span(name="cleanup_gs")
            try:
                logger.info(f"[GS] Cleanup started: {current_file.name}")
                next_file = input_pdf.parent / "1_cleaned.pdf"
                # Вызов метода очистки (внутри subprocess.run)
                run_cleanup(current_file, next_file)
                current_file = next_file
                span.update(metadata={"output": "1_cleaned.pdf"})
            finally:
                span.end()

        # 2. Нормализация (OCRmyPDF)
        if "norm" in profile.value:
            span = trace.span(name="normalization_ocr")
            try:
                logger.info(f"[OCR] Normalization started: {current_file.name}")
                next_file = input_pdf.parent / "2_normalized.pdf"
                run_normalization(current_file, next_file)
                current_file = next_file
                span.update(metadata={"output": "2_normalized.pdf"})
            finally:
                span.end()

        # 3: Docling
        span = trace.span(name="parsing_docling")
        try:
            logger.info(f"[Docling] Final Parse: {current_file.name}")
            # Метод внутри docling_parser.py
            self.parser.process(current_file, out_dir)
            span.update(metadata={"engine": "docling_v2_core"})
        finally:
            span.end()

    def on_message(self, ch, method, properties, body):
        """Точка входа: обработка сообщения из RabbitMQ"""
        data = json.loads(body)

        task_id = data.get('taskId')
        trace_id = data.get('traceId')
        s3_path = data.get('storagePath')
        file_hash = data.get('sha256')
        profile_str = data.get('profile', 'parse')
        profile = ProcessingProfile(profile_str)
        # --- Observability Fail-Safe ---
        # Подключаемся к Root Trace из Java по trace_id.
        # ВНИМАНИЕ: Ошибки мониторинга не должны останавливать конвейер (Fail-Safe).
        try:
            # Проверяем наличие метода trace, чтобы не упасть при плохой авторизации
            if hasattr(self.langfuse, 'trace'):
                trace = self.langfuse.trace(id=trace_id, name=f"WorkerProcess:{profile.value}")
            else:
                trace = MagicMock()
        except Exception as e:
            logger.warning(f"Langfuse failed: {e}")
            trace = MagicMock()

        # Контекст файловой системы для задачи
        work_dir = Path(f"/tmp/{task_id}")
        raw_pdf = work_dir / "input.pdf"
        results_dir = work_dir / "results"
        tar_path = work_dir / f"{task_id}.tar"

        try:
            work_dir.mkdir(parents=True, exist_ok=True)
            logger.info(f"🚀 Задача {task_id} получена. Профиль: {profile.value}")

            # Проверка наличия результата в S3 (Идемпотентность)
            # Если результат уже в S3, не тратим ресурсы CPU/GPU
            result_filename = f"{task_id}.tar"
            try:
                stat = self.s3.stat_object(settings.bucket_proc, result_filename)
                logger.info(f"Результат {task_id} уже существует. Пропускаем OCR.")
                file_size = stat.size
                file_hash = stat.metadata.get('x-amz-meta-sha256')
                # 3. Если хэша НЕТ (кто-то залил файл мимо системы) — считаем его один раз
                # 2. Если в паспорте (метаданных) пусто — считаем вручную
                if not file_hash:
                    response = self.s3.get_object(settings.bucket_proc, result_filename)
                    with response:
                        file_hash = _calculate_hash_from_stream(response)

                # Создаем спан и сразу закрываем его
                span = trace.span(name="idempotency_hit")
                self._send_response(task_id, "SUCCESS", result_filename, file_hash, file_size, {"hit": "s3_stat"})
                span.end()

                self.langfuse.flush() # Выталкиваем трейс перед выходом
                ch.basic_ack(delivery_tag=method.delivery_tag)
                return
            except:
                pass # Результата нет, работаем

            # 1. DOWNLOAD
            span_dl = trace.span(name="io_s3_download")
            self.s3.fget_object(settings.bucket_raw, s3_path, str(raw_pdf))
            span_dl.end()

            # 2. INTEGRITY
            span_hash = trace.span(name="integrity_hash_check")
            actual_hash = calculate_sha256(raw_pdf)
            if file_hash and actual_hash != file_hash:
                raise Exception(f"Integrity error: expected {file_hash}, but got {actual_hash}")
            logger.info(f"🛡️ Hash verified: {actual_hash}")
            span_hash.end()

            # 3. PIPELINE (GS -> OCR -> Docling)
            # Внутри создаются спаны cleanup_gs, normalization_ocr, parsing_docling
            self._execute_pipeline(profile, raw_pdf, results_dir, trace)

            # 4. PACKAGING (TAR без сжатия)
            span_pack = trace.span(name="io_pack_tar")
            subprocess.run(["tar", "-cf", str(tar_path), "-C", str(results_dir), "."], check=True)
            result_hash = calculate_sha256(tar_path)
            span_pack.end()

            # 5. UPLOAD
            span_up = trace.span(name="io_s3_upload")
            self.s3.fput_object(
                bucket_name=settings.bucket_proc,
                object_name=result_filename,
                file_path=str(tar_path),
                content_type="application/x-tar",
                metadata={"sha256": result_hash})
            span_up.end()

            # 6. RESPONSE
            file_size = os.path.getsize(str(tar_path))
            self._send_response(
                task_id,
                trace_id,
                "COMPLETED",
                result_filename,
                result_hash,
                file_size,
                {}
            )

            self.langfuse.flush() # Финальный сброс трейсов
            ch.basic_ack(delivery_tag=method.delivery_tag)
            logger.info(f"Задача {task_id} завершена успешно.")

        except Exception as e:
            error_msg = str(e)
            logger.error(f"Ошибка задачи {task_id}: {error_msg}")
            trace.update(status_message=error_msg, level="ERROR")

            # Сообщаем внешней системе об ошибке
            self._send_response(
                task_id,
                trace_id,
                "ERROR",
                "FAILED",
                "ERROR_HASH",
                0,
                {"error": error_msg}
            )
            self.langfuse.flush()
            # Отправляем в DLQ (requeue=False)
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

        finally:
            # Очистка диска
            shutil.rmtree(work_dir, ignore_errors=True)

    def start(self):
        """Бесконечный цикл прослушивания очереди"""
        logger.info(f"Воркер запущен. Слушаю очередь: {settings.queue_input}")
        self.channel.basic_consume(queue=settings.queue_input, on_message_callback=self.on_message)
        self.channel.start_consuming()

if __name__ == "__main__":
    # Загружаем нейросети один раз при старте контейнера (Bean Initialization)
    # Это позволяет экономить время на каждой задаче
    shared_parser = DoclingParser()

    # Запускаем координатор
    worker = Coordinator(parser_instance=shared_parser)
    worker.start()