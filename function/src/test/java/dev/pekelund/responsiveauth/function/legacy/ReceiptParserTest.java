package dev.pekelund.responsiveauth.function.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReceiptParserTest {

    private final ReceiptParser parser = new ReceiptParser();

    @Test
    void classifiesLargeDiscountAsGeneralDiscount() {
        String[] lines = {
            "Beskrivning Artikelnummer Pris Mängd Summa(SEK)",
            "Yoghurt 1234567890123 20,00 1,00 st 20,00",
            "Familjerabatt  -66,81",
            "Betalat 1269,43"
        };

        LegacyParsedReceipt receipt = parser.parse(lines, ReceiptFormat.NEW_FORMAT);

        assertThat(receipt.items()).hasSize(1);
        assertThat(receipt.items().get(0).discounts()).isEmpty();

        assertThat(receipt.generalDiscounts()).hasSize(1);
        LegacyReceiptDiscount discount = receipt.generalDiscounts().get(0);
        assertThat(discount.description()).isEqualTo("Familjerabatt");
        assertThat(discount.amount()).isEqualByComparingTo(new BigDecimal("-66.81"));
    }

    @Test
    void keepsSmallerDiscountAttachedToItem() {
        String[] lines = {
            "Beskrivning Artikelnummer Pris Mängd Summa(SEK)",
            "Jordgubbar 1234567890123 10,00 2,00 st 20,00",
            "2 för 15:-  -5,00",
            "Betalat 15,00"
        };

        LegacyParsedReceipt receipt = parser.parse(lines, ReceiptFormat.NEW_FORMAT);

        assertThat(receipt.generalDiscounts()).isEmpty();

        assertThat(receipt.items()).hasSize(1);
        List<LegacyReceiptDiscount> discounts = receipt.items().get(0).discounts();
        assertThat(discounts).hasSize(1);
        LegacyReceiptDiscount itemDiscount = discounts.get(0);
        assertThat(itemDiscount.description()).isEqualTo("2 för 15:-");
        assertThat(itemDiscount.amount()).isEqualByComparingTo(new BigDecimal("-5.00"));
    }
}
