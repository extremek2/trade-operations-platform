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

    @Test
    void acceptsCategoryAndPopularityCodesInSuccessfulLiveDetail() {
        DomeggookCatalogGateway gateway = new DomeggookCatalogGateway("configured-key");
        WholesaleCatalogItem listing = new WholesaleCatalogItem("DOMEGGOOK", "LIVE", "12345678", "seller-1",
                "실제 응답 형태", new BigDecimal("4500"), 10, null, List.of(), true, false,
                "3000", "https://www.domeggook.com/main/item/itemView.php?itemNo=12345678",
                LocalDateTime.now(), true);
        String detail = """
                <?xml version="1.0" encoding="EUC-KR"?>
                <domeggook>
                  <basis><no>12345678</no><title>실제 응답 형태</title></basis>
                  <price><dome>4500</dome></price>
                  <qty><inventory>100</inventory><domeMoq>10</domeMoq></qty>
                  <seller><id>seller-1</id></seller>
                  <deli><fromOversea>false</fromOversea><dome><fee>3000</fee></dome></deli>
                  <category><parents><elem><code>11_00_00_00_00</code></elem></parents><current><code>11_11_03_00_00</code></current></category>
                  <popular><code>01_04</code></popular>
                </domeggook>
                """;

        WholesaleCatalogItem parsed = gateway.parseDetail(detail, listing, DomeggookCatalogGateway.Mode.LIVE);

        assertThat(parsed.itemId()).isEqualTo("12345678");
        assertThat(parsed.unitPrice()).isEqualByComparingTo("4500");
        assertThat(parsed.availableQuantity()).isEqualTo(100);
    }

    @Test
    void rejectsOnlyTheApiErrorEnvelopeNotArbitraryNestedCodes() {
        DomeggookCatalogGateway gateway = new DomeggookCatalogGateway("configured-key");

        assertThatThrownBy(() -> gateway.parseList(
                "<errors><code>401</code><message>API authentication failed</message></errors>",
                DomeggookCatalogGateway.Mode.LIVE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("401");
    }
}
