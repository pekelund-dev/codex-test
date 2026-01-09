package dev.pekelund.pklnd.receiptparser.legacy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CodexParser implements ReceiptFormatParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodexParser.class);

    private static final Pattern ITEM_PATTERN = Pattern.compile(
        "^(?<name>.+?)\\s+(?<ean>\\d{4,13})\\s+(?<unit>-?\\d+,\\d{2})\\s+(?<quantity>\\d+(?:,\\d+)?\\s*\\p{L}+)\\s+(?<total>-?\\d+,\\d{2})$"
    );
    private static final Pattern ITEM_NO_UNIT_PRICE_PATTERN = Pattern.compile(
        "^(?<name>.+?)\\s+(?<ean>\\d{4,13})\\s+(?<quantity>\\d+(?:,\\d+)?\\s*\\p{L}+)\\s+(?<total>-?\\d+,\\d{2})$"
    );
    private static final Pattern DISCOUNT_PATTERN = Pattern.compile(
        "^(?<description>.+?)\\s+(?<amount>-\\d+,\\d{2})$"
    );
    private static final Pattern VAT_HEADER_PATTERN = Pattern.compile(
        "^Moms\\s*%\\s*Moms\\s*Netto\\s*Brutto$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern VAT_LINE_PATTERN = Pattern.compile(
        "^(?<rate>\\d+(?:,\\d+)?)\\s+(?<tax>-?\\d+,\\d{2})\\s+(?<net>-?\\d+,\\d{2})\\s+(?<gross>-?\\d+,\\d{2})$"
    );
    private static final Pattern PANT_PATTERN = Pattern.compile(
        "^Pant\\s+(?<unit>\\d+,\\d{2})\\s+(?<quantity>\\d+)\\s+(?<total>-?\\d+,\\d{2})$"
    );
     private static final Pattern PANT_HEADER_PATTERN = Pattern.compile(
        "^Pant$",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TOTAL_PATTERN = Pattern.compile(
        "^Betalat\\s+(?<amount>[-\\d,]+)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DATE_PATTERN = Pattern.compile("^(?<date>\\d{4}-\\d{2}-\\d{2})$");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public boolean supportsFormat(ReceiptFormat format) {
        return format == ReceiptFormat.NEW_FORMAT || format == ReceiptFormat.UNKNOWN;
    }

    @Override
    public LegacyParsedReceipt parse(String[] pdfData, ReceiptFormat format) {
        String[] lines = pdfData == null ? new String[0] : pdfData;

        String storeName = extractStoreName(lines);
        LocalDate receiptDate = extractDate(lines);
        BigDecimal totalAmount = null;

        List<LegacyReceiptItem> items = new ArrayList<>();
        List<LegacyReceiptVat> vats = new ArrayList<>();
        List<LegacyReceiptDiscount> generalDiscounts = new ArrayList<>();
        List<LegacyReceiptError> errors = new ArrayList<>();

        boolean inItems = false;
        boolean inVat = false;
        LegacyReceiptItem currentItem = null;
        ReconciliationStatus reconciliationStatus = ReconciliationStatus.NONE;

        for (int index = 0; index < lines.length; index++) {
            String originalLine = lines[index];
            String line = sanitize(originalLine);
            if (line.isEmpty()) {
                continue;
            }

            if (VAT_HEADER_PATTERN.matcher(line).matches()) {
                LOGGER.debug("Detected VAT section at line {}", index);
                inItems = false;
                inVat = true;
                continue;
            }

            if (line.startsWith("Beskrivning")) {
                LOGGER.debug("Detected item header at line {}", index);
                inItems = true;
                inVat = false;
                continue;
            }

            if (line.contains("Delavstämning korrekt")) {
                reconciliationStatus = ReconciliationStatus.PARTIAL;
                inItems = false;
                inVat = false;
                continue;
            }

            if (line.contains("Avstämning korrekt")) {
                reconciliationStatus = ReconciliationStatus.COMPLETE;
                inItems = false;
                inVat = false;
                continue;
            }

            if (line.startsWith("Betalningsinformation")
                || line.startsWith("Erhållen rabatt")
                || line.startsWith("Avrundning")
                || line.startsWith("Kort")) {
                inItems = false;
                inVat = false;
            }

            Matcher totalMatcher = TOTAL_PATTERN.matcher(line);
            if (totalMatcher.matches()) {
                BigDecimal amount = parseAmount(totalMatcher.group("amount"));
                if (amount != null) {
                    totalAmount = amount;
                } else {
                    errors.add(new LegacyReceiptError(index, originalLine, "Unable to parse total amount"));
                }
                inItems = false;
                currentItem = null;
                continue;
            }

            if (inItems) {
                Matcher itemMatcher = ITEM_PATTERN.matcher(line);
                if (itemMatcher.matches()) {
                    LegacyReceiptItem item = createItem(itemMatcher);
                    items.add(item);
                    currentItem = item;
                    continue;
                }

                Matcher itemNoUnitPriceMatcher = ITEM_NO_UNIT_PRICE_PATTERN.matcher(line);
                if (itemNoUnitPriceMatcher.matches()) {
                    LegacyReceiptItem item = createItemWithoutUnitPrice(itemNoUnitPriceMatcher);
                    items.add(item);
                    currentItem = item;
                    continue;
                }

                Matcher pantMatcher = PANT_PATTERN.matcher(line);
                if (pantMatcher.matches()) {
                    LegacyReceiptItem item = createPantItem(pantMatcher);
                    items.add(item);
                    currentItem = item;
                    continue;
                }

                Matcher discountMatcher = DISCOUNT_PATTERN.matcher(line);
                if (discountMatcher.matches()) {
                    String description = normalizeWhitespace(discountMatcher.group("description"));
                    BigDecimal discountAmount = parseAmount(discountMatcher.group("amount"));
                    if (discountAmount == null) {
                        errors.add(new LegacyReceiptError(index, originalLine, "Unable to parse discount amount"));
                        continue;
                    }
                    if (currentItem != null && currentItem.getTotalPrice() != null
                        && discountAmount.abs().compareTo(currentItem.getTotalPrice().abs()) <= 0) {
                        currentItem.addDiscount(new LegacyReceiptDiscount(description, discountAmount));
                    } else {
                        generalDiscounts.add(new LegacyReceiptDiscount(description, discountAmount));
                        currentItem = null;
                    }
                    continue;
                }
                
                Matcher pantHeaderMatcher = PANT_HEADER_PATTERN.matcher(line);
                if (pantHeaderMatcher.matches()) {
                    continue;
                }

                if (line.equalsIgnoreCase("Retur")) {
                    continue;
                }

                errors.add(new LegacyReceiptError(index, originalLine, "Unrecognized item line"));
                continue;
            }

            if (inVat) {
                Matcher vatMatcher = VAT_LINE_PATTERN.matcher(line);
                if (vatMatcher.matches()) {
                    LegacyReceiptVat vat = createVat(vatMatcher);
                    if (vat != null) {
                        vats.add(vat);
                    } else {
                        errors.add(new LegacyReceiptError(index, originalLine, "Unable to parse VAT line"));
                    }
                    continue;
                }

                if (!line.startsWith("Moms")) {
                    inVat = false;
                }
            }
        }

        return new LegacyParsedReceipt(format, storeName, receiptDate, totalAmount, items, vats, generalDiscounts, errors, reconciliationStatus);
    }

    private String extractStoreName(String[] lines) {
        boolean seenHeader = false;
        for (String raw : lines) {
            String line = sanitize(raw);
            if (line.isEmpty()) {
                continue;
            }
            if (!seenHeader && line.equalsIgnoreCase("Kvitto")) {
                seenHeader = true;
                continue;
            }
            if (seenHeader && !line.isEmpty()) {
                return line;
            }
        }
        for (String raw : lines) {
            String line = sanitize(raw);
            if (!line.isEmpty()) {
                return line;
            }
        }
        return null;
    }

    private LocalDate extractDate(String[] lines) {
        for (String raw : lines) {
            String line = sanitize(raw);
            Matcher matcher = DATE_PATTERN.matcher(line);
            if (matcher.matches()) {
                try {
                    return LocalDate.parse(matcher.group("date"), DATE_FORMATTER);
                } catch (Exception ex) {
                    LOGGER.debug("Unable to parse date '{}'", line, ex);
                }
            }
        }
        return null;
    }

    private LegacyReceiptItem createItem(Matcher matcher) {
        String name = normalizeWhitespace(matcher.group("name"));
        String ean = matcher.group("ean");
        BigDecimal unitPrice = parseAmount(matcher.group("unit"));
        String quantity = normalizeQuantity(matcher.group("quantity"));
        BigDecimal totalPrice = parseAmount(matcher.group("total"));
        return new LegacyReceiptItem(name, ean, unitPrice, quantity, totalPrice);
    }

    private LegacyReceiptItem createItemWithoutUnitPrice(Matcher matcher) {
        String name = normalizeWhitespace(matcher.group("name"));
        String ean = matcher.group("ean");
        String quantity = normalizeQuantity(matcher.group("quantity"));
        BigDecimal totalPrice = parseAmount(matcher.group("total"));
        return new LegacyReceiptItem(name, ean, null, quantity, totalPrice);
    }

    private LegacyReceiptItem createPantItem(Matcher matcher) {
        String name = "Pant";
        BigDecimal unitPrice = parseAmount(matcher.group("unit"));
        String quantity = normalizeQuantity(matcher.group("quantity"));
        BigDecimal totalPrice = parseAmount(matcher.group("total"));
        return new LegacyReceiptItem(name, null, unitPrice, quantity, totalPrice);
    }

    private LegacyReceiptVat createVat(Matcher matcher) {
        BigDecimal rate = parseAmount(matcher.group("rate"));
        BigDecimal tax = parseAmount(matcher.group("tax"));
        BigDecimal net = parseAmount(matcher.group("net"));
        BigDecimal gross = parseAmount(matcher.group("gross"));
        if (rate == null || tax == null || net == null || gross == null) {
            return null;
        }
        return new LegacyReceiptVat(rate, tax, net, gross);
    }

    private String sanitize(String line) {
        if (line == null) {
            return "";
        }
        return line.replace('\u00A0', ' ').trim();
    }

    private String normalizeWhitespace(String value) {
        return value == null ? null : value.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
    }

    private String normalizeQuantity(String value) {
        return value == null ? null : value.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
    }

    private BigDecimal parseAmount(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.replace('\u00A0', ' ').trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        boolean negative = trimmed.startsWith("-");
        boolean positive = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[^0-9,]", "");
        if (digits.isEmpty()) {
            return null;
        }
        int lastComma = digits.lastIndexOf(',');
        String integerPart;
        String decimalPart = "";
        if (lastComma >= 0) {
            integerPart = digits.substring(0, lastComma).replace(",", "");
            decimalPart = digits.substring(lastComma + 1);
        } else {
            integerPart = digits.replace(",", "");
        }
        if (integerPart.isEmpty()) {
            integerPart = "0";
        }
        StringBuilder number = new StringBuilder(integerPart);
        if (!decimalPart.isEmpty()) {
            number.append('.').append(decimalPart);
        }
        BigDecimal amount = new BigDecimal(number.toString());
        if (negative) {
            amount = amount.negate();
        } else if (positive) {
            return amount;
        }
        return amount;
    }
}
