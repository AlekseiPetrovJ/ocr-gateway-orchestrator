import os

from dotenv import load_dotenv
load_dotenv()

class Settings:
    rabbit_host = os.getenv("RABBIT_HOST", "localhost")
    rabbit_port = os.getenv("RABBIT_PORT", "5672")
    s3_endpoint = os.getenv("MINIO_URL", "localhost:9005")

    rabbit_user = os.getenv("RABBIT_USER")
    rabbit_pass = os.getenv("RABBIT_PASS")
    s3_access   = os.getenv("MINIO_ACCESS")
    s3_secret   = os.getenv("MINIO_SECRET")

    # Мониторинг Langfuse
    lf_public_key = os.getenv("LANGFUSE_PUBLIC_KEY")
    lf_secret_key = os.getenv("LANGFUSE_SECRET_KEY")
    lf_host       = os.getenv("LANGFUSE_HOST", "https://cloud.langfuse.com")


    @property
    def rabbit_url(self):
        # Fail-fast: если юзер или пароль None, pika выкинет ошибку при коннекте
        return f"amqp://{self.rabbit_user}:{self.rabbit_pass}@{self.rabbit_host}:{self.rabbit_port}/"

    queue_input = "ocr.tasks.queue"
    queue_output = "ocr.results.queue"
    bucket_raw = "ocr-artifacts"
    bucket_proc = "ocr-processed"
    rmq_heartbeat = 60

settings = Settings()
