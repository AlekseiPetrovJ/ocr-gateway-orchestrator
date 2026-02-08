import torch
import json
import argparse
import warnings
import re
from pathlib import Path
from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import (
    PdfPipelineOptions, EasyOcrOptions, RapidOcrOptions, 
    AcceleratorOptions, AcceleratorDevice
)
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling_core.transforms.chunker import HierarchicalChunker

class DoclingParser:
    def __init__(self, ocr_engine="easy"):
        warnings.filterwarnings("ignore", category=UserWarning)
        
        is_cuda = torch.cuda.is_available()
        dev = AcceleratorDevice.CUDA if is_cuda else AcceleratorDevice.CPU
        print(f"--- [HARDWARE] Device: {dev} | CUDA: {is_cuda} ---")
        
        opts = PdfPipelineOptions()
        opts.do_ocr = True
        opts.generate_page_images = False
        opts.generate_picture_images = True
        opts.images_scale = 2.0
        
        # меняем только одно поле в готовом объекте
        opts.accelerator_options.device = dev 
        
        if ocr_engine == "rapid":
            opts.ocr_options = RapidOcrOptions(force_full_page_ocr=True)
        else:
            opts.ocr_options = EasyOcrOptions(lang=["ru", "en"], force_full_page_ocr=True)

        self.converter = DocumentConverter(
            format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=opts)}
        )
        print(f"✅ Parser initialized")


    def _remove_base64(self, obj):
        if isinstance(obj, dict):
            for k, v in list(obj.items()):
                if k == "uri" and isinstance(v, str) and v.startswith("data:image"):
                    obj[k] = "stored_in_images_folder"
                else: self._remove_base64(v)
        elif isinstance(obj, list):
            for i in obj: self._remove_base64(i)

    def process(self, input_path: Path, out_dir: Path):
        img_dir = out_dir / "images"
        out_dir.mkdir(parents=True, exist_ok=True)
        img_dir.mkdir(parents=True, exist_ok=True)

        res = self.converter.convert(input_path, raises_on_error=False)
        # логирование битых страниц, пока в консоль докера.
        for i, page in enumerate(res.pages):
            if not hasattr(page, "page_no") or page.size is None:
                print(f"⚠ [PAGE ERROR] Стр {i+1}: страница не была обработана корректно")
        
        doc = res.document

        # 1. Извлекаем картинки
        img_map = {}
        for i, el in enumerate(doc.pictures):
            idx = i + 1
            img = el.get_image(doc)
            if img:
                img.save(img_dir / f"img_{idx}.png", "PNG")
                img_map[idx] = f"images/img_{idx}.png"

        # 2. FULL JSON (Clean)
        f_dict = doc.export_to_dict()
        self._remove_base64(f_dict)
        with open(out_dir / f"{input_path.stem}.json", "w", encoding="utf-8") as f:
            json.dump(f_dict, f, ensure_ascii=False, indent=2)

        # 3. STRUCTURE JSON (Для БД)
        struct = []
        p_idx = 1
        for item, lvl in doc.iterate_items():
            pg, box = None, None
            if item.prov:
                p = item.prov[0] if isinstance(item.prov, list) else item.prov
                pg = p.page_no
                box = p.bbox.model_dump() if hasattr(p, "bbox") else None

            txt, img_p = "", None
            if item.label == "table":
                txt = f"\n\n{item.export_to_markdown(doc=doc)}\n\n" # FIX: добавили doc
            elif item.label == "picture":
                img_p = img_map.get(p_idx)
                p_idx += 1
            elif hasattr(item, "text"):
                txt = item.text

            struct.append({
                "type": str(item.label),
                "level": lvl,
                "text": txt,
                "image": img_p,
                "page": pg,
                "bbox": box
            })
        
        with open(out_dir / f"{input_path.stem}_structure.json", "w", encoding="utf-8") as f:
            json.dump(struct, f, ensure_ascii=False, indent=2)

        # 4. CHUNKS JSON (Для LLM)
        chunker = HierarchicalChunker()
        chunks = []
        for c in chunker.chunk(doc):
            chunks.append({
                "text": chunker.contextualize(c),
                "metadata": {
                    "headings": c.meta.headings if c.meta.headings else [],
                    "pages": list(set([p.page_no for it in c.meta.doc_items for p in it.prov])) if c.meta.doc_items else []
                }
            })
        with open(out_dir / f"{input_path.stem}_chunks.json", "w", encoding="utf-8") as f:
            json.dump(chunks, f, ensure_ascii=False, indent=2)

        # 5. MD & HTML (Фикс картинок)
        md_text = doc.export_to_markdown()
        # В v2.x ручная вставка картинок в MD, если плейсхолдеры не сработали
        for i in range(1, len(doc.pictures) + 1):
            if "<!-- image -->" in md_text:
                md_text = md_text.replace("<!-- image -->", f"\n![Рис {i}](images/img_{i}.png)\n", 1)
        
        with open(out_dir / f"{input_path.stem}.md", "w", encoding="utf-8") as f:
            f.write(md_text)

        html_path = out_dir / f"{input_path.stem}.html"
        doc.save_as_html(html_path)
        with open(html_path, "r", encoding="utf-8") as f:
            html_c = f.read()
        # Фикс src в HTML
        for i in range(1, len(doc.pictures) + 1):
            html_c = re.sub(r'src="[^"]*"|src=\'\'', f'src="images/img_{i}.png"', html_c, count=1)
        with open(html_path, "w", encoding="utf-8") as f:
            f.write(html_c)

# --- CLI ---
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--ocr", choices=["easy", "rapid"], default="easy")
    args = parser.parse_args()
    p = DoclingParser(ocr_engine=args.ocr)
    p.process(Path(args.input), Path("result_folder") / f"{Path(args.input).stem}_{args.ocr}")
    print("✅ Done")
