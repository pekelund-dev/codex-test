package dev.pekelund.pklnd.receiptparser.legacy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pekelund.pklnd.receiptparser.ReceiptDataExtractor;
import dev.pekelund.pklnd.receiptparser.ReceiptExtractionResult;
import dev.pekelund.pklnd.receiptparser.ReceiptParsingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LegacyPdfReceiptExtractor implements ReceiptDataExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyPdfReceiptExtractor.class);
    private static final String SOURCE = "legacy-pdf-parser";
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("^(?<amount>-?\\d+(?:[.,]\\d+)?)\\s*(?<unit>\\p{L}+)$");

    private final PdfParser pdfParser;
    private final ObjectMapper objectMapper;

    public LegacyPdfReceiptExtractor(PdfParser pdfParser, ObjectMapper objectMapper) {
        this.pdfParser = pdfParser;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReceiptExtractionResult extract(byte[] pdfBytes, String fileName) {
        LOGGER.info("LegacyPdfReceiptExtractor invoked for file {} with payload size {}", fileName,
            pdfBytes != null ? pdfBytes.length : null);
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new ReceiptParsingException("Cannot parse an empty PDF document");
        }

        String[] pdfData = readPdfLines(pdfBytes);
        if (pdfData.length == 0) {
            throw new ReceiptParsingException("PDF document did not contain any readable text");
        }

        LegacyParsedReceipt parsedReceipt = pdfParser.parse(pdfData);
        Map<String, Object> structuredData = mapStructuredData(parsedReceipt, pdfData, fileName);
        String rawResponse = toJson(structuredData);
        return new ReceiptExtractionResult(structuredData, rawResponse);
    }

    private String[] readPdfLines(byte[] pdfBytes) {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return text != null ? text.split("\\r?\\n") : new String[0];
        } catch (IOException ex) {
            throw new ReceiptParsingException("Failed to read PDF document", ex);
        }
    }

    private Map<String, Object> mapStructuredData(LegacyParsedReceipt parsedReceipt, String[] pdfData, String fileName) {
        Map<String, Object> general = new LinkedHashMap<>();
        general.put("storeName", parsedReceipt.storeName());
        general.put("receiptDate", Optional.ofNullable(parsedReceipt.receiptDate()).map(Object::toString).orElse(null));
        general.put("totalAmount", parsedReceipt.totalAmount());
        general.put("format", Optional.ofNullable(parsedReceipt.format()).map(Enum::name).orElse(null));
        general.put("reconciliationStatus", Optional.ofNullable(parsedReceipt.reconciliationStatus()).map(Enum::name).orElse("NONE"));
        general.put("fileName", fileName);
        general.put("source", SOURCE);

        List<Map<String, Object>> items = parsedReceipt.items().stream()
            .map(this::mapItem)
            .collect(Collectors.toCollection(ArrayList::new));

        List<Map<String, Object>> vats = parsedReceipt.vats().stream()
            .map(this::mapVat)
            .collect(Collectors.toCollection(ArrayList::new));

        List<Map<String, Object>> generalDiscounts = parsedReceipt.generalDiscounts().stream()
            .map(this::mapDiscount)
            .collect(Collectors.toCollection(ArrayList::new));

        List<Map<String, Object>> errors = parsedReceipt.errors().stream()
            .map(error -> Map.<String, Object>of(
                "lineNumber", error.lineNumber(),
                "content", error.content(),
                "message", error.message()))
            .collect(Collectors.toCollection(ArrayList::new));

        Map<String, Object> structuredData = new LinkedHashMap<>();
        structuredData.put("general", general);
        structuredData.put("items", items);
        structuredData.put("vats", vats);
        structuredData.put("generalDiscounts", generalDiscounts);
        structuredData.put("errors", errors);
        structuredData.put("rawText", String.join("\n", pdfData));
        structuredData.put("source", SOURCE);
        return structuredData;
    }

    private Map<String, Object> mapItem(LegacyReceiptItem item) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("name", item.getName());
        mapped.put("eanCode", item.getEanCode());
        mapped.put("unitPrice", item.getUnitPrice());
        mapped.put("quantity", resolveQuantity(item));
        mapped.put("totalPrice", item.getTotalPrice());
        List<Map<String, Object>> discounts = item.getDiscounts().stream()
            .map(this::mapDiscount)
            .collect(Collectors.toCollection(ArrayList::new));
        mapped.put("discounts", discounts);
        return mapped;
    }

    private String resolveQuantity(LegacyReceiptItem item) {
        String quantity = item.getQuantity();
        if (quantity == null || item.getUnitPrice() == null || item.getTotalPrice() == null
            || item.getUnitPrice().compareTo(BigDecimal.ZERO) == 0) {
            return quantity;
        }

        Matcher matcher = QUANTITY_PATTERN.matcher(quantity.trim());
        if (!matcher.matches()) {
            return quantity;
        }

        BigDecimal parsedAmount = parseAmount(matcher.group("amount"));
        if (parsedAmount == null) {
            return quantity;
        }

        String unit = matcher.group("unit");
        BigDecimal calculatedAmount = item.getTotalPrice().divide(item.getUnitPrice(), 4, RoundingMode.HALF_UP);
        BigDecimal diff = parsedAmount.subtract(calculatedAmount).abs();
        if (diff.compareTo(new BigDecimal("0.001")) <= 0) {
            return quantity;
        }

        return formatAmount(calculatedAmount) + " " + unit;
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatAmount(BigDecimal amount) {
        BigDecimal scaled = amount.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
        if (scaled.scale() < 0) {
            scaled = scaled.setScale(0, RoundingMode.UNNECESSARY);
        }
        return scaled.toPlainString().replace('.', ',');
    }

    private Map<String, Object> mapVat(LegacyReceiptVat vat) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("rate", vat.rate());
        mapped.put("taxAmount", vat.taxAmount());
        mapped.put("netAmount", vat.netAmount());
        mapped.put("grossAmount", vat.grossAmount());
        return mapped;
    }

    private Map<String, Object> mapDiscount(LegacyReceiptDiscount discount) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("description", discount.description());
        mapped.put("amount", discount.amount());
        return mapped;
    }

    private String toJson(Map<String, Object> structuredData) {
        try {
            return objectMapper.writeValueAsString(structuredData);
        } catch (JsonProcessingException ex) {
            LOGGER.warn("Failed to serialize legacy structured data to JSON", ex);
            return structuredData.toString();
        }
    }
}
