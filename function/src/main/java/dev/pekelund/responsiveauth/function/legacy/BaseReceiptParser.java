package dev.pekelund.responsiveauth.function.legacy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class BaseReceiptParser implements ReceiptFormatParser {

    private static final Pattern DECIMAL_PATTERN = Pattern.compile("[-+]?\\d+(?:[\\s.,]\\d+)*");

    protected Optional<BigDecimal> parseDecimal(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.replace('\u00A0', ' ').trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        boolean negative = trimmed.startsWith("-");
        boolean positive = !negative && trimmed.startsWith("+");
        String digitsOnly = trimmed.replaceAll("[^0-9,.-]", "");
        if (negative || positive) {
            digitsOnly = digitsOnly.substring(1);
        }
        String normalized = digitsOnly.replace(" ", "");
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        int lastComma = normalized.lastIndexOf(',');
        if (lastComma >= 0) {
            String integer = normalized.substring(0, lastComma).replace(",", "").replace(".", "");
            String decimal = normalized.substring(lastComma + 1).replace(",", "").replace(".", "");
            normalized = integer + "." + decimal;
        } else {
            int lastDot = normalized.lastIndexOf('.');
            if (lastDot >= 0) {
                String integer = normalized.substring(0, lastDot).replace(".", "");
                String decimal = normalized.substring(lastDot + 1).replace(".", "");
                normalized = integer + "." + decimal;
            } else {
                normalized = normalized.replace(",", "").replace(".", "");
            }
        }
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        if (negative) {
            normalized = "-" + normalized;
        } else if (positive) {
            normalized = "+" + normalized;
        }
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
        String sanitized = line.replace('\u00A0', ' ').trim();
        Matcher matcher = pattern.matcher(sanitized);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String description = matcher.group("name");
        String normalizedDescription = description == null ? null : description.replace('\u00A0', ' ').trim();
        String amountGroup = matcher.group("discountAmount");
        if (amountGroup == null && matcher.groupCount() >= 2) {
            try {
                amountGroup = matcher.group(2);
            } catch (IndexOutOfBoundsException | IllegalStateException ex) {
                amountGroup = null;
            }
        }
        String normalizedAmount = amountGroup;
        if (normalizedAmount != null) {
            normalizedAmount = normalizedAmount.replace('\u00A0', ' ').trim();
            if (!normalizedAmount.startsWith("-")) {
                normalizedAmount = "-" + normalizedAmount;
            }
        }
        return parseDecimal(normalizedAmount).map(amount -> new LegacyReceiptDiscount(normalizedDescription, amount));
    }
}
