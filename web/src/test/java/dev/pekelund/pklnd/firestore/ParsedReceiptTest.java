package dev.pekelund.pklnd.firestore;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParsedReceiptTest {

    @Test
    void recalculatesUnitPriceForStarredItems() {
        ParsedReceipt receipt = receiptWithItems(List.of(Map.of(
            "name", "*Gurka",
            "unitPrice", new BigDecimal("14.25"),
            "quantity", "2,00 st",
            "totalPrice", new BigDecimal("35.90")
        )));

        Map<String, Object> item = receipt.displayItems().get(0);
        assertThat(item.get("displayUnitPrice")).isEqualTo("17.95");
        assertThat(item.get("displayQuantity")).isEqualTo("2 st");
        assertThat(item.get("displayTotalPrice")).isEqualTo("35.90");
    }

    @Test
    void recalculatesWeightWhenTotalsDoNotMatch() {
        ParsedReceipt receipt = receiptWithItems(List.of(Map.of(
            "name", "Fransk lantsalami",
            "unitPrice", new BigDecimal("590.00"),
            "quantity", "1,00 st",
            "totalPrice", new BigDecimal("15.34")
        )));

        Map<String, Object> item = receipt.displayItems().get(0);
        assertThat(item.get("displayQuantity")).isEqualTo("0.026 kg");
        assertThat(item.get("displayUnitPrice")).isEqualTo("590.00");
        assertThat(item.get("displayTotalPrice")).isEqualTo("15.34");
    }

    @Test
    void showsWholeNumbersForNonWeightQuantities() {
        ParsedReceipt receipt = receiptWithItems(List.of(Map.of(
            "name", "Yoghurt",
            "unitPrice", new BigDecimal("20.00"),
            "quantity", "1,00 st",
            "totalPrice", new BigDecimal("20.00")
        )));

        Map<String, Object> item = receipt.displayItems().get(0);
        assertThat(item.get("displayQuantity")).isEqualTo("1 st");
        assertThat(item.get("displayUnitPrice")).isEqualTo("20.00");
        assertThat(item.get("displayTotalPrice")).isEqualTo("20.00");
    }

    @Test
    void keepsExistingWeightQuantitiesWithThreeDecimals() {
        ParsedReceipt receipt = receiptWithItems(List.of(Map.of(
            "name", "Potatis",
            "unitPrice", new BigDecimal("15.95"),
            "quantity", "0,75 kg",
            "totalPrice", new BigDecimal("11.96")
        )));

        Map<String, Object> item = receipt.displayItems().get(0);
        assertThat(item.get("displayQuantity")).isEqualTo("0.750 kg");
        assertThat(item.get("displayUnitPrice")).isEqualTo("15.95");
        assertThat(item.get("displayTotalPrice")).isEqualTo("11.96");
    }

    private ParsedReceipt receiptWithItems(List<Map<String, Object>> items) {
        return new ParsedReceipt(
            "id",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of(),
            items,
            ParsedReceipt.ReceiptItemHistory.empty(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null
        );
    }

    @Test
    void calculatesGeneralDiscountTotal() {
        ParsedReceipt receipt = new ParsedReceipt(
            "id",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of(),
            List.of(),
            ParsedReceipt.ReceiptItemHistory.empty(),
            List.of(),
            List.of(
                Map.of("description", "Medlemsrabatt", "amount", new BigDecimal("5.00")),
                Map.of("description", "Kampanj", "amount", new BigDecimal("10.50"))
            ),
            List.of(),
            null,
            null,
            null,
            null
        );

        assertThat(receipt.generalDiscountTotal()).isEqualByComparingTo(new BigDecimal("15.50"));
        assertThat(receipt.formattedGeneralDiscount()).isEqualTo("15.50");
    }

    @Test
    void calculatesItemDiscountTotal() {
        ParsedReceipt receipt = receiptWithItems(List.of(
            Map.of(
                "name", "Yoghurt",
                "totalPrice", new BigDecimal("20.00"),
                "discounts", List.of(
                    Map.of("description", "Item discount", "amount", new BigDecimal("2.00"))
                )
            ),
            Map.of(
                "name", "Juice",
                "totalPrice", new BigDecimal("15.00"),
                "discounts", List.of(
                    Map.of("description", "Item discount", "amount", new BigDecimal("3.50"))
                )
            )
        ));

        assertThat(receipt.itemDiscountTotal()).isEqualByComparingTo(new BigDecimal("5.50"));
        assertThat(receipt.formattedItemDiscount()).isEqualTo("5.50");
    }

    @Test
    void calculatesTotalDiscountAmount() {
        ParsedReceipt receipt = new ParsedReceipt(
            "id",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of(),
            List.of(
                Map.of(
                    "name", "Yoghurt",
                    "totalPrice", new BigDecimal("20.00"),
                    "discounts", List.of(
                        Map.of("description", "Item discount", "amount", new BigDecimal("2.00"))
                    )
                )
            ),
            ParsedReceipt.ReceiptItemHistory.empty(),
            List.of(),
            List.of(
                Map.of("description", "Medlemsrabatt", "amount", new BigDecimal("5.00"))
            ),
            List.of(),
            null,
            null,
            null,
            null
        );

        assertThat(receipt.totalDiscountAmount()).isEqualByComparingTo(new BigDecimal("7.00"));
        assertThat(receipt.formattedTotalDiscount()).isEqualTo("7.00");
    }

    @Test
    void returnsZeroWhenNoDiscountsPresent() {
        ParsedReceipt receipt = receiptWithItems(List.of(
            Map.of(
                "name", "Yoghurt",
                "totalPrice", new BigDecimal("20.00")
            )
        ));

        assertThat(receipt.generalDiscountTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(receipt.itemDiscountTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(receipt.totalDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(receipt.formattedTotalDiscount()).isEqualTo("0.00");
    }

    @Test
    void handlesNegativeDiscountAmounts() {
        ParsedReceipt receipt = new ParsedReceipt(
            "id",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of(),
            List.of(),
            ParsedReceipt.ReceiptItemHistory.empty(),
            List.of(),
            List.of(
                Map.of("description", "Rabatt", "amount", new BigDecimal("-5.00"))
            ),
            List.of(),
            null,
            null,
            null,
            null
        );

        // Should use absolute value for negative discounts
        assertThat(receipt.generalDiscountTotal()).isEqualByComparingTo(new BigDecimal("5.00"));
    }
}
