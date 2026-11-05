FROM debian:bookworm-slim@sha256:3783cc01769c7b2b1b83a5c5ad96c815348e28ed7da68e2e3687004faa906251

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        poppler-data \
        poppler-utils \
        python3 \
        python3-opencv \
        tesseract-ocr \
        tesseract-ocr-eng \
        tesseract-ocr-jpn \
        tesseract-ocr-kor \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY server.py /app/server.py

ENV OCR_HOST=0.0.0.0 \
    OCR_PORT=8090 \
    OCR_LANGUAGES=kor+eng+jpn \
    OCR_MAX_BYTES=15728640 \
    OCR_MAX_PAGES=10 \
    OPENCV_IO_MAX_IMAGE_PIXELS=40000000

EXPOSE 8090
USER 65534:65534
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD ["curl", "--fail", "--silent", "http://localhost:8090/health"]
ENTRYPOINT ["python3", "/app/server.py"]
