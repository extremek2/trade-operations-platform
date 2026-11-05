package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import com.tradeoperationsplatform.apiserver.domain.costing.entity.CostScenario;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import com.tradeoperationsplatform.apiserver.domain.product.entity.Product;
import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseOrderCatalogRecheckTest {
    @Test
    void changedOrFailedRecheckInvalidatesApprovalUntilTheCatalogPassesAgain() {
        PurchaseOrder order = domesticOrder();
        order.requireCatalogRecheck();
        assertThatThrownBy(order::approve).hasMessageContaining("재확인");

        order.recordCatalogRecheck(PurchaseOrder.CatalogRecheckStatus.PASSED, "일치");
        order.approve();
        assertThat(order.getStatus()).isEqualTo(PurchaseOrder.Status.APPROVED);

        order.recordCatalogRecheck(PurchaseOrder.CatalogRecheckStatus.CHANGED, "단가 변경");
        assertThat(order.getStatus()).isEqualTo(PurchaseOrder.Status.DRAFT);
        assertThat(order.getApprovedAt()).isNull();
        assertThatThrownBy(order::approve).hasMessageContaining("재확인");

        order.recordCatalogRecheck(PurchaseOrder.CatalogRecheckStatus.PASSED, "다시 일치");
        order.approve();
        order.recordCatalogRecheck(PurchaseOrder.CatalogRecheckStatus.FAILED, "조회 실패");
        assertThat(order.getStatus()).isEqualTo(PurchaseOrder.Status.DRAFT);
        assertThat(order.getApprovedAt()).isNull();
    }

    private PurchaseOrder domesticOrder() {
        Organization organization = mock(Organization.class);
        AppUser creator = mock(AppUser.class);
        CostScenario scenario = mock(CostScenario.class);
        SupplierQuote quote = mock(SupplierQuote.class);
        BusinessPartner supplier = mock(BusinessPartner.class);
        Product product = mock(Product.class);
        when(organization.getId()).thenReturn(1L);
        when(scenario.getTotalLandedCostKrw()).thenReturn(BigDecimal.valueOf(82_000));
        when(scenario.getQuotedUnitPrice()).thenReturn(BigDecimal.valueOf(4_100));
        when(scenario.getOrganization()).thenReturn(organization);
        when(scenario.getQuote()).thenReturn(quote);
        when(scenario.getProduct()).thenReturn(product);
        when(scenario.getQuoteCurrency()).thenReturn("KRW");
        when(scenario.getQuoteRevisionNumber()).thenReturn(1);
        when(quote.getId()).thenReturn(2L);
        when(quote.getSupplier()).thenReturn(supplier);
        when(quote.getQuoteNumber()).thenReturn("Q-CATALOG");
        when(supplier.getName()).thenReturn("도매 공급처");
        when(product.getId()).thenReturn(3L);
        return new PurchaseOrder(organization, creator, List.of(scenario), "PO-CATALOG", null, null,
                PurchaseOrder.ProcurementRoute.DOMESTIC_STOCK);
    }
}
