import os
import json
import shutil
import zipfile
import uvicorn
from pathlib import Path
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.responses import FileResponse
from processing_profile import ProcessingProfile

import preprocessor  # Импортируем наш файл с GS и OCRmyPDF
# Импортируем класс парсера из файла docling_parser.py
from docling_parser import DoclingParser

app = FastAPI(title="Docling Parser API", description="MVP для Аверс")

# 1. Инициализация парсера (загрузка моделей в память)
try:
    print("🚀 Загрузка нейросетей Docling... Подождите.")
    parser_engine = DoclingParser(ocr_engine="easy")
    print("✅ Парсер готов к работе.")
except Exception as e:
    print(f"❌ Ошибка инициализации: {e}")
    parser_engine = None

@app.post("/parse", summary="Распознать PDF и вернуть ZIP с результатами")
async def parse_pdf(
    file: UploadFile = File(...),
    profile: ProcessingProfile  = Form(ProcessingProfile.parse) # Сюда придет профиль обработки
):
    if not parser_engine:
        raise HTTPException(status_code=500, detail="Движок парсера не загружен")

    # Создаем уникальную рабочую папку для задачи
    task_id = f"task_{int(os.getpid())}_{os.urandom(2).hex()}"
    tmp_dir = Path("./tmp") / task_id
    tmp_dir.mkdir(parents=True, exist_ok=True)

    base_name = Path(file.filename).stem
    zip_filename = f"{base_name}_results.zip"
    
    current_file = tmp_dir /  f"0_raw_{file.filename}"
    output_dir = tmp_dir / "results"
    zip_path = tmp_dir / zip_filename

    # Сохраняем входящий файл
    with current_file.open("wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    # Достаем текстовое значение из выпадающего списка
    profile_value = profile.value

    try:
        print(f"🚀 Начинаем цепочку [{profile_value}] для {file.filename}")

        # --- ШАГ 1: Очистка (Ghostscript) ---
        if "clean" in profile_value:
            print("🧹 Выполняю Ghostscript...")
            next_file = tmp_dir / "1_cleaned.pdf"
            preprocessor.run_cleanup(current_file, next_file)
            # ТЕПЕРЬ текущим файлом для следующего шага становится этот
            current_file = next_file

        # --- ШАГ 2: Нормализация (OCRmyPDF) ---
        if "norm" in profile_value:
            print("📐 Выполняю OCRmyPDF (Deskew)...")
            next_file = tmp_dir / "2_normalized.pdf"
            preprocessor.run_normalization(current_file, next_file)
            # ТЕПЕРЬ текущим файлом становится этот
            current_file = next_file

        # 2. ЗАПУСК ПАРСИНГА
        # Класс создаст JSON, Structure, Chunks, MD и Картинки
        print(f"🧬 Финальный парсинг файла: {current_file.name}")
        parser_engine.process(current_file, output_dir)

        # 3. УПАКОВКА В ZIP
        if not output_dir.exists():
            raise Exception("Папка с результатами не создана")

        # --- ПЕРЕИМЕНОВАНИЕ (возвращаем оригинальное имя файлам внутри) ---
        # Проходим по всем файлам в папке results
        for item in output_dir.glob("*"):
            if item.is_file():
                # Заменяем техническое имя (напр. "2_normalized") на оригинальное (напр. "отчет")
                # Но сохраняем расширение (.json, .md, .html)
                new_name = output_dir / f"{base_name}{item.suffix}"
                item.rename(new_name)

        with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
            for root, dirs, files in os.walk(output_dir):
                for f in files:
                    full_path = Path(root) / f
                    # Сохраняем относительный путь, чтобы в архиве была папка images/
                    arcname = full_path.relative_to(output_dir)
                    zipf.write(full_path, arcname)

        print(f"📦 Архив готов: {zip_filename}")
        
        # Возвращаем файл пользователю
        return FileResponse(
            path=zip_path, 
            filename=zip_filename, 
            media_type='application/zip'
        )

    except Exception as e:
        print(f"❌ Ошибка: {e}")
        return {"status": "error", "message": str(e)}
    
    # В MVP не удаляем tmp сразу,можно было зайти и посмотреть глазами,
    # но в проде здесь должен быть shutil.rmtree(tmp_dir)

@app.get("/health")
async def health():
    return {
        "status": "UP"
    }

if __name__ == "__main__":
    # Запуск на всех интерфейсах, порт 8000
    uvicorn.run(app, host="0.0.0.0", port=8000)
