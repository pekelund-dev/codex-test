package dev.pekelund.responsiveauth.function.legacy;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class BaseReceiptParser implements ReceiptFormatParser {

    private static final Pattern DECIMAL_PATTERN = Pattern.compile("[-+]?\\d+[.,]?\\d*");
    private final DecimalFormatSymbols decimalSymbols = new DecimalFormatSymbols(Locale.US);

    protected Optional<BigDecimal> parseDecimal(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        String normalized = trimmed.replace(decimalSymbols.getDecimalSeparator(), '.').replace(',', '.');
        try {
            return Optional.of(new BigDecimal(normalized));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    protected Optional<BigDecimal> extractFirstDecimal(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = DECIMAL_PATTERN.matcher(text.replace(" ", ""));
        if (matcher.find()) {
            return parseDecimal(matcher.group());
        }
        return Optional.empty();
    }

    protected Optional<LegacyReceiptDiscount> parseDiscountLine(String line, Pattern pattern) {
        if (line == null) {
            return Optional.empty();
        }
        Matcher matcher = pattern.matcher(line.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String description = matcher.group("name");
        String amountGroup = matcher.group("discountAmount");
        if (amountGroup == null && matcher.groupCount() >= 2) {
            amountGroup = matcher.group(2);
        }
        return parseDecimal(amountGroup).map(amount -> new LegacyReceiptDiscount(description, amount));
    }
}
