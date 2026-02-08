import os
import json
import time
import shutil
import pika
from pathlib import Path
from minio import Minio
from docling_parser import DoclingParser  # Твой класс

# --- Конфигурация из ENV (стандарт Highload) ---
RABBIT_URL = os.getenv("RABBIT_URL", "amqp://guest:guest@localhost/")
MINIO_URL = os.getenv("MINIO_URL", "localhost:9000")
MINIO_ACCESS = os.getenv("MINIO_ACCESS", "minioadmin")
MINIO_SECRET = os.getenv("MINIO_SECRET", "minioadmin")

class Coordinator:
    def __init__(self):
        # 1. Инициализируем парсер ОДИН раз (модели грузятся в RAM)
        self.parser = DoclingParser(ocr_engine="easy")
        
        # 2. Настройка S3
        self.s3 = Minio(MINIO_URL, access_key=MINIO_ACCESS, secret_key=MINIO_SECRET, secure=False)
        
        # 3. Настройка RabbitMQ
        params = pika.URLParameters(RABBIT_URL)
        self.connection = pika.BlockingConnection(params)
        self.channel = self.connection.channel()
        self.channel.queue_declare(queue="pdf_tasks", durable=True)
        self.channel.basic_qos(prefetch_count=1) # Берем строго по 1 задаче

    def upload_results(self, task_id, local_dir):
        """Загрузка всей папки с результатами в MinIO"""
        for file_path in Path(local_dir).rglob("*"):
            if file_path.is_file():
                s3_path = f"results/{task_id}/{file_path.relative_to(local_dir)}"
                self.s3.fput_object("documents", s3_path, str(file_path))

    def process_task(self, ch, method, properties, body):
        """Callback при получении задачи из RabbitMQ"""
        task = json.loads(body)
        task_id = task.get("task_id")
        input_s3_path = task.get("input_path") # путь к PDF в MinIO
        
        tmp_dir = Path(f"/tmp/{task_id}")
        input_pdf = tmp_dir / "input.pdf"
        output_dir = tmp_dir / "output"

        try:
            print(f"📥 Задача {task_id}: начинаю обработку...")
            tmp_dir.mkdir(parents=True, exist_ok=True)

            # Этап 1: Скачивание
            self.s3.fget_object("documents", input_s3_path, str(input_pdf))

            # Этап 2: Очистка (здесь можно вызвать GS-скрипт)
            # clean_pdf = self.run_cleanup(input_pdf) 

            # Этап 3: Парсинг (вызов модуля)
            self.parser.process(input_pdf, output_dir)

            # Этап 4: Загрузка результатов
            self.upload_results(task_id, output_dir)

            # Подтверждаем успех
            ch.basic_ack(delivery_tag=method.delivery_tag)
            print(f"✅ Задача {task_id} выполнена успешно.")

        except Exception as e:
            print(f"❌ Ошибка в задаче {task_id}: {e}")
            # Отклоняем задачу, не возвращая в очередь (чтобы не зациклить ошибку)
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
        
        finally:
            # Очистка временных файлов (Highload-гигиена)
            if tmp_dir.exists():
                shutil.rmtree(tmp_dir)

    def start(self):
        print("🚀 Координатор запущен и ждет задач...")
        self.channel.basic_consume(queue="pdf_tasks", on_message_callback=self.process_task)
        self.channel.start_consuming()

if __name__ == "__main__":
    coord = Coordinator()
    coord.start()
