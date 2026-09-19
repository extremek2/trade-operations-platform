package com.tradeoperationsplatform.apiserver.domain.offer;

import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class SupplierOfferFileParser {
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_UNCOMPRESSED_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 200;
    private static final int MAX_ROWS = 200;
    private static final int MAX_COLUMNS = 50;
    public static final String EXTRACTOR_VERSION = "tabular-v1";

    public enum Format { CSV, XLSX }
    private enum Field { NAME, SKU, MOQ, UNIT, PRICE, ORIGIN, NOTES }

    public record ParsedOffer(Format format, String sheetName, List<ParsedLine> lines) {}
    public record ParsedLine(int sourceRow, String sourceLocation, String originalName,
                             String supplierSku, String quantityUnit, BigDecimal minimumQuantity,
                             BigDecimal unitPrice, String countryOfOrigin, String notes,
                             List<String> errors) {}
    private record Cell(String value, boolean formula) {
        static Cell blank() { return new Cell("", false); }
    }
    private record TabularData(String sheetName, List<List<Cell>> rows) {}

    public ParsedOffer parse(String fileName, byte[] content) {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("원본 파일명이 필요합니다.");
        if (content == null || content.length == 0) throw new IllegalArgumentException("비어 있는 파일은 가져올 수 없습니다.");
        if (content.length > MAX_FILE_BYTES) throw new IllegalArgumentException("파일은 2MB 이하여야 합니다.");
        String lower = fileName.toLowerCase(Locale.ROOT);
        TabularData data;
        Format format;
        if (lower.endsWith(".csv")) {
            format = Format.CSV;
            data = parseCsv(content);
        } else if (lower.endsWith(".xlsx")) {
            format = Format.XLSX;
            data = parseXlsx(content);
        } else {
            throw new IllegalArgumentException("CSV 또는 XLSX 파일만 가져올 수 있습니다.");
        }
        return new ParsedOffer(format, data.sheetName(), mapRows(data));
    }

    private TabularData parseCsv(byte[] content) {
        String text;
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            text = decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("CSV는 UTF-8 인코딩이어야 합니다.");
        }
        if (text.indexOf('\0') >= 0) throw new IllegalArgumentException("CSV에 허용되지 않는 바이너리 문자가 있습니다.");
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<List<Cell>> rows = new ArrayList<>();
        List<Cell> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        value.append('"'); index++;
                    } else quoted = false;
                } else value.append(current);
            } else if (current == '"' && value.length() == 0) quoted = true;
            else if (current == ',') { row.add(new Cell(value.toString(), false)); value.setLength(0); }
            else if (current == '\n' || current == '\r') {
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                row.add(new Cell(value.toString(), false)); value.setLength(0);
                addCsvRow(rows, row); row = new ArrayList<>();
            } else value.append(current);
        }
        if (quoted) throw new IllegalArgumentException("CSV 따옴표가 닫히지 않았습니다.");
        if (value.length() > 0 || !row.isEmpty()) { row.add(new Cell(value.toString(), false)); addCsvRow(rows, row); }
        return new TabularData("CSV", rows);
    }

    private void addCsvRow(List<List<Cell>> rows, List<Cell> row) {
        if (row.size() > MAX_COLUMNS) throw new IllegalArgumentException("파일 열은 50개를 초과할 수 없습니다.");
        if (rows.size() >= MAX_ROWS + 10) throw new IllegalArgumentException("파일 데이터 행은 200개를 초과할 수 없습니다.");
        rows.add(List.copyOf(row));
    }

    private TabularData parseXlsx(byte[] content) {
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K')
            throw new IllegalArgumentException("올바른 XLSX 파일이 아닙니다.");
        Map<String, byte[]> entries = unzip(content);
        if (entries.keySet().stream().anyMatch(name -> name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")))
            throw new IllegalArgumentException("매크로가 포함된 파일은 가져올 수 없습니다.");
        byte[] workbookXml = requiredEntry(entries, "xl/workbook.xml");
        Document workbook = xml(workbookXml);
        NodeList sheets = workbook.getElementsByTagNameNS("*", "sheet");
        Element chosen = null;
        for (int index = 0; index < sheets.getLength(); index++) {
            Element candidate = (Element) sheets.item(index);
            if (!"hidden".equalsIgnoreCase(candidate.getAttribute("state")) &&
                    !"veryHidden".equalsIgnoreCase(candidate.getAttribute("state"))) { chosen = candidate; break; }
        }
        if (chosen == null) throw new IllegalArgumentException("읽을 수 있는 XLSX 시트가 없습니다.");
        String sheetName = chosen.getAttribute("name");
        String relationId = chosen.getAttributeNS(
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
        if (relationId.isBlank()) relationId = chosen.getAttribute("r:id");
        Document relations = xml(requiredEntry(entries, "xl/_rels/workbook.xml.rels"));
        String target = null;
        NodeList relationNodes = relations.getElementsByTagNameNS("*", "Relationship");
        for (int index = 0; index < relationNodes.getLength(); index++) {
            Element relation = (Element) relationNodes.item(index);
            if (relationId.equals(relation.getAttribute("Id"))) { target = relation.getAttribute("Target"); break; }
        }
        if (target == null || target.isBlank()) throw new IllegalArgumentException("XLSX 시트 연결정보를 찾을 수 없습니다.");
        String sheetPath = normalizeSheetPath(target);
        List<String> sharedStrings = entries.containsKey("xl/sharedStrings.xml")
                ? readSharedStrings(entries.get("xl/sharedStrings.xml")) : List.of();
        return new TabularData(sheetName, readSheet(requiredEntry(entries, sheetPath), sharedStrings));
    }

    private Map<String, byte[]> unzip(byte[] content) {
        Map<String, byte[]> entries = new HashMap<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (entries.size() >= MAX_ZIP_ENTRIES) throw new IllegalArgumentException("XLSX 내부 파일 수가 제한을 초과했습니다.");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_UNCOMPRESSED_BYTES) throw new IllegalArgumentException("XLSX 압축 해제 크기가 제한을 초과했습니다.");
                    output.write(buffer, 0, read);
                }
                String name = entry.getName().replace('\\', '/');
                if (entries.putIfAbsent(name, output.toByteArray()) != null)
                    throw new IllegalArgumentException("XLSX 내부 파일명이 중복되었습니다.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("XLSX 압축 내용을 읽을 수 없습니다.");
        }
        return entries;
    }

    private String normalizeSheetPath(String target) {
        String cleaned = target.replace('\\', '/');
        Path path = cleaned.startsWith("/") ? Path.of(cleaned.substring(1)).normalize()
                : Path.of("xl").resolve(cleaned).normalize();
        String value = path.toString().replace('\\', '/');
        if (!value.startsWith("xl/worksheets/")) throw new IllegalArgumentException("허용되지 않는 XLSX 시트 경로입니다.");
        return value;
    }

    private List<String> readSharedStrings(byte[] content) {
        Document document = xml(content);
        List<String> values = new ArrayList<>();
        NodeList items = document.getElementsByTagNameNS("*", "si");
        for (int index = 0; index < items.getLength(); index++) values.add(descendantText((Element) items.item(index), "t"));
        return values;
    }

    private List<List<Cell>> readSheet(byte[] content, List<String> sharedStrings) {
        Document document = xml(content);
        NodeList rowNodes = document.getElementsByTagNameNS("*", "row");
        List<List<Cell>> rows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rowNodes.getLength(); rowIndex++) {
            Element rowElement = (Element) rowNodes.item(rowIndex);
            int declaredRow = positiveInteger(rowElement.getAttribute("r"), rows.size() + 1);
            if (declaredRow > MAX_ROWS + 10)
                throw new IllegalArgumentException("파일 데이터 행은 200개를 초과할 수 없습니다.");
            if (declaredRow <= rows.size())
                throw new IllegalArgumentException("XLSX 행 번호가 중복되거나 순서가 올바르지 않습니다.");
            while (rows.size() < declaredRow - 1) rows.add(List.of());
            List<Cell> row = new ArrayList<>();
            NodeList cells = rowElement.getElementsByTagNameNS("*", "c");
            for (int cellIndex = 0; cellIndex < cells.getLength(); cellIndex++) {
                Element cell = (Element) cells.item(cellIndex);
                int column = columnIndex(cell.getAttribute("r"));
                if (column >= MAX_COLUMNS) throw new IllegalArgumentException("파일 열은 50개를 초과할 수 없습니다.");
                while (row.size() <= column) row.add(Cell.blank());
                boolean formula = cell.getElementsByTagNameNS("*", "f").getLength() > 0;
                String type = cell.getAttribute("t");
                String raw = firstDescendantText(cell, "v");
                String value;
                if ("s".equals(type)) {
                    try {
                        int sharedIndex = Integer.parseInt(raw);
                        value = sharedIndex >= 0 && sharedIndex < sharedStrings.size() ? sharedStrings.get(sharedIndex) : "";
                    } catch (NumberFormatException e) { value = ""; }
                } else if ("inlineStr".equals(type)) value = descendantText(cell, "t");
                else if ("b".equals(type)) value = "1".equals(raw) ? "TRUE" : "FALSE";
                else value = raw;
                row.set(column, new Cell(value == null ? "" : value, formula));
            }
            rows.add(List.copyOf(row));
        }
        return rows;
    }

    private List<ParsedLine> mapRows(TabularData data) {
        int headerIndex = -1;
        Map<Field, Integer> columns = null;
        for (int index = 0; index < Math.min(10, data.rows().size()); index++) {
            Map<Field, Integer> candidate = mapHeader(data.rows().get(index), false);
            if (candidate.containsKey(Field.NAME)) { headerIndex = index; columns = mapHeader(data.rows().get(index), true); break; }
        }
        if (headerIndex < 0 || columns == null)
            throw new IllegalArgumentException("상품명 열을 찾을 수 없습니다. 지원 헤더: 상품명, 제품명, 품명, name, product_name");
        List<ParsedLine> result = new ArrayList<>();
        for (int rowIndex = headerIndex + 1; rowIndex < data.rows().size(); rowIndex++) {
            List<Cell> row = data.rows().get(rowIndex);
            if (row.stream().allMatch(cell -> cell.value().isBlank())) continue;
            if (result.size() >= MAX_ROWS) throw new IllegalArgumentException("파일 데이터 행은 200개를 초과할 수 없습니다.");
            List<String> errors = new ArrayList<>();
            for (Integer column : columns.values()) if (cell(row, column).formula()) {
                errors.add("수식 셀은 실행하지 않습니다. 표시된 값을 확인해 주세요."); break;
            }
            String name = text(row, columns.get(Field.NAME));
            if (name == null) { name = "(상품명 누락)"; errors.add("상품명이 누락되었습니다."); }
            BigDecimal moq = decimal(text(row, columns.get(Field.MOQ)), "MOQ", true, errors);
            String unit = text(row, columns.get(Field.UNIT));
            if (moq != null && unit == null) errors.add("MOQ가 있지만 수량 단위가 없습니다.");
            BigDecimal price = decimal(text(row, columns.get(Field.PRICE)), "단가", false, errors);
            String origin = text(row, columns.get(Field.ORIGIN));
            if (origin != null) {
                origin = origin.toUpperCase(Locale.ROOT);
                if (!origin.matches("[A-Z]{2}")) { errors.add("원산지는 ISO 2자리 코드여야 합니다."); origin = null; }
            }
            int sourceRow = rowIndex + 1;
            String location = "CSV".equals(data.sheetName()) ? "CSV row " + sourceRow
                    : data.sheetName() + "!row " + sourceRow;
            result.add(new ParsedLine(sourceRow, location, name, text(row, columns.get(Field.SKU)), unit,
                    moq, price, origin, text(row, columns.get(Field.NOTES)), List.copyOf(errors)));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("가져올 상품 행이 없습니다.");
        return List.copyOf(result);
    }

    private Map<Field, Integer> mapHeader(List<Cell> row, boolean rejectDuplicates) {
        Map<Field, Integer> result = new EnumMap<>(Field.class);
        for (int index = 0; index < row.size(); index++) {
            Field field = headerField(row.get(index).value());
            if (field == null) continue;
            if (rejectDuplicates && result.containsKey(field))
                throw new IllegalArgumentException("같은 의미의 헤더가 중복되었습니다: " + row.get(index).value());
            result.putIfAbsent(field, index);
        }
        return result;
    }

    private Field headerField(String value) {
        String header = normalizeHeader(value);
        if (Set.of("상품명", "제품명", "품명", "name", "itemname", "productname", "description").contains(header)) return Field.NAME;
        if (Set.of("공급처sku", "suppliersku", "sku", "상품코드", "제품코드", "품번", "itemcode").contains(header)) return Field.SKU;
        if (Set.of("moq", "최소수량", "최소주문수량", "minimumquantity", "minorderqty").contains(header)) return Field.MOQ;
        if (Set.of("단위", "수량단위", "quantityunit", "unit", "uom").contains(header)) return Field.UNIT;
        if (Set.of("단가", "공급가", "도매가", "unitprice", "price", "cost").contains(header)) return Field.PRICE;
        if (Set.of("원산지", "원산지국가", "countryoforigin", "origin", "country").contains(header)) return Field.ORIGIN;
        if (Set.of("비고", "메모", "notes", "note", "remark", "remarks").contains(header)) return Field.NOTES;
        return null;
    }

    private String normalizeHeader(String value) {
        if (value == null) return "";
        return value.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-./()]", "");
    }

    private BigDecimal decimal(String value, String label, boolean positive, List<String> errors) {
        if (value == null) return null;
        try {
            BigDecimal parsed = new BigDecimal(value.replace(",", "").trim());
            if ((positive && parsed.signum() <= 0) || (!positive && parsed.signum() < 0)) {
                errors.add(label + " 값의 범위가 올바르지 않습니다."); return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errors.add(label + "가 숫자가 아닙니다: " + value); return null;
        }
    }

    private Cell cell(List<Cell> row, Integer index) { return index == null || index >= row.size() ? Cell.blank() : row.get(index); }
    private String text(List<Cell> row, Integer index) {
        String value = cell(row, index).value();
        return value == null || value.isBlank() ? null : value.strip();
    }
    private int columnIndex(String reference) {
        int value = 0; int letters = 0;
        for (int index = 0; index < reference.length(); index++) {
            char character = reference.charAt(index);
            if (!Character.isLetter(character)) break;
            value = value * 26 + (Character.toUpperCase(character) - 'A' + 1); letters++;
        }
        return letters == 0 ? 0 : value - 1;
    }
    private int positiveInteger(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("XLSX 행 번호가 올바르지 않습니다.");
        }
    }
    private byte[] requiredEntry(Map<String, byte[]> entries, String name) {
        byte[] value = entries.get(name);
        if (value == null) throw new IllegalArgumentException("XLSX 필수 구성요소가 없습니다: " + name);
        return value;
    }
    private Document xml(byte[] content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(content)));
        } catch (Exception e) {
            throw new IllegalArgumentException("XLSX XML을 안전하게 읽을 수 없습니다.");
        }
    }
    private String descendantText(Element element, String localName) {
        StringBuilder result = new StringBuilder();
        NodeList nodes = element.getElementsByTagNameNS("*", localName);
        for (int index = 0; index < nodes.getLength(); index++) result.append(nodes.item(index).getTextContent());
        return result.toString();
    }
    private String firstDescendantText(Element element, String localName) {
        NodeList nodes = element.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }
}
