package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class DomeggookCatalogGateway {
    public enum Mode { SAMPLE, LIVE }
    public record Snapshot(WholesaleCatalogItem item, String raw) {}

    private static final String API_URL = "https://www.domeggook.com/ssl/api/";
    private static final Charset EUC_KR = Charset.forName("EUC-KR");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final String apiKey;
    private LocalDate quotaDate = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private int requestsToday;
    private long lastRequestNanos;

    public DomeggookCatalogGateway(@Value("${app.wholesale.domeggook-api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean liveConfigured() { return !apiKey.isBlank(); }

    public List<WholesaleCatalogItem> search(String keyword, Mode mode) {
        if (keyword == null || keyword.isBlank() || keyword.trim().length() > 80)
            throw new BusinessException("검색어를 1~80자로 입력해 주세요.");
        String raw = mode == Mode.SAMPLE ? sample("domeggook-list.xml")
                : request("4.1", "getItemList", "kw=" + encode(keyword.trim()) + "&sz=20&pg=1");
        List<WholesaleCatalogItem> rows = parseList(raw, mode);
        if (mode == Mode.SAMPLE) {
            String term = keyword.trim().toLowerCase(Locale.ROOT);
            return rows.stream().filter(item -> item.title().toLowerCase(Locale.ROOT).contains(term)
                    || item.itemId().contains(term)).toList();
        }
        return rows;
    }

    public Snapshot detail(String itemId, Mode mode) {
        if (itemId == null || !itemId.matches("[0-9]{1,12}"))
            throw new BusinessException("상품번호는 숫자여야 합니다.");
        String listRaw = mode == Mode.SAMPLE ? sample("domeggook-list.xml")
                : request("4.1", "getItemList", "itemNo=" + itemId + "&sz=1");
        WholesaleCatalogItem listing = parseList(listRaw, mode).stream()
                .filter(item -> item.itemId().equals(itemId)).findFirst()
                .orElseThrow(() -> new BusinessException("현재 판매 중인 상품을 찾을 수 없습니다."));
        String raw = mode == Mode.SAMPLE ? sample("domeggook-detail-" + itemId + ".xml")
                : request("4.6", "getItemView", "no=" + itemId);
        return new Snapshot(parseDetail(raw, listing, mode), listRaw + "\n<!-- ITEM DETAIL -->\n" + raw);
    }

    List<WholesaleCatalogItem> parseList(String raw, Mode mode) {
        Document doc = parse(raw);
        Element list = first(doc.getDocumentElement(), "list");
        if (list == null) throw new BusinessException("도매꾹 상품 목록 형식이 예상과 다릅니다.");
        List<WholesaleCatalogItem> rows = new ArrayList<>();
        for (Element item : children(list, "item")) {
            String id = value(item, "no");
            String title = value(item, "title");
            if (id == null || title == null) continue;
            Element deli = child(item, "deli");
            rows.add(new WholesaleCatalogItem("DOMEGGOOK", mode.name(), id, value(item, "id"), title,
                    decimal(value(item, "price")), integer(value(item, "unitQty")), null, List.of(),
                    Boolean.parseBoolean(value(item, "comOnly")), Boolean.parseBoolean(value(deli, "fromOversea")),
                    value(deli, "fee"), mode == Mode.SAMPLE ? null : safeProductUrl(value(item, "url"), id),
                    LocalDateTime.now(), true));
        }
        return rows;
    }

    WholesaleCatalogItem parseDetail(String raw, WholesaleCatalogItem listing, Mode mode) {
        Document doc = parse(raw);
        Element root = doc.getDocumentElement();
        Element basis = first(root, "basis");
        Element price = first(root, "price");
        Element qty = first(root, "qty");
        Element seller = first(root, "seller");
        Element deli = first(root, "deli");
        if (basis == null || price == null || qty == null || seller == null)
            throw new BusinessException("도매꾹 상품 상세 형식이 예상과 다릅니다.");
        if (!listing.itemId().equals(value(basis, "no")))
            throw new BusinessException("상품 목록과 상세의 상품번호가 다릅니다.");
        String sellerId = value(seller, "id");
        if (sellerId == null || !sellerId.equals(listing.sellerId()))
            throw new BusinessException("상품 목록과 상세의 판매자가 다릅니다.");
        String tierText = value(price, "dome");
        boolean tiered = tierText != null && tierText.contains("+");
        List<WholesalePriceTier> priceTiers = tiered ? priceTiers(tierText) : List.of();
        BigDecimal unitPrice = tiered ? null : decimal(tierText);
        Integer minimum = integer(value(qty, "domeMoq"));
        if (minimum == null) minimum = listing.minimumQuantity();
        if (tierText == null || (!tiered && unitPrice == null) || minimum == null || minimum < 1)
            throw new BusinessException("도매 가격 또는 최소구매수량을 확인할 수 없습니다.");
        Element domeDeli = child(deli, "dome");
        String shippingFee = value(domeDeli, "fee");
        if (value(domeDeli, "tbl") != null) shippingFee = "수량별 배송비: " + value(domeDeli, "tbl");
        if (shippingFee == null) shippingFee = listing.shippingFee();
        return new WholesaleCatalogItem("DOMEGGOOK", mode.name(), listing.itemId(), sellerId,
                value(basis, "title") == null ? listing.title() : value(basis, "title"), unitPrice,
                minimum, integer(value(qty, "inventory")), priceTiers,
                listing.businessOnly(), Boolean.parseBoolean(value(deli, "fromOversea")),
                shippingFee, listing.productUrl(), LocalDateTime.now(), true);
    }

    private synchronized String request(String version, String apiMode, String query) {
        if (!liveConfigured()) throw new BusinessException("도매꾹 API 키가 설정되지 않았습니다. 샘플 모드를 이용해 주세요.");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        if (!today.equals(quotaDate)) { quotaDate = today; requestsToday = 0; }
        if (requestsToday >= 14_000) throw new BusinessException("오늘의 도매꾹 조회 한도에 도달했습니다.");
        long elapsed = System.nanoTime() - lastRequestNanos;
        long interval = Duration.ofMillis(350).toNanos();
        if (lastRequestNanos != 0 && elapsed < interval) {
            try { Thread.sleep(Duration.ofNanos(interval - elapsed).toMillis() + 1); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("도매꾹 API 조회가 중단되었습니다.");
            }
        }
        lastRequestNanos = System.nanoTime();
        requestsToday++;
        // aid is a query parameter required by the provider. Never log the full URI or an upstream exception.
        URI uri = URI.create(API_URL + "?ver=" + version + "&mode=" + apiMode + "&aid="
                + encode(apiKey) + "&market=dome&om=xml&" + query);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var body = response.body()) {
                if (response.statusCode() == 429) throw new BusinessException("도매꾹 API 호출 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.");
                if (response.statusCode() != 200) throw new BusinessException("도매꾹 API 조회에 실패했습니다 (HTTP " + response.statusCode() + ").");
                bytes = body.readNBytes(2_000_001);
            }
            if (bytes.length > 2_000_000) throw new BusinessException("도매꾹 API 응답이 너무 큽니다.");
            String head = new String(bytes, 0, Math.min(bytes.length, 128), StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
            Charset charset = head.contains("EUC-KR") || head.contains("EUC_KR") ? EUC_KR : StandardCharsets.UTF_8;
            return new String(bytes, charset);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("도매꾹 API 연결에 실패했습니다.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("도매꾹 API 조회가 중단되었습니다.");
        }
    }

    private static Document parse(String raw) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(raw)));
            Element root = doc.getDocumentElement();
            Element error = "error".equalsIgnoreCase(root.getTagName()) || "errors".equalsIgnoreCase(root.getTagName())
                    ? root : child(root, "error");
            if (error == null) error = child(root, "errors");
            if (error != null) {
                String code = value(error, "code");
                throw new BusinessException("도매꾹 API 오류 코드: " + (code == null ? "UNKNOWN" : code));
            }
            return doc;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("도매꾹 API 응답을 읽을 수 없습니다.");
        }
    }

    private String sample(String name) {
        try (var stream = getClass().getResourceAsStream("/catalog/" + name)) {
            if (stream == null) throw new BusinessException("샘플 상품을 찾을 수 없습니다.");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new BusinessException("샘플 상품을 읽을 수 없습니다."); }
    }

    private static String encode(String value) { return URLEncoder.encode(value, EUC_KR); }
    private static BigDecimal decimal(String value) {
        if (value == null) return null;
        try { return new BigDecimal(value.replace(",", "")); }
        catch (NumberFormatException e) { return null; }
    }
    private static Integer integer(String value) {
        if (value == null) return null;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException e) { return null; }
    }
    static List<WholesalePriceTier> priceTiers(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            List<WholesalePriceTier> tiers = new ArrayList<>();
            for (String token : value.split("\\|")) {
                String[] fields = token.trim().split("\\+", -1);
                if (fields.length != 2) throw new IllegalArgumentException();
                tiers.add(new WholesalePriceTier(decimalRequired(fields[0]), decimalRequired(fields[1])));
            }
            tiers.sort(Comparator.comparing(WholesalePriceTier::minimumQuantity));
            for (int index = 1; index < tiers.size(); index++) {
                if (tiers.get(index - 1).minimumQuantity().compareTo(tiers.get(index).minimumQuantity()) == 0)
                    throw new IllegalArgumentException();
            }
            return List.copyOf(tiers);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("수량별 단가 형식을 확인할 수 없습니다.");
        }
    }
    private static BigDecimal decimalRequired(String value) {
        BigDecimal parsed = decimal(value);
        if (parsed == null) throw new IllegalArgumentException();
        return parsed;
    }
    private static String safeProductUrl(String candidate, String id) {
        if (candidate != null && candidate.startsWith("https://") && candidate.length() <= 255) return candidate;
        return "https://www.domeggook.com/main/item/itemView.php?itemNo=" + id;
    }
    private static Element first(Element parent, String name) {
        if (parent == null) return null;
        NodeList nodes = parent.getElementsByTagName(name);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }
    private static Element child(Element parent, String name) {
        if (parent == null) return null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element element && element.getTagName().equals(name)) return element;
        return null;
    }
    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element element && element.getTagName().equals(name)) result.add(element);
        return result;
    }
    private static String value(Element parent, String name) {
        Element element = child(parent, name);
        if (element == null) return null;
        String text = element.getTextContent().trim();
        return text.isEmpty() ? null : text;
    }
}
