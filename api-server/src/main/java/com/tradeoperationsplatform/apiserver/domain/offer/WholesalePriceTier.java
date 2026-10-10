package com.tradeoperationsplatform.apiserver.domain.offer;

import java.math.BigDecimal;

public record WholesalePriceTier(BigDecimal minimumQuantity, BigDecimal unitPrice) {
    public WholesalePriceTier {
        if (minimumQuantity == null || minimumQuantity.signum() <= 0)
            throw new IllegalArgumentException("구간가 최소수량은 양수여야 합니다.");
        if (unitPrice == null || unitPrice.signum() < 0)
            throw new IllegalArgumentException("구간가 단가는 음수일 수 없습니다.");
    }
}
