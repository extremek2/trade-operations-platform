package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DocumentOfferCandidateParser {
    private static final int MAX_LINES = 200;
    private static final int MAX_NAME_LENGTH = 500;
    private static final Pattern TABLE_ROW = Pattern.compile(
            "^([A-Za-z0-9]+(?:[-_]\\s*[A-Za-z0-9]+)+)\\s+(.+?)\\s+([0-9]+(?:\\.[0-9]+)?)\\s+"
                    + "([A-Za-z가-힣]{1,30})\\s+([0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.([0-9]+))?\\s+([A-Za-z]{2})$");

    public record Candidate(String originalText, int sourceLine, String reviewedName, String supplierSku,
                            String quantityUnit, BigDecimal minimumQuantity, BigDecimal unitPrice,
                            String countryOfOrigin, List<String> warnings) {}

    public List<Candidate> parse(String extractedText) {
        if (extractedText == null || extractedText.isBlank())
            throw new BusinessException("추출된 문서 텍스트가 비어 있습니다.");
        List<Candidate> result = new ArrayList<>();
        String[] lines = extractedText.replace('\f', '\n').split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String text = lines[index].trim();
            if (text.isEmpty()) continue;
            if (result.size() == MAX_LINES)
                throw new BusinessException("추출 텍스트가 200행을 초과합니다. 문서를 나눠 검토해 주세요.");
            List<String> warnings = new ArrayList<>();
            if (text.length() > MAX_NAME_LENGTH) {
                warnings.add("긴 원문은 500자까지만 후보에 표시됩니다. 전체 추출 텍스트를 확인하세요.");
            }
            String original = text.substring(0, Math.min(text.length(), MAX_NAME_LENGTH));
            Matcher row = TABLE_ROW.matcher(text);
            if (text.length() <= 1000 && row.matches()) {
                String name = row.group(2).trim();
                BigDecimal quantity = decimal(row.group(3), 19, 6);
                String priceText = row.group(5).replace(",", "")
                        + (row.group(6) == null ? "" : "." + row.group(6));
                BigDecimal price = decimal(priceText, 19, 4);
                if (name.length() <= MAX_NAME_LENGTH && quantity != null && quantity.signum() > 0 && price != null) {
                    String sku = row.group(1).replaceAll("([-_])\\s+", "$1");
                    result.add(new Candidate(original, index + 1, name, sku, row.group(4),
                            quantity, price, row.group(7), List.copyOf(warnings)));
                    continue;
                }
                warnings.add("표 행의 이름·수량·단가 범위를 확인해 주세요.");
            }
            result.add(new Candidate(original, index + 1, null, null, null, null, null, null,
                    List.copyOf(warnings)));
        }
        if (result.isEmpty()) throw new BusinessException("추출된 문서 텍스트가 비어 있습니다.");
        return List.copyOf(result);
    }

    private BigDecimal decimal(String value, int maxPrecision, int maxScale) {
        try {
            BigDecimal number = new BigDecimal(value);
            return number.precision() <= maxPrecision && number.scale() <= maxScale ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
