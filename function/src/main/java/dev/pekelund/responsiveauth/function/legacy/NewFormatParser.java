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
class NewFormatParser extends BaseReceiptParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewFormatParser.class);

    private static final Pattern ITEM_PATTERN = Pattern.compile(
        "(?<name>.+?) (?<eanCode>\\d{8,13}) (?<unitPrice>\\d+[.,]\\d{2}) (?<quantity>\\d+(?:[.,]\\d+)?\\s(?:st|kg)) (?<totalPrice>\\d+[.,]\\d{2})");
    private static final Pattern DISCOUNT_PATTERN = Pattern.compile("(?<name>.+?)\\s-\\s?(?<discountAmount>[\\d]+[.,][\\d]{2})");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern TOTAL_PATTERN = Pattern.compile("Total(?:t att betala)?\\s*([\\d,.]+)");
    private static final Pattern VAT_LINE_PATTERN = Pattern.compile(
        "(?:Moms\\s*)?(?<rate>\\d+(?:[.,]\\d+)?)%?\\s+(?<tax>\\d+[.,]\\d{2})\\s+(?<net>\\d+[.,]\\d{2})\\s+(?<gross>\\d+[.,]\\d{2})",
        Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supportsFormat(ReceiptFormat format) {
        return format == ReceiptFormat.NEW_FORMAT;
    }

    @Override
    public LegacyParsedReceipt parse(String[] pdfData, ReceiptFormat format) {
        List<LegacyReceiptItem> items = new ArrayList<>();
        List<LegacyReceiptError> errors = new ArrayList<>();

        String store = pdfData != null && pdfData.length > 1 ? pdfData[1].trim() : null;
        LocalDate receiptDate = extractDate(pdfData).orElse(null);
        BigDecimal totalAmount = extractTotal(pdfData).orElse(null);

        int itemsStartIndex = locateItemsStart(pdfData);
        int itemsEndIndex = locateItemsEnd(pdfData, itemsStartIndex);

        if (itemsStartIndex >= 0 && itemsEndIndex >= itemsStartIndex) {
            for (int index = itemsStartIndex; index <= itemsEndIndex && index < pdfData.length; index++) {
                String line = pdfData[index];
                if (line == null || line.isBlank()) {
                    continue;
                }
                Optional<LegacyReceiptItem> item = parseItem(line.trim());
                if (item.isPresent()) {
                    LegacyReceiptItem receiptItem = item.get();
                    if (index + 1 <= itemsEndIndex) {
                        Optional<LegacyReceiptDiscount> discount = parseDiscountLine(pdfData[index + 1], DISCOUNT_PATTERN);
                        if (discount.isPresent()) {
                            receiptItem.addDiscount(discount.get());
                            index++;
                        }
                    }
                    items.add(receiptItem);
                } else {
                    Optional<LegacyReceiptDiscount> discount = parseDiscountLine(line, DISCOUNT_PATTERN);
                    if (discount.isPresent()) {
                        if (!items.isEmpty()) {
                            items.get(items.size() - 1).addDiscount(discount.get());
                        } else {
                            errors.add(new LegacyReceiptError(index, line, "Discount encountered before any item"));
                        }
                    } else {
                        errors.add(new LegacyReceiptError(index, line, "Unrecognized line in items section"));
                    }
                }
            }
        }

        List<LegacyReceiptVat> vats = extractVatLines(pdfData, itemsEndIndex + 1);

        LOGGER.info("Parsed NEW_FORMAT receipt - store: {}, date: {}, total: {}, items: {}, vat lines: {}", store,
            receiptDate, totalAmount, items.size(), vats.size());
        return new LegacyParsedReceipt(format, store, receiptDate, totalAmount, items, vats, errors);
    }

    private Optional<LocalDate> extractDate(String[] pdfData) {
        if (pdfData == null) {
            return Optional.empty();
        }
        for (String line : pdfData) {
            if (line == null) {
                continue;
            }
            Matcher matcher = DATE_PATTERN.matcher(line);
            if (matcher.find()) {
                return Optional.of(LocalDate.parse(matcher.group(1)));
            }
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> extractTotal(String[] pdfData) {
        if (pdfData == null) {
            return Optional.empty();
        }
        for (String line : pdfData) {
            if (line == null) {
                continue;
            }
            Matcher matcher = TOTAL_PATTERN.matcher(line);
            if (matcher.find()) {
                return parseDecimal(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    private int locateItemsStart(String[] pdfData) {
        if (pdfData == null) {
            return -1;
        }
        for (int i = 0; i < pdfData.length; i++) {
            String line = pdfData[i];
            if (line != null && line.contains("Beskrivning Artikelnummer Pris Mängd Summa(SEK)")) {
                return i + 1;
            }
        }
        return -1;
    }

    private int locateItemsEnd(String[] pdfData, int startIndex) {
        if (pdfData == null || startIndex < 0) {
            return -1;
        }
        for (int i = startIndex; i < pdfData.length; i++) {
            String line = pdfData[i];
            if (line == null) {
                continue;
            }
            if (line.contains("Moms") || line.startsWith("Total") || line.contains("Betalat")) {
                return i - 1;
            }
        }
        return pdfData.length - 1;
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
        BigDecimal total = parseDecimal(matcher.group("totalPrice")).orElse(null);
        return Optional.of(new LegacyReceiptItem(name, ean, unitPrice, quantity, total));
    }

    private List<LegacyReceiptVat> extractVatLines(String[] pdfData, int startIndex) {
        List<LegacyReceiptVat> vats = new ArrayList<>();
        if (pdfData == null || startIndex < 0) {
            return vats;
        }
        boolean inVatSection = false;
        for (int index = Math.max(startIndex, 0); index < pdfData.length; index++) {
            String line = pdfData[index];
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
            if (trimmed.contains("Moms % Moms Netto Brutto") || trimmed.equalsIgnoreCase("Moms")) {
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
        return line.startsWith("Total") || line.contains("Betalat") || line.startsWith("Summa");
    }
}
