# 1. универсально для GPU и CPU
FROM nvidia/cuda:12.1.0-base-ubuntu22.04

# 2. Системные инструменты (GS, Tesseract, OCRmyPDF)
RUN apt-get update && apt-get install -y \
    python3-pip python3-dev \
    libgl1 libglib2.0-0 \
    ghostscript ocrmypdf tesseract-ocr-rus \
    && rm -rf /var/lib/apt/lists/*

RUN ln -s /usr/bin/python3 /usr/bin/python

WORKDIR /app

# 3. ОПТИМИЗАЦИЯ: Ставим Torch отдельно (забетонировали слой на 2Гб)
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cu121


# 4. Остальные библиотеки
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 5. ПРОГРЕВ (Вшиваем модели, чтобы работало без интернета)
RUN python -c "from docling.document_converter import DocumentConverter; DocumentConverter()"
RUN python -c "import easyocr; easyocr.Reader(['ru', 'en'])"

# 6. Копируем всё скрипты
COPY . .

# Открываем порт для Swagger
EXPOSE 8000

# Запускаем Веб-интерфейс
CMD ["python", "web_api.py"]
