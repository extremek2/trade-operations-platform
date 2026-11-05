package com.tradeoperationsplatform.apiserver.domain.offer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record WholesaleCatalogItem(
        String platform, String mode, String itemId, String sellerId, String title,
        BigDecimal unitPrice, Integer minimumQuantity, Integer availableQuantity,
        List<WholesalePriceTier> priceTiers, boolean businessOnly, boolean overseasShipping,
        String shippingFee, String productUrl, LocalDateTime fetchedAt, boolean reviewRequired
) {
    public WholesaleCatalogItem {
        priceTiers = priceTiers == null ? List.of() : List.copyOf(priceTiers);
    }

    public BigDecimal unitPriceFor(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0)
            throw new IllegalArgumentException("희망 수량은 양수여야 합니다.");
        if (minimumQuantity != null && quantity.compareTo(BigDecimal.valueOf(minimumQuantity)) < 0)
            throw new IllegalArgumentException("희망 수량이 최소구매수량보다 적습니다.");
        if (availableQuantity != null && quantity.compareTo(BigDecimal.valueOf(availableQuantity)) > 0)
            throw new IllegalArgumentException("희망 수량이 조회 당시 재고보다 많습니다.");
        if (priceTiers.isEmpty()) {
            if (unitPrice == null) throw new IllegalArgumentException("단가를 확인할 수 없습니다.");
            return unitPrice;
        }
        return priceTiers.stream()
                .filter(tier -> quantity.compareTo(tier.minimumQuantity()) >= 0)
                .max(Comparator.comparing(WholesalePriceTier::minimumQuantity))
                .orElseThrow(() -> new IllegalArgumentException("희망 수량에 적용할 구간가가 없습니다."))
                .unitPrice();
    }
}
