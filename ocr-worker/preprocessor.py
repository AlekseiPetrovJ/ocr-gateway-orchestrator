import subprocess
import logging
from pathlib import Path

# Настройка простого логгера
logger = logging.getLogger(__name__)

def run_cleanup(inp: Path, out: Path):
    """
    Ghostscript: удаление подложки/текстового слоя.
    """
    logger.info(f"🧹 Запуск Ghostscript для {inp.name}")
    cmd = [
        "gs", "-o", str(out), 
        "-sDEVICE=pdfwrite", 
        "-dFILTERTEXT", 
        str(inp)
    ]
    # check=True выбросит исключение, если GS упадет
    # capture_output=True соберет логи ошибки
    subprocess.run(cmd, check=True, capture_output=True, text=True)

def run_normalization(inp: Path, out: Path):
    """
    OCRmyPDF: выравнивание (deskew) и поворот страниц.
    """
    logger.info(f"📐 Запуск OCRmyPDF для {inp.name}")
    cmd = [
        "ocrmypdf", 
        "--deskew", 
        "--rotate-pages",
        "--tesseract-timeout", "0", # говорим Тессеракту не тратить время на распознавание
        "--force-ocr",      # Заставляет пересобрать растр (нужно для deskew)
        str(inp), 
        str(out)
    ]
    subprocess.run(cmd, check=True, capture_output=True, text=True)
