package dev.pekelund.pklnd.receiptparser.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ReceiptParserErrorsTest {

    private final CodexParser parser = new CodexParser();

    @Test
    void parseReconciliationLines() {
        String[] lines = new String[] {
            "Kvitto",
            "ICA Kvantum Malmborgs Caroli",
            "2025-01-08",
            "Beskrivning Artikelnummer Pris Mängd Summa(SEK)",
            "Broccoli filmad 7318690662952 20,95 1,00 st 20,95",
            "          Delavstämning korrekt",
            "Avstämning korrekt.",
            "Retur",
            "Betalat 20,95"
        };

        LegacyParsedReceipt receipt = parser.parse(lines, ReceiptFormat.NEW_FORMAT);

        assertThat(receipt.errors()).isEmpty();
        assertThat(receipt.items()).hasSize(1);
        assertThat(receipt.items().get(0).getName()).isEqualTo("Broccoli filmad");
        assertThat(receipt.reconciliationStatus()).isEqualTo(ReconciliationStatus.COMPLETE);
    }

    @Test
    void parsePantLine() {
        String[] lines = new String[] {
            "Kvitto",
            "ICA Kvantum Malmborgs Caroli",
            "2025-01-08",
            "Beskrivning Artikelnummer Pris Mängd Summa(SEK)",
            "CocaCola 50cl 7318690662952 10,00 1,00 st 10,00",
            "Pant  1,00 1 1,00",
            "Betalat 11,00"
        };

        LegacyParsedReceipt receipt = parser.parse(lines, ReceiptFormat.NEW_FORMAT);

        assertThat(receipt.errors()).isEmpty();
        assertThat(receipt.items()).hasSize(2);
        
        LegacyReceiptItem pantItem = receipt.items().get(1);
        assertThat(pantItem.getName()).isEqualTo("Pant");
        assertThat(pantItem.getEanCode()).isNull();
        assertThat(pantItem.getUnitPrice()).isEqualByComparingTo("1.00");
        assertThat(pantItem.getQuantity()).isEqualTo("1");
        assertThat(pantItem.getTotalPrice()).isEqualByComparingTo("1.00");
    }

    @Test
    void parsePantLineWithQuantity() {
         // Case: "Pant  3,00 2 6,00"
         String[] lines = new String[] {
            "Kvitto",
            "ICA Kvantum Malmborgs Caroli",
            "2025-10-12",
            "Beskrivning Artikelnummer Pris Mängd Summa(SEK)",
            "Pant  3,00 2 6,00",
            "Betalat 6,00"
        };

        LegacyParsedReceipt receipt = parser.parse(lines, ReceiptFormat.NEW_FORMAT);

        assertThat(receipt.errors()).isEmpty();
        assertThat(receipt.items()).hasSize(1);
        
        LegacyReceiptItem pantItem = receipt.items().get(0);
        assertThat(pantItem.getName()).isEqualTo("Pant");
        assertThat(pantItem.getUnitPrice()).isEqualByComparingTo("3.00");
        assertThat(pantItem.getQuantity()).isEqualTo("2");
        assertThat(pantItem.getTotalPrice()).isEqualByComparingTo("6.00");
    }

    @Test
    void shouldParseItemWithMissingUnitPrice() {
        String[] lines = new String[] {
            "Beskrivning Artikelnummer Pris Mängd Summa(SEK)",
            "Kolonial ätbart 19500 1,00 st 70,90",
            "Moms % Moms Netto Brutto"
        };
        
        LegacyParsedReceipt receipt = parser.parse(lines, ReceiptFormat.NEW_FORMAT);
        
        assertThat(receipt.items()).hasSize(1);
        LegacyReceiptItem item = receipt.items().get(0);
        assertThat(item.getName()).isEqualTo("Kolonial ätbart");
        assertThat(item.getEanCode()).isEqualTo("19500");
        assertThat(item.getQuantity()).isEqualTo("1,00 st");
        assertThat(item.getTotalPrice()).isEqualByComparingTo(new BigDecimal("70.90"));
        assertThat(item.getUnitPrice()).isNull(); 
    }
}
