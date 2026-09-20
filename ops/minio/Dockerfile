FROM golang:1.24.8-alpine@sha256:3d78beb141d98f42337f1252ecf2a5f20374109929a4c3f6817f9e4179cc0ae5 AS build
ARG MINIO_REF=RELEASE.2025-10-15T17-29-55Z
RUN apk add --no-cache ca-certificates git
RUN git clone --depth 1 --branch "${MINIO_REF}" https://github.com/minio/minio.git /src
WORKDIR /src
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/minio .

FROM alpine:3.22.1@sha256:eafc1edb577d2e9b458664a15f23ea1c370214193226069eb22921169fc7e43f
RUN apk add --no-cache ca-certificates \
    && addgroup -S -g 10001 minio \
    && adduser -S -D -H -u 10001 -G minio minio \
    && mkdir -p /data \
    && chown minio:minio /data
COPY --from=build /out/minio /usr/local/bin/minio
USER 10001:10001
EXPOSE 9000 9001
VOLUME ["/data"]
HEALTHCHECK --interval=5s --timeout=3s --retries=12 \
    CMD ["wget", "-q", "--spider", "http://localhost:9000/minio/health/live"]
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
