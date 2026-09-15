package com.tradeoperationsplatform.apiserver.domain.offer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierOfferDraftTest {
    @Test
    void everyExtractedLineMustBeResolvedBeforeConfirmation() {
        SupplierOfferDraft draft = new SupplierOfferDraft(null, null, null, null, "JPY");
        SupplierOfferDraftLine first = new SupplierOfferDraftLine(draft, 1, "원본 A", "sheet1!A2");
        SupplierOfferDraftLine second = new SupplierOfferDraftLine(draft, 2, "원본 B", "sheet1!A3");
        draft.addLine(first);
        draft.addLine(second);

        first.review("확인 A", "A-1", "EA", new BigDecimal("12"), new BigDecimal("280"), null, null);
        assertThatThrownBy(draft::confirm).isInstanceOf(IllegalStateException.class);
        second.exclude();
        draft.confirm();
        assertThat(draft.getStatus()).isEqualTo(SupplierOfferDraft.Status.CONFIRMED);
        assertThat(first.getOriginalName()).isEqualTo("원본 A");
        assertThat(first.getReviewedName()).isEqualTo("확인 A");
        assertThatThrownBy(second::exclude).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> first.review("변경", null, null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void uncertainValuesRemainNullAndInvalidNumericValuesAreRejected() {
        SupplierOfferDraft draft = new SupplierOfferDraft(null, null, null, null, "KRW");
        SupplierOfferDraftLine line = new SupplierOfferDraftLine(draft, 1, "미확인 상품", null);
        draft.addLine(line);
        line.review("미확인 상품", null, null, null, null, null, null);
        assertThat(line.getMinimumQuantity()).isNull();
        assertThat(line.getUnitPrice()).isNull();
        assertThatThrownBy(() -> line.review("상품", null, "EA", BigDecimal.ZERO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line.review("상품", null, "EA", null, new BigDecimal("-1"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
