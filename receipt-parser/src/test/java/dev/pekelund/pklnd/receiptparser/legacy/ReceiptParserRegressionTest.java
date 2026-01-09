package dev.pekelund.pklnd.receiptparser.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ReceiptParserRegressionTest {

    private final CodexParser parser = new CodexParser();

    @Test
    void parsesReceiptWithReturLine() {
        String[] lines = {
            "Kvitto",
            "ICA Kvantum Malmborgs Caroli",
            "Kvitto nr",
            "Beskrivning Artikelnummer Pris Mängd Summa(SEK)",
            "Retur",
            "scampi rå 7315630033155 159,00 1,00 st -159,00",
            "Aluminiumformar 7318690067238 17,90 1,00 st 17,90",
            "scampi rå 7315630033155 159,00 1,00 st 159,00",
            "Betalat 17,90",
            "Moms % Moms Netto Brutto"
        };

        LegacyParsedReceipt receipt = parser.parse(lines, ReceiptFormat.NEW_FORMAT);

        assertThat(receipt.items()).hasSize(3);
        assertThat(receipt.items().get(0).getName()).isEqualTo("scampi rå");
        assertThat(receipt.items().get(1).getName()).isEqualTo("Aluminiumformar");
        assertThat(receipt.items().get(2).getName()).isEqualTo("scampi rå");
    }

    @Test
    void parsesReturnReceiptWithPant() {
        String[] lines = {
            "Kvitto",
            "Maxi ICA Stormarknad Västra Hamnen",
            "Kvitto nr",
            "Beskrivning Artikelnummer Pris Mängd Summa(SEK)",
            "Retur",
            "Julmust Sfri 12p 7310403041832 67,90 1,00 st -55,90",
            "Pant  12,00 1 -12,00",
            "Betalat 67,90"
        };
        LegacyParsedReceipt receipt = parser.parse(lines, ReceiptFormat.NEW_FORMAT);
        assertThat(receipt.items()).hasSize(2);
        assertThat(receipt.items().get(1).getName()).isEqualTo("Pant");
        assertThat(receipt.items().get(1).getTotalPrice()).isEqualByComparingTo("-12.00");
    }
}
