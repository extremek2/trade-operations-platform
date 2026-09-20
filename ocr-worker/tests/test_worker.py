import importlib.util
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("ocr_server", ROOT / "ocr-worker" / "server.py")
SERVER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SERVER)
FIXTURES = ROOT / "api-server" / "src" / "test" / "resources" / "fixtures" / "ocr"


class OcrWorkerTest(unittest.TestCase):
    def test_text_pdf_bypasses_ocr(self):
        result = SERVER.extract((FIXTURES / "supplier-quotation-text.pdf").read_bytes(), "text.pdf")
        self.assertEqual("PDF_TEXT", result["method"])
        self.assertRegex(result["text"], r"JP-\s*001")
        self.assertIn("스테인리스 텀블러", result["text"])
        self.assertFalse(result["reviewRequired"])
        self.assertFalse(result["preprocessingApplied"])

    def test_blurred_scan_runs_original_and_antiblur_candidates(self):
        result = SERVER.extract((FIXTURES / "supplier-quotation-blurred-scan.pdf").read_bytes(), "scan.pdf")
        self.assertEqual("OCR", result["method"])
        self.assertEqual(1, result["pageCount"])
        self.assertRegex(result["text"], r"JP-\s*001")
        self.assertGreater(result["pages"][0]["originalConfidence"], 0)
        self.assertGreater(result["pages"][0]["enhancedConfidence"], 0)
        self.assertTrue(result["pages"][0]["blurDetected"])

    def test_rejects_unknown_binary_and_oversize(self):
        with self.assertRaisesRegex(SERVER.ExtractionError, "PDF, PNG 또는 JPEG"):
            SERVER.extract(b"not a document")
        with self.assertRaisesRegex(SERVER.ExtractionError, "MB 이하"):
            SERVER.extract(b"%PDF-" + b"x" * SERVER.MAX_BYTES)


if __name__ == "__main__":
    unittest.main()
