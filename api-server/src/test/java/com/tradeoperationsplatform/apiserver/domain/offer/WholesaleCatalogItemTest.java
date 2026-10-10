package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WholesaleCatalogItemTest {
    @Test
    void normalizesTiersAndChoosesTheHighestApplicableMinimum() {
        List<WholesalePriceTier> tiers = DomeggookCatalogGateway.priceTiers("50+3800|1+4500|20+4100");
        WholesaleCatalogItem item = new WholesaleCatalogItem("DOMEGGOOK", "SAMPLE", "1002", "seller",
                "테스트", null, 5, 85, tiers, true, false, null, null, LocalDateTime.now(), true);

        assertThat(tiers).extracting(WholesalePriceTier::minimumQuantity)
                .containsExactly(new BigDecimal("1"), new BigDecimal("20"), new BigDecimal("50"));
        assertThat(item.unitPriceFor(new BigDecimal("5"))).isEqualByComparingTo("4500");
        assertThat(item.unitPriceFor(new BigDecimal("20"))).isEqualByComparingTo("4100");
        assertThat(item.unitPriceFor(new BigDecimal("84"))).isEqualByComparingTo("3800");
        assertThatThrownBy(() -> item.unitPriceFor(new BigDecimal("4"))).hasMessageContaining("최소구매수량");
        assertThatThrownBy(() -> item.unitPriceFor(new BigDecimal("86"))).hasMessageContaining("재고");
    }

    @Test
    void rejectsMalformedOrDuplicateTiers() {
        assertThatThrownBy(() -> DomeggookCatalogGateway.priceTiers("1+4500|broken"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("수량별 단가");
        assertThatThrownBy(() -> DomeggookCatalogGateway.priceTiers("1+4500|1+4100"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("수량별 단가");
    }
}
