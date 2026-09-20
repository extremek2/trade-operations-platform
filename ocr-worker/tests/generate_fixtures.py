#!/usr/bin/env python3
"""Generate deterministic supplier-document fixtures for PDF text and OCR tests."""

from pathlib import Path
import random

from PIL import Image, ImageEnhance, ImageFilter
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


ROOT = Path(__file__).resolve().parents[2]
TMP = ROOT / "tmp" / "pdfs"
OUTPUT = ROOT / "output" / "pdf"
FIXTURES = ROOT / "api-server" / "src" / "test" / "resources" / "fixtures" / "ocr"
def paths() -> tuple[Path, Path, Path]:
    for directory in (TMP, OUTPUT, FIXTURES):
        directory.mkdir(parents=True, exist_ok=True)
    text_pdf = OUTPUT / "supplier-quotation-text.pdf"
    scan_pdf = OUTPUT / "supplier-quotation-blurred-scan.pdf"
    scan_png = FIXTURES / "supplier-quotation-blurred-scan.png"
    return text_pdf, scan_pdf, scan_png


def register_fonts() -> None:
    pdfmetrics.registerFont(UnicodeCIDFont("HYSMyeongJo-Medium"))
    pdfmetrics.registerFont(UnicodeCIDFont("HYGothic-Medium"))


def build_text_pdf(destination: Path) -> None:
    register_fonts()
    styles = getSampleStyleSheet()
    title = ParagraphStyle(
        "TitleCJK", parent=styles["Title"], fontName="HYGothic-Medium", fontSize=20,
        leading=26, alignment=TA_CENTER, textColor=colors.HexColor("#15314B"),
    )
    body = ParagraphStyle("BodyCJK", parent=styles["BodyText"], fontName="HYSMyeongJo-Medium", fontSize=9.5, leading=14)
    doc = SimpleDocTemplate(
        str(destination), pagesize=A4, rightMargin=18 * mm, leftMargin=18 * mm,
        topMargin=17 * mm, bottomMargin=17 * mm,
        title="Synthetic Supplier Quotation", author="Trade Operations Platform",
    )
    story = [
        Paragraph("SUPPLIER QUOTATION", title),
        Paragraph("공급 상품 제안서 / SUPPLIER OFFER", ParagraphStyle(
            "Subtitle", parent=title, fontSize=12, leading=17, textColor=colors.HexColor("#51677A")
        )),
        Spacer(1, 8 * mm),
    ]
    meta = Table([
        ["Supplier / 공급처", "TOKYO SAMPLE TRADING", "Quote No.", "SYN-2026-0919"],
        ["Date / 작성일", "2026-09-19", "Currency / 통화", "JPY"],
    ], colWidths=[35 * mm, 62 * mm, 30 * mm, 43 * mm])
    meta.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, -1), "HYSMyeongJo-Medium"), ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#EDF3F7")),
        ("BACKGROUND", (2, 0), (2, -1), colors.HexColor("#EDF3F7")),
        ("GRID", (0, 0), (-1, -1), 0.6, colors.HexColor("#90A4B3")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"), ("PADDING", (0, 0), (-1, -1), 6),
    ]))
    story.extend([meta, Spacer(1, 7 * mm)])
    rows = [
        ["SKU", "상품명 / Product", "MOQ", "Unit", "Unit Price", "Origin"],
        ["JP-001", "스테인리스 텀블러", "24", "EA", "850", "JP"],
        ["JP-002", "보관 파우치", "10", "EA", "320", "CN"],
        ["JP-003", "휴대용 접이식 우산", "12", "EA", "1,280", "JP"],
        ["JP-004", "샘플 진열대", "5", "SET", "2,400", "KR"],
    ]
    items = Table(rows, colWidths=[22 * mm, 68 * mm, 18 * mm, 18 * mm, 28 * mm, 18 * mm], repeatRows=1)
    items.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, 0), "HYGothic-Medium"),
        ("FONTNAME", (0, 1), (-1, -1), "HYSMyeongJo-Medium"),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#15314B")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F7FAFC")]),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#90A4B3")),
        ("ALIGN", (2, 1), (-1, -1), "CENTER"),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"), ("PADDING", (0, 0), (-1, -1), 7),
    ]))
    story.extend([
        items, Spacer(1, 8 * mm),
        Paragraph("Terms: Prices exclude freight. Valid for 30 days. 가격과 MOQ는 발주 전 재확인 바랍니다.", body),
        Spacer(1, 14 * mm),
        Paragraph("SYNTHETIC TEST DOCUMENT - 실제 거래에 사용할 수 없습니다.", ParagraphStyle(
            "Footer", parent=body, alignment=TA_CENTER, textColor=colors.HexColor("#A13D3D")
        )),
    ])
    doc.build(story)


def make_blurred_scan(text_pdf: Path, scan_pdf: Path, scan_png: Path) -> None:
    rendered_prefix = TMP / "supplier-quotation-source"
    import subprocess
    subprocess.run(["pdftoppm", "-f", "1", "-singlefile", "-r", "180", "-png",
                    str(text_pdf), str(rendered_prefix)], check=True)
    source = Image.open(rendered_prefix.with_suffix(".png")).convert("RGB")
    reduced = source.resize((source.width * 2 // 3, source.height * 2 // 3), Image.LANCZOS)
    blurred = reduced.filter(ImageFilter.GaussianBlur(radius=1.15))
    blurred = ImageEnhance.Contrast(blurred).enhance(0.88)
    pixels = blurred.load()
    rng = random.Random(20260919)
    for _ in range((blurred.width * blurred.height) // 150):
        x = rng.randrange(blurred.width)
        y = rng.randrange(blurred.height)
        r, g, b = pixels[x, y]
        delta = rng.choice((-14, -9, 9, 14))
        pixels[x, y] = tuple(max(0, min(255, channel + delta)) for channel in (r, g, b))
    blurred.save(scan_png, format="PNG", optimize=True)
    blurred.save(scan_pdf, format="PDF", resolution=120.0)


def copy_fixtures(text_pdf: Path, scan_pdf: Path) -> None:
    (FIXTURES / text_pdf.name).write_bytes(text_pdf.read_bytes())
    (FIXTURES / scan_pdf.name).write_bytes(scan_pdf.read_bytes())


def main() -> None:
    text_pdf, scan_pdf, scan_png = paths()
    build_text_pdf(text_pdf)
    make_blurred_scan(text_pdf, scan_pdf, scan_png)
    copy_fixtures(text_pdf, scan_pdf)
    print(text_pdf)
    print(scan_pdf)
    print(scan_png)


if __name__ == "__main__":
    main()
