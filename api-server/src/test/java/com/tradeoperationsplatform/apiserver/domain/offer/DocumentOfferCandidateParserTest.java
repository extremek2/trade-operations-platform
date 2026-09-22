package com.tradeoperationsplatform.apiserver.domain.offer;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentOfferCandidateParserTest {
    private final DocumentOfferCandidateParser parser = new DocumentOfferCandidateParser();

    @Test
    void mapsRenderedPdfTableRowsWithoutConfirmingUnrelatedText() throws Exception {
        String text = Files.readString(Path.of("src/test/resources/fixtures/ocr/supplier-quotation-layout.txt"));
        var candidates = parser.parse(text);
        var productRows = candidates.stream().filter(candidate -> candidate.supplierSku() != null).toList();

        assertThat(productRows).hasSize(4);
        assertThat(productRows.get(0).sourceLine()).isEqualTo(13);
        assertThat(productRows.get(0).reviewedName()).isEqualTo("스테인리스 텀블러");
        assertThat(productRows.get(0).supplierSku()).isEqualTo("JP-001");
        assertThat(productRows.get(0).minimumQuantity()).isEqualByComparingTo("24");
        assertThat(productRows.get(0).unitPrice()).isEqualByComparingTo("850");
        assertThat(productRows.get(2).unitPrice()).isEqualByComparingTo("1280");
        assertThat(productRows.get(3).quantityUnit()).isEqualTo("SET");
        assertThat(candidates).anySatisfy(candidate -> {
            assertThat(candidate.originalText()).startsWith("Terms:");
            assertThat(candidate.reviewedName()).isNull();
        });
    }

    @Test
    void leavesUncertainRowsForHumanReviewAndRejectsSilentOverflow() {
        var candidates = parser.parse("JP-001 텀블러 24 EA 가격미정 JP\n"
                + "JP-002 파우치 10 EA 1,28 JP\n알 수 없는 부대 비용");
        assertThat(candidates).hasSize(3);
        assertThat(candidates.get(0).originalText()).contains("가격미정");
        assertThat(candidates.get(0).unitPrice()).isNull();
        assertThat(candidates.get(1).unitPrice()).isNull();
        assertThatThrownBy(() -> parser.parse("상품\n".repeat(201)))
                .hasMessageContaining("200행");
    }

    @Test
    void normalizesObservedOcrSkuGapButPreservesRecognizedNameForReview() {
        var candidate = parser.parse("JP- 001 스테인리스 Yee 24 EA 850 JP").get(0);
        assertThat(candidate.originalText()).isEqualTo("JP- 001 스테인리스 Yee 24 EA 850 JP");
        assertThat(candidate.supplierSku()).isEqualTo("JP-001");
        assertThat(candidate.reviewedName()).isEqualTo("스테인리스 Yee");
    }
}
