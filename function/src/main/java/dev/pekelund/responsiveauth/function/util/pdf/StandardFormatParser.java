package dev.pekelund.pklnd.utils.pdf;

import dev.pekelund.pklnd.models.receipts.Receipt;
import dev.pekelund.pklnd.models.receipts.Item;
import dev.pekelund.pklnd.models.errors.Error;
import dev.pekelund.pklnd.models.receipts.Discount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StandardFormatParser extends BaseReceiptParser {
    private static final Logger logger = LoggerFactory.getLogger(StandardFormatParser.class);

    private static final String regexStandard = "(?<name>.+?)\\s(?<eanCode>\\d{8,13})\\s(?<comparisonPrice>\\d+\\.\\d{2})\\s(?<quantity>\\d+(?:\\.\\d+)?\\s(?:st|kg))\\s(?<totalPrice>\\d+\\.\\d{2})";
    private static final String regexDiscount = "(?<name>.+?)\\s-\\s(?<discountAmount>\\d+\\.\\d{2})";

    @Override
    public boolean supportsFormat(ReceiptFormat format) {
        return format == ReceiptFormat.STANDARD;
    }

    @Override
    public Receipt parse(String[] pdfData, String userId, URL url) {
        Receipt receipt = new Receipt();
        receipt.setUserId(userId);
        receipt.setItems(new ArrayList<>());
        receipt.setUrl(url);

        Pattern pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} AID:.+");
        String store = pdfData[1];
        int i = 2;
        while (!pdfData[i].startsWith("Total")) {
            i++;
        }

        String amount = pdfData[i].substring(7, pdfData[i].length() - 1);

        Matcher matcher;
        do {
            i++;
            matcher = pattern.matcher(pdfData[i]);
        } while (!matcher.find() && i < pdfData.length - 1);

        String date = pdfData[i].substring(0, 10);
        receipt.setStore(store);
        receipt.setAmount(new BigDecimal(amount));
        receipt.setDate(LocalDate.parse(date));

        boolean parsingItems = false;
        for (i = 0; i < pdfData.length; i++) {
            String line = pdfData[i];
            if (line.contains("Beskrivning Art. nr. Pris Mängd Summa(SEK)")) {
                parsingItems = true;
                continue;
            }
            if (line.contains("Moms % Moms Netto Brutto")) {
                parsingItems = false;
                continue;
            }
            if (parsingItems) {
                Optional<Item> item = parseItem(line);
                if (item.isPresent()) {
                    Item parsedItem = item.get();
                    // Check for discount line
                    if (line.startsWith("*") && i + 1 < pdfData.length) {
                        String nextLine = pdfData[i + 1];
                        if (isDiscountLine(nextLine)) {
                            Discount discount = parseDiscount(nextLine);
                            if (discount != null) {
                                logger.debug("Adding discount to item: {}", discount);
                                discount.setItem(parsedItem);
                                parsedItem.addDiscount(discount);
                            }
                            i++; // Skip the discount line
                        }
                    }
                    parsedItem.setReceipt(receipt);
                    receipt.getItems().add(parsedItem);

                } else {
                    Error error = new Error(null, i, line, receipt);
                    receipt.addError(error);
                    logger.warn("Failed to parse item: {}", error);
                }
            }
        }

        return receipt;
    }

    private boolean isDiscountLine(String line) {
        Pattern discountPattern = Pattern.compile(regexDiscount);
        Matcher matcher = discountPattern.matcher(line);
        return matcher.matches();
    }

    private Discount parseDiscount(String line) {
        Pattern discountPattern = Pattern.compile(regexDiscount);
        Matcher matcher = discountPattern.matcher(line);
        if (matcher.matches()) {
            String name = matcher.group("name");
            BigDecimal discountAmount = new BigDecimal(matcher.group("discountAmount"));
            logger.debug("Parsed discount: name={}, discountAmount={}", name, discountAmount);
            logger.debug("discount: {}", new Discount(null, name, discountAmount, null));
            return new Discount(null, name, discountAmount, null);
        }
        return null;
    }

    private Optional<Item> parseItem(String line) {
        Pattern itemPattern = Pattern.compile(regexStandard);
        Matcher matcher = itemPattern.matcher(line);
        if (matcher.matches()) {
            String name = matcher.group("name");
            String eanCode = matcher.group("eanCode");
            BigDecimal comparisonPrice = new BigDecimal(matcher.group("comparisonPrice"));
            String quantity = matcher.group("quantity");
            BigDecimal totalPrice = new BigDecimal(matcher.group("totalPrice"));
            logger.debug("Parsed item: name={}, eanCode={}, comparisonPrice={}, quantity={}, totalPrice={}", name, eanCode, comparisonPrice, quantity, totalPrice);
            logger.debug("item: {}", new Item(name, totalPrice, quantity, eanCode, comparisonPrice));
            return Optional.of(new Item(name, totalPrice, quantity, eanCode, comparisonPrice));
        }
        return Optional.empty();
    }
}
