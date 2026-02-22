import os
import sys
import argparse
from pathlib import Path
from pypdf import PdfReader, PdfWriter

def split_pdf_files(input_folder, output_folder):
    # Создаем выходную папку, если её нет
    input_path = Path(input_folder)
    output_path = Path(output_folder)
    output_path.mkdir(parents=True, exist_ok=True)

    # Ищем все PDF файлы
    pdf_files = list(input_path.glob("*.pdf"))
    if not pdf_files:
        print(f"❌ В папке '{input_folder}' PDF-файлы не найдены.")
        return

    print(f"🚀 Начинаю обработку {len(pdf_files)} файлов...")

    for pdf_file in pdf_files:
        try:
            reader = PdfReader(pdf_file)
            base_name = pdf_file.stem
            total_pages = len(reader.pages)

            print(f"📄 Обработка: {pdf_file.name} ({total_pages} стр.)")

            for i in range(total_pages):
                writer = PdfWriter()
                writer.add_page(reader.pages[i])

                # Формат имени: Оригинал_001.pdf (с ведущими нулями для сортировки)
                output_filename = f"{base_name}_{i+1:03d}.pdf"
                dest_file = output_path / output_filename

                with open(dest_file, "wb") as f:
                    writer.write(f)

            print(f"✅ {pdf_file.name} успешно нарезан.")
        except Exception as e:
            print(f"⚠️ Ошибка при обработке {pdf_file.name}: {e}")

    print(f"\n✨ Готово! Все страницы сохранены в: {output_folder}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Нарезка PDF документов по страницам.")
    parser.add_argument("--input", default="./input", help="Путь к папке с исходными PDF")
    parser.add_argument("--output", default="./output", help="Путь к папке для нарезанных страниц")

    args = parser.parse_args()
    split_pdf_files(args.input, args.output)
