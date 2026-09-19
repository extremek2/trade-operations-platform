package com.tradeoperationsplatform.apiserver.domain.offer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierOfferFileParserTest {
    private final SupplierOfferFileParser parser = new SupplierOfferFileParser();

    @Test
    void parsesSyntheticXlsxAndKeepsRowLevelProblemsForReview() throws IOException {
        byte[] content;
        try (InputStream input = Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/supplier-offer-synthetic.xlsx"))) {
            content = input.readAllBytes();
        }

        SupplierOfferFileParser.ParsedOffer result = parser.parse("supplier-offer.xlsx", content);

        assertThat(result.format()).isEqualTo(SupplierOfferFileParser.Format.XLSX);
        assertThat(result.sheetName()).isEqualTo("상품제안");
        assertThat(result.lines()).hasSize(4);
        assertThat(result.lines().get(0).originalName()).isEqualTo("스테인리스 텀블러");
        assertThat(result.lines().get(0).supplierSku()).isEqualTo("JP-001");
        assertThat(result.lines().get(0).minimumQuantity()).isEqualByComparingTo("24");
        assertThat(result.lines().get(0).unitPrice()).isEqualByComparingTo("850");
        assertThat(result.lines().get(0).errors()).isEmpty();
        assertThat(result.lines().get(2).unitPrice()).isNull();
        assertThat(result.lines().get(2).errors()).containsExactly("단가가 숫자가 아닙니다: 가격미정");
        assertThat(result.lines().get(3).errors()).containsExactly("MOQ가 있지만 수량 단위가 없습니다.");
        assertThat(result.lines().get(3).sourceLocation()).isEqualTo("상품제안!row 5");
    }

    @Test
    void parsesUtf8BomQuotedCsvAndEnglishAliases() {
        String csv = "\uFEFFproduct_name,supplier_sku,min_order_qty,uom,price,country,notes\r\n"
                + "\"Bottle, steel\",S-1,12,EA,9.50,kr,\"quote \"\"A\"\"\"\r\n";

        SupplierOfferFileParser.ParsedOffer result = parser.parse("offer.CSV", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.lines()).singleElement().satisfies(line -> {
            assertThat(line.originalName()).isEqualTo("Bottle, steel");
            assertThat(line.supplierSku()).isEqualTo("S-1");
            assertThat(line.minimumQuantity()).isEqualByComparingTo("12");
            assertThat(line.unitPrice()).isEqualByComparingTo("9.50");
            assertThat(line.countryOfOrigin()).isEqualTo("KR");
            assertThat(line.notes()).isEqualTo("quote \"A\"");
            assertThat(line.sourceLocation()).isEqualTo("CSV row 2");
        });
    }

    @Test
    void rejectsUnsupportedAndBrokenInputsBeforeExtraction() {
        assertThatThrownBy(() -> parser.parse("offer.xls", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CSV 또는 XLSX");
        assertThatThrownBy(() -> parser.parse("offer.xlsx", "not a zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("올바른 XLSX");
        assertThatThrownBy(() -> parser.parse("offer.csv", "상품명\n\"열린 값".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("따옴표");
        assertThatThrownBy(() -> parser.parse("offer.csv", new byte[SupplierOfferFileParser.MAX_FILE_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2MB");
    }
}
