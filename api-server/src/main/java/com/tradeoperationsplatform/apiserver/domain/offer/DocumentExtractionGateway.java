package com.tradeoperationsplatform.apiserver.domain.offer;

import java.math.BigDecimal;

public interface DocumentExtractionGateway {
    boolean isEnabled();
    Result extract(byte[] content, String fileName, String contentType);

    record Result(String engineVersion, ExtractionRun.Method method, String text, BigDecimal confidence,
                  int pageCount, boolean preprocessingApplied, boolean reviewRequired, String rawPayload) {}
}
