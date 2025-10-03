package dev.pekelund.responsiveauth.function.legacy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class StandardFormatParser extends BaseReceiptParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(StandardFormatParser.class);

    private static final Pattern ITEM_PATTERN = Pattern.compile(
        "(?<name>.+?)\\s(?<eanCode>\\d{8,13})\\s(?<unitPrice>\\d+[.,]\\d{2})\\s(?<quantity>\\d+(?:[.,]\\d+)?\\s(?:st|kg))\\s(?<totalPrice>\\d+[.,]\\d{2})");
    private static final Pattern DISCOUNT_PATTERN = Pattern.compile("(?<name>.+?)\\s-\\s(?<discountAmount>\\d+[.,]\\d{2})");
    private static final Pattern DATE_LINE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} AID:.+");
    private static final Pattern VAT_LINE_PATTERN = Pattern.compile(
        "(?:Moms\\s*)?(?<rate>\\d+(?:[.,]\\d+)?)%?\\s+(?<tax>\\d+[.,]\\d{2})\\s+(?<net>\\d+[.,]\\d{2})\\s+(?<gross>\\d+[.,]\\d{2})",
        Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supportsFormat(ReceiptFormat format) {
        return format == ReceiptFormat.STANDARD;
    }

    @Override
    public LegacyParsedReceipt parse(String[] pdfData, ReceiptFormat format) {
        List<LegacyReceiptItem> items = new ArrayList<>();
        List<LegacyReceiptError> errors = new ArrayList<>();

        String store = pdfData != null && pdfData.length > 1 ? pdfData[1].trim() : null;
        BigDecimal totalAmount = extractTotalAmount(pdfData).orElse(null);
        LocalDate receiptDate = extractReceiptDate(pdfData).orElse(null);

        boolean parsingItems = false;
        for (int index = 0; pdfData != null && index < pdfData.length; index++) {
            String line = pdfData[index];
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.contains("Beskrivning Art. nr. Pris Mängd Summa(SEK)")) {
                parsingItems = true;
                continue;
            }

            if (trimmed.contains("Moms % Moms Netto Brutto")) {
                parsingItems = false;
                continue;
            }

            if (trimmed.startsWith("Total") || trimmed.contains("Betalat")) {
                parsingItems = false;
                continue;
            }

            if (!parsingItems) {
                continue;
            }

            Optional<LegacyReceiptItem> parsedItem = parseItem(trimmed);
            if (parsedItem.isPresent()) {
                LegacyReceiptItem item = parsedItem.get();
                if (index + 1 < pdfData.length) {
                    Optional<LegacyReceiptDiscount> discount = parseDiscountLine(pdfData[index + 1], DISCOUNT_PATTERN);
                    if (discount.isPresent()) {
                        item.addDiscount(discount.get());
                        index++;
                    }
                }
                items.add(item);
            } else {
                Optional<LegacyReceiptDiscount> trailingDiscount = parseDiscountLine(trimmed, DISCOUNT_PATTERN);
                if (trailingDiscount.isPresent()) {
                    if (!items.isEmpty()) {
                        items.get(items.size() - 1).addDiscount(trailingDiscount.get());
                    } else {
                        errors.add(new LegacyReceiptError(index, trimmed, "Discount encountered before any item"));
                    }
                } else {
                    errors.add(new LegacyReceiptError(index, trimmed, "Unrecognized line in items section"));
                }
            }
        }

        List<LegacyReceiptVat> vats = extractVatLines(pdfData);

        LOGGER.info("Parsed STANDARD receipt - store: {}, date: {}, total: {}, items: {}, vat lines: {}", store,
            receiptDate, totalAmount, items.size(), vats.size());
        return new LegacyParsedReceipt(format, store, receiptDate, totalAmount, items, vats, List.of(), errors);
    }

    private Optional<BigDecimal> extractTotalAmount(String[] pdfData) {
        if (pdfData == null) {
            return Optional.empty();
        }
        for (String line : pdfData) {
            if (line != null && line.startsWith("Total")) {
                return extractFirstDecimal(line.substring("Total".length()));
            }
        }
        return Optional.empty();
    }

    private Optional<LocalDate> extractReceiptDate(String[] pdfData) {
        if (pdfData == null) {
            return Optional.empty();
        }
        for (String line : pdfData) {
            if (line == null) {
                continue;
            }
            Matcher matcher = DATE_LINE_PATTERN.matcher(line);
            if (matcher.find()) {
                return Optional.of(LocalDate.parse(line.substring(0, 10)));
            }
        }
        return Optional.empty();
    }

    private Optional<LegacyReceiptItem> parseItem(String line) {
        Matcher matcher = ITEM_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String name = matcher.group("name");
        String ean = matcher.group("eanCode");
        BigDecimal unitPrice = parseDecimal(matcher.group("unitPrice")).orElse(null);
        String quantity = matcher.group("quantity");
        BigDecimal totalPrice = parseDecimal(matcher.group("totalPrice")).orElse(null);
        return Optional.of(new LegacyReceiptItem(name, ean, unitPrice, quantity, totalPrice));
    }

    private List<LegacyReceiptVat> extractVatLines(String[] pdfData) {
        List<LegacyReceiptVat> vats = new ArrayList<>();
        if (pdfData == null) {
            return vats;
        }
        boolean inVatSection = false;
        for (String line : pdfData) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (inVatSection) {
                    break;
                }
                continue;
            }
            if (trimmed.contains("Moms % Moms Netto Brutto")) {
                inVatSection = true;
                continue;
            }
            if (!inVatSection) {
                continue;
            }
            if (isVatSectionTerminator(trimmed)) {
                break;
            }
            Matcher matcher = VAT_LINE_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                Optional<BigDecimal> rate = parseDecimal(matcher.group("rate"));
                Optional<BigDecimal> tax = parseDecimal(matcher.group("tax"));
                Optional<BigDecimal> net = parseDecimal(matcher.group("net"));
                Optional<BigDecimal> gross = parseDecimal(matcher.group("gross"));
                vats.add(new LegacyReceiptVat(rate.orElse(null), tax.orElse(null), net.orElse(null),
                    gross.orElse(null)));
            } else if (trimmed.startsWith("Erhållen") || trimmed.startsWith("Avrundning")
                || trimmed.startsWith("Kort") || trimmed.startsWith("Kontant")) {
                break;
            }
        }
        return vats;
    }

    private boolean isVatSectionTerminator(String line) {
        return line.startsWith("Total") || line.startsWith("Summa") || line.contains("Betalat");
    }
}
