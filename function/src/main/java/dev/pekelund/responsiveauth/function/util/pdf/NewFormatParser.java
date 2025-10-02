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
public class NewFormatParser extends BaseReceiptParser {
    private static final Logger logger = LoggerFactory.getLogger(NewFormatParser.class);
    
    // Updated regex for new format: name, EAN, price, quantity, total
    private static final String regexNewFormat = "(?<name>.+?) (?<eanCode>\\d{8,13}) (?<price>\\d+\\.\\d{2}) (?<quantity>\\d+(?:\\.\\d+)?\\s(?:st|kg)) (?<totalPrice>\\d+\\.\\d{2})";
    private static final String regexDiscount = "(?<name>.+?)\\s-([\\d]+\\.[\\d]{2})";
    
    @Override
    public boolean supportsFormat(ReceiptFormat format) {
        return format == ReceiptFormat.NEW_FORMAT;
    }
    
    @Override
    public Receipt parse(String[] pdfData, String userId, URL url) {
        // Use the common functionality from BaseReceiptParser if needed
        Receipt receipt = new Receipt();
        receipt.setUserId(userId);
        receipt.setItems(new ArrayList<>());
        receipt.setUrl(url);
        
        // Implementation for the new format
        // Note: This is a sample implementation that should be adjusted
        // to match the actual new format of the receipt
        
        try {
            // Extract store name - assuming it's at index 1
            String store = pdfData[1];
            receipt.setStore(store);
            
            // Find date and total amount
            Pattern datePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
            Pattern totalPattern = Pattern.compile("Total\\s*([\\d,.]+)");
            
            LocalDate date = null;
            BigDecimal total = null;
            
            for (String line : pdfData) {
                Matcher dateMatcher = datePattern.matcher(line);
                if (dateMatcher.find() && date == null) {
                    date = LocalDate.parse(dateMatcher.group(1));
                }
                Matcher totalMatcher = totalPattern.matcher(line);
                if (totalMatcher.find()) {
                    String amt = totalMatcher.group(1).replace(",", ".");
                    total = new BigDecimal(amt);
                }
            }
            receipt.setDate(date);
            receipt.setAmount(total);
            
            // Find the start of items section
            int itemsStartIndex = -1;
            for (int i = 0; i < pdfData.length; i++) {
                if (pdfData[i].contains("Beskrivning Artikelnummer Pris Mängd Summa(SEK)")) {
                    itemsStartIndex = i + 1;
                    break;
                }
            }
            // Items go until a line containing "Moms" or "Total"
            int itemsEndIndex = pdfData.length - 1;
            for (int i = itemsStartIndex; i < pdfData.length; i++) {
                if (pdfData[i].contains("Moms") || pdfData[i].startsWith("Total")) {
                    itemsEndIndex = i - 1;
                    break;
                }
            }
            // Parse items and discounts
            for (int i = itemsStartIndex; i <= itemsEndIndex; i++) {
                String line = pdfData[i];
                Optional<Item> item = parseItem(line);
                if (item.isPresent()) {
                    Item parsedItem = item.get();
                    // Check for discount in the next line
                    if (i + 1 <= itemsEndIndex && isDiscountLine(pdfData[i + 1])) {
                        Discount discount = parseDiscount(pdfData[i + 1]);
                        if (discount != null) {
                            discount.setItem(parsedItem);
                            parsedItem.addDiscount(discount);
                        }
                        i++; // Skip the discount line
                    }
                    parsedItem.setReceipt(receipt);
                    receipt.getItems().add(parsedItem);
                } else if (isDiscountLine(line)) {
                    // Standalone discount (not attached to an item)
                    Discount discount = parseDiscount(line);
                    if (discount != null) {
                        // Optionally attach to last item
                        if (!receipt.getItems().isEmpty()) {
                            Item lastItem = receipt.getItems().get(receipt.getItems().size() - 1);
                            discount.setItem(lastItem);
                            lastItem.addDiscount(discount);
                        }
                    }
                } else if (!line.trim().isEmpty() && !line.contains("Beskrivning")) {
                    Error error = new Error(null, i, line, receipt);
                    receipt.addError(error);
                    logger.warn("Failed to parse item: {}", error);
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing receipt in new format", e);
            Error error = new Error(null, 0, "Error parsing receipt: " + e.getMessage(), receipt);
            receipt.addError(error);
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
            BigDecimal discountAmount = new BigDecimal(matcher.group(2));
            logger.debug("Parsed discount: name={}, discountAmount={}", name, discountAmount);
            return new Discount(null, name, discountAmount, null);
        }
        return null;
    }
    
    private Optional<Item> parseItem(String line) {
        Pattern itemPattern = Pattern.compile(regexNewFormat);
        Matcher matcher = itemPattern.matcher(line);
        if (matcher.matches()) {
            String name = matcher.group("name");
            String eanCode = matcher.group("eanCode");
            BigDecimal price = new BigDecimal(matcher.group("price"));
            String quantity = matcher.group("quantity");
            BigDecimal totalPrice = new BigDecimal(matcher.group("totalPrice"));
            logger.debug("Parsed item: name={}, eanCode={}, price={}, quantity={}, totalPrice={}", name, eanCode, price, quantity, totalPrice);
            return Optional.of(new Item(name, totalPrice, quantity, eanCode, price));
        }
        return Optional.empty();
    }
}