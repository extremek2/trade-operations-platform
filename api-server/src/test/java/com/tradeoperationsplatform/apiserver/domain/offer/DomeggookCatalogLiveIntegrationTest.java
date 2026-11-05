package com.tradeoperationsplatform.apiserver.domain.offer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DOMEGGOOK_API_KEY", matches = ".+")
class DomeggookCatalogLiveIntegrationTest {
    @Test
    void readsAListAndDetailWithoutCreatingAnOrder() {
        DomeggookCatalogGateway gateway = new DomeggookCatalogGateway(System.getenv("DOMEGGOOK_API_KEY"));

        var rows = gateway.search("텀블러", DomeggookCatalogGateway.Mode.LIVE);
        assertThat(rows).isNotEmpty();

        WholesaleCatalogItem detail = gateway.detail(rows.get(0).itemId(), DomeggookCatalogGateway.Mode.LIVE).item();
        assertThat(detail.itemId()).isEqualTo(rows.get(0).itemId());
        assertThat(detail.sellerId()).isNotBlank();
        assertThat(detail.minimumQuantity()).isPositive();
        assertThat(detail.availableQuantity()).isNotNull().isNotNegative();
        assertThat(detail.productUrl()).startsWith("https://www.domeggook.com/");
    }
}
