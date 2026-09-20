#!/usr/bin/env python3
"""Small deterministic OCR HTTP worker with optional anti-blur preprocessing."""

from __future__ import annotations

import csv
import io
import json
import math
import os
from pathlib import Path
import subprocess
import tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import cv2


MAX_BYTES = int(os.getenv("OCR_MAX_BYTES", str(15 * 1024 * 1024)))
MAX_PAGES = int(os.getenv("OCR_MAX_PAGES", "10"))
LANGUAGES = os.getenv("OCR_LANGUAGES", "kor+eng+jpn")
MIN_TEXT_CHARS = int(os.getenv("OCR_MIN_TEXT_CHARS", "40"))
BLUR_THRESHOLD = float(os.getenv("OCR_BLUR_THRESHOLD", "110"))
PROCESS_TIMEOUT = int(os.getenv("OCR_PROCESS_TIMEOUT_SECONDS", "90"))
ENGINE_VERSION = "tesseract-antiblur-v1"


class ExtractionError(ValueError):
    pass


def run(command: list[str], *, text: bool = True) -> str | bytes:
    completed = subprocess.run(
        command, check=False, capture_output=True, text=text, timeout=PROCESS_TIMEOUT,
        env={**os.environ, "OMP_THREAD_LIMIT": "2"},
    )
    if completed.returncode != 0:
        error = completed.stderr.strip() if text else completed.stderr.decode("utf-8", "replace").strip()
        raise ExtractionError(error[:500] or "document command failed")
    return completed.stdout


def detect_type(content: bytes) -> str:
    if content.startswith(b"%PDF-"):
        return "PDF"
    if content.startswith(b"\x89PNG\r\n\x1a\n"):
        return "PNG"
    if content.startswith(b"\xff\xd8\xff"):
        return "JPEG"
    raise ExtractionError("PDF, PNG 또는 JPEG 원본만 처리할 수 있습니다.")


def pdf_pages(path: Path) -> int:
    output = run(["pdfinfo", str(path)])
    for line in output.splitlines():
        if line.startswith("Pages:"):
            pages = int(line.split(":", 1)[1].strip())
            if pages < 1 or pages > MAX_PAGES:
                raise ExtractionError(f"PDF 페이지는 {MAX_PAGES}개 이하여야 합니다.")
            return pages
    raise ExtractionError("PDF 페이지 수를 확인할 수 없습니다.")


def embedded_pdf_text(path: Path) -> str:
    return run(["pdftotext", "-layout", "-enc", "UTF-8", str(path), "-"])


def blur_score(gray) -> float:
    return float(cv2.Laplacian(gray, cv2.CV_64F).var())


def resize_for_ocr(gray):
    target = 1800
    shortest = min(gray.shape[:2])
    if shortest >= target:
        return gray
    scale = min(2.5, target / max(1, shortest))
    return cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)


def antiblur(gray):
    enlarged = resize_for_ocr(gray)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(enlarged)
    softened = cv2.GaussianBlur(clahe, (0, 0), 1.15)
    sharpened = cv2.addWeighted(clahe, 1.85, softened, -0.85, 0)
    return cv2.fastNlMeansDenoising(sharpened, None, 7, 7, 21)


def parse_tsv(tsv: str) -> dict:
    words = []
    line_words: dict[tuple[str, str, str, str], list[str]] = {}
    weighted_confidence = 0.0
    weight = 0
    reader = csv.DictReader(io.StringIO(tsv), delimiter="\t")
    for row in reader:
        value = (row.get("text") or "").strip()
        try:
            confidence = float(row.get("conf", "-1"))
        except ValueError:
            confidence = -1
        if not value or confidence < 0:
            continue
        key = (row.get("page_num", ""), row.get("block_num", ""), row.get("par_num", ""), row.get("line_num", ""))
        line_words.setdefault(key, []).append(value)
        char_weight = max(1, len(value))
        weighted_confidence += confidence * char_weight
        weight += char_weight
        words.append({
            "text": value, "confidence": round(confidence, 2),
            "left": int(row.get("left", "0")), "top": int(row.get("top", "0")),
            "width": int(row.get("width", "0")), "height": int(row.get("height", "0")),
        })
    text = "\n".join(" ".join(values) for values in line_words.values())
    average = weighted_confidence / weight if weight else 0.0
    quality = average + min(15.0, math.sqrt(max(0, len(text))))
    return {"text": text, "confidence": round(average, 2), "quality": quality, "words": words}


def tesseract(image_path: Path) -> dict:
    output = run([
        "tesseract", str(image_path), "stdout", "-l", LANGUAGES,
        "--oem", "1", "--psm", "6", "tsv",
    ])
    return parse_tsv(output)


