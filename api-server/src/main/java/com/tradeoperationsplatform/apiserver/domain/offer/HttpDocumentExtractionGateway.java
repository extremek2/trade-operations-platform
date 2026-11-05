package com.tradeoperationsplatform.apiserver.domain.offer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.ocr.enabled", havingValue = "true")
public class HttpDocumentExtractionGateway implements DocumentExtractionGateway {
    private static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024;
    private final HttpClient client;
    private final ObjectMapper json;
    private final URI endpoint;
    private final Duration requestTimeout;

    public HttpDocumentExtractionGateway(ObjectMapper json,
                                         @Value("${app.ocr.base-url}") String baseUrl,
                                         @Value("${app.ocr.connect-timeout-seconds:3}") int connectTimeoutSeconds,
                                         @Value("${app.ocr.request-timeout-seconds:120}") int requestTimeoutSeconds) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("활성 OCR base URL은 필수입니다.");
        this.json = json;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/extract");
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build();
    }

    @Override public boolean isEnabled() { return true; }

    @Override
    public Result extract(byte[] content, String fileName, String contentType) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Content-Type", contentType)
                    .header("X-File-Name", asciiFileName(fileName))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.body().length > MAX_RESPONSE_BYTES)
                throw new DocumentExtractionException("OCR 응답 크기가 허용범위를 넘었습니다.");
            String body = new String(response.body(), StandardCharsets.UTF_8);
            if (response.statusCode() != 200) {
                String message = "OCR 서비스가 문서를 처리하지 못했습니다.";
                try {
                    String detail = json.readTree(body).path("message").asText();
                    if (!detail.isBlank()) message = detail;
                } catch (Exception ignored) {}
                throw new DocumentExtractionException(message);
            }
            JsonNode node = json.readTree(body);
            ExtractionRun.Method method;
            try { method = ExtractionRun.Method.valueOf(requiredText(node, "method")); }
            catch (IllegalArgumentException e) { throw new DocumentExtractionException("OCR 응답의 추출 방식이 올바르지 않습니다.", e); }
            if (method != ExtractionRun.Method.PDF_TEXT && method != ExtractionRun.Method.OCR)
                throw new DocumentExtractionException("OCR 응답의 추출 방식이 허용되지 않습니다.");
            if (!node.path("confidence").isNumber() || !node.path("pageCount").canConvertToInt()
                    || node.path("pageCount").asInt() < 1
                    || node.path("confidence").decimalValue().signum() < 0
                    || node.path("confidence").decimalValue().compareTo(new BigDecimal("100")) > 0)
                throw new DocumentExtractionException("OCR 응답의 수치 필드가 올바르지 않습니다.");
            if (!node.path("preprocessingApplied").isBoolean() || !node.path("reviewRequired").isBoolean())
                throw new DocumentExtractionException("OCR 응답의 상태 필드가 올바르지 않습니다.");
            return new Result(requiredText(node, "engineVersion"), method, requiredText(node, "text"),
                    node.path("confidence").decimalValue(), node.path("pageCount").asInt(),
                    node.path("preprocessingApplied").asBoolean(), node.path("reviewRequired").asBoolean(), body);
        } catch (DocumentExtractionException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DocumentExtractionException("OCR 요청이 중단되었습니다.", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new DocumentExtractionException("OCR 서비스에 연결할 수 없습니다.", e);
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) throw new DocumentExtractionException("OCR 응답에 " + field + " 값이 없습니다.");
        return value;
    }

    private String asciiFileName(String value) {
        if (value == null) return "document";
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "document" : safe.substring(0, Math.min(200, safe.length()));
    }
}
