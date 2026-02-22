import os
import json
import time
import requests
from pathlib import Path

# --- КОНФИГУРАЦИЯ (Твой шлюз) ---
API_URL = "http://localhost:8080/api/v1/tasks"
INPUT_DIR = "./output"  # Папка с нарезанными страницами
PROFILE = "parse"
# --------------------

def upload_files(directory):
    path = Path(directory)
    # Сортируем, чтобы страницы шли 001, 002, 003...
    files = sorted(list(path.glob("*.pdf")))

    if not files:
        print(f"❌ В папке '{directory}' нет PDF для отправки.")
        return

    print(f"🚀 Начинаю загрузку {len(files)} страниц на {API_URL}...")
    print("-" * 50)

    for i, file_path in enumerate(files, 1):
        request_data = {
            "profile": PROFILE,
            "options": {}
        }

        # Открываем файл
        file_obj = open(file_path, 'rb')

        # Формируем multipart запрос (как в твоем curl)
        files_payload = {
            'file': (file_path.name, file_obj, 'application/pdf'),
            'request': (None, json.dumps(request_data), 'application/json')
        }

        start_t = time.time()

        try:
            # Таймаут 30 сек, чтобы скрипт не висел вечно
            response = requests.post(API_URL, files=files_payload, timeout=30)
            elapsed = time.time() - start_t

            if response.status_code in [200, 201, 202]:
                result = response.json()
                task_id = result.get('taskId', 'N/A')
                file_hash = result.get('fileHash', 'no-hash')[:8]

                print(f"✅ [{i}/{len(files)}] {file_path.name} -> Task: {task_id} (Hash: {file_hash}) | Time: {elapsed:.2f}s")
            else:
                print(f"❌ [{i}/{len(files)}] {file_path.name} -> Error {response.status_code}: {response.text}")

        except Exception as e:
            print(f"⚠️ [{i}/{len(files)}] Ошибка соединения для {file_path.name}: {e}")

        finally:
            # 🧱 ГАРАНТИРОВАННО ЗАКРЫВАЕМ ФАЙЛ
            # Мы открыли его вручную в file_obj, теперь закрываем его же
            file_obj.close()

    print("-" * 50)
    print(f"✨ Готово! Все задачи отправлены в шлюз.")

if __name__ == "__main__":
    upload_files(INPUT_DIR)
