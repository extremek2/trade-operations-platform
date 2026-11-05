package com.tradeoperationsplatform.apiserver.domain.offer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ocr.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledDocumentExtractionGateway implements DocumentExtractionGateway {
    @Override public boolean isEnabled() { return false; }
    @Override public Result extract(byte[] content, String fileName, String contentType) {
        throw new DocumentExtractionException("OCR 연결이 비활성화되어 있습니다.");
    }
}