def ocr_page(source: Path, work: Path, number: int) -> dict:
    image = cv2.imread(str(source), cv2.IMREAD_GRAYSCALE)
    if image is None:
        raise ExtractionError("페이지 이미지를 읽을 수 없습니다.")
    score = blur_score(image)
    original_path = work / f"page-{number}-original.png"
    enhanced_path = work / f"page-{number}-antiblur.png"
    cv2.imwrite(str(original_path), resize_for_ocr(image))
    cv2.imwrite(str(enhanced_path), antiblur(image))
    original = tesseract(original_path)
    enhanced = tesseract(enhanced_path)
    use_enhanced = enhanced["quality"] > original["quality"] + 0.5
    chosen = enhanced if use_enhanced else original
    return {
        "page": number,
        "blurScore": round(score, 2),
        "blurDetected": score < BLUR_THRESHOLD,
        "preprocessingApplied": use_enhanced,
        "originalConfidence": original["confidence"],
        "enhancedConfidence": enhanced["confidence"],
        "confidence": chosen["confidence"],
        "text": chosen["text"],
        "words": chosen["words"],
    }


def extract(content: bytes, file_name: str = "document") -> dict:
    if not content:
        raise ExtractionError("비어 있는 문서는 처리할 수 없습니다.")
    if len(content) > MAX_BYTES:
        raise ExtractionError(f"문서는 {MAX_BYTES // (1024 * 1024)}MB 이하여야 합니다.")
    document_type = detect_type(content)
    suffix = {"PDF": ".pdf", "PNG": ".png", "JPEG": ".jpg"}[document_type]
    with tempfile.TemporaryDirectory(prefix="trade-ops-ocr-") as directory:
        work = Path(directory)
        source = work / ("source" + suffix)
        source.write_bytes(content)
        if document_type == "PDF":
            page_count = pdf_pages(source)
            text = embedded_pdf_text(source)
            if len("".join(text.split())) >= MIN_TEXT_CHARS:
                return {
                    "engineVersion": ENGINE_VERSION, "method": "PDF_TEXT", "fileName": file_name,
                    "pageCount": page_count, "text": text.strip(), "confidence": 100.0,
                    "reviewRequired": False, "preprocessingApplied": False, "pages": [],
                }
            prefix = work / "rendered"
            run(["pdftoppm", "-png", "-r", "240", str(source), str(prefix)])
            page_files = sorted(work.glob("rendered-*.png"))
        else:
            page_count = 1
            page_files = [source]
        if not page_files or len(page_files) != page_count:
            raise ExtractionError("OCR 페이지 렌더링 결과가 올바르지 않습니다.")
        pages = [ocr_page(page, work, index) for index, page in enumerate(page_files, start=1)]
        text = "\n\n".join(page["text"] for page in pages if page["text"])
        confidence = sum(page["confidence"] for page in pages) / len(pages)
        return {
            "engineVersion": ENGINE_VERSION, "method": "OCR", "fileName": file_name,
            "pageCount": page_count, "text": text, "confidence": round(confidence, 2),
            "reviewRequired": confidence < 85.0 or not text.strip(),
            "preprocessingApplied": any(page["preprocessingApplied"] for page in pages), "pages": pages,
        }


class Handler(BaseHTTPRequestHandler):
    server_version = "TradeOpsOCR/1"

    def send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        if self.path == "/health":
            self.send_json(200, {"status": "UP", "engineVersion": ENGINE_VERSION})
        else:
            self.send_json(404, {"message": "not found"})

    def do_POST(self) -> None:
        if self.path != "/extract":
            self.send_json(404, {"message": "not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length < 1 or length > MAX_BYTES:
                raise ExtractionError(f"문서는 {MAX_BYTES // (1024 * 1024)}MB 이하여야 합니다.")
            content = self.rfile.read(length)
            result = extract(content, self.headers.get("X-File-Name", "document"))
            self.send_json(200, result)
        except ExtractionError as error:
            self.send_json(400, {"message": str(error)})
        except (subprocess.TimeoutExpired, OSError) as error:
            self.send_json(503, {"message": "OCR 엔진을 실행할 수 없습니다.", "detail": str(error)[:200]})
        except Exception:
            self.send_json(500, {"message": "OCR 처리 중 내부 오류가 발생했습니다."})

    def log_message(self, format: str, *args) -> None:
        print(f"ocr-worker {self.address_string()} {format % args}", flush=True)


def main() -> None:
    host = os.getenv("OCR_HOST", "0.0.0.0")
    port = int(os.getenv("OCR_PORT", "8090"))
    ThreadingHTTPServer((host, port), Handler).serve_forever()


if __name__ == "__main__":
    main()
