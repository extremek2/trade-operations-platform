package com.tradeoperationsplatform.apiserver.domain.offer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WholesaleCatalogItem(
        String platform, String mode, String itemId, String sellerId, String title,
        BigDecimal unitPrice, Integer minimumQuantity, Integer availableQuantity,
        String priceTiers, boolean businessOnly, boolean overseasShipping,
        String shippingFee, String productUrl, LocalDateTime fetchedAt, boolean reviewRequired
) {}
