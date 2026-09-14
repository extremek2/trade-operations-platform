package com.productresearch.apiserver.domain.sourcing.entity;

import com.productresearch.apiserver.domain.identity.entity.AppUser;
import com.productresearch.apiserver.domain.identity.entity.Organization;
import com.productresearch.apiserver.domain.partner.entity.BusinessPartner;
import com.productresearch.apiserver.domain.product.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class SupplierQuoteTest {

    private final Organization organization = new Organization(
            "테스트 조직", Organization.Type.SHIPPER, null, null, null);
    private final AppUser creator = AppUser.registered(
            "owner@example.test", "encoded", "담당자", null);

    @Test
    void quoteKeepsUnknownPriceNullableDuringEarlySourcing() {
        BusinessPartner supplier = new BusinessPartner(
                organization, "공급사", "cn", null, BusinessPartner.Role.SUPPLIER);
        Product product = new Product(organization, creator, "상품 후보", null, null, null, null);
        SupplierQuote quote = new SupplierQuote(
                organization, creator, supplier, "Q-1", "usd",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);

        quote.addLine(new SupplierQuoteLine(
                quote, product, 1, null, "가격 확인 중", new BigDecimal("100"),
                "EA", null, "cn", null, null));

        assertThat(quote.getCurrency()).isEqualTo("USD");
        assertThat(quote.getLines()).singleElement()
                .extracting(SupplierQuoteLine::getUnitPrice, SupplierQuoteLine::getCountryOfOrigin)
                .containsExactly(null, "CN");
    }

    @Test
    void nonSupplierPartnerCannotOwnSupplierQuote() {
        BusinessPartner forwarder = new BusinessPartner(
                organization, "포워더", "KR", null, BusinessPartner.Role.FORWARDER);

        assertThatThrownBy(() -> new SupplierQuote(
                organization, creator, forwarder, null, "KRW", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("공급처 역할");
    }

    @Test
    void onlyDraftIsEditedAndReceivedQuoteCreatesANewRevision() {
        BusinessPartner supplier = new BusinessPartner(
                organization, "공급사", "KR", null, BusinessPartner.Role.SUPPLIER);
        SupplierQuote quote = new SupplierQuote(
                organization, creator, supplier, "Q-2", "KRW", null, null, null);

        quote.revise("Q-2", "usd", null, null, "초안 수정");
        assertThat(quote.getCurrency()).isEqualTo("USD");
        quote.changeStatus(SupplierQuote.Status.RECEIVED);

        assertThatThrownBy(() -> quote.revise("Q-2", "KRW", null, null, null))
                .isInstanceOf(IllegalStateException.class);
        SupplierQuote revision = quote.createRevision(creator, "USD", null, null, "공급사 정정");
        assertThat(revision.getPreviousQuote()).isSameAs(quote);
        assertThat(revision.getRevisionNumber()).isEqualTo(2);
        assertThat(revision.getStatus()).isEqualTo(SupplierQuote.Status.DRAFT);
        assertThatThrownBy(() -> revision.revise("Q-3", "USD", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("견적번호");
    }
}
