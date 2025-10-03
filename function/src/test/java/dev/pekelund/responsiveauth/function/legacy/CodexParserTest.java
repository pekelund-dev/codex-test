package dev.pekelund.responsiveauth.function.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodexParserTest {

    private final CodexParser parser = new CodexParser();

    @Test
    void parsesLargeSwedishReceipt() {
        String[] lines = RECEIPT_TEXT.split("\\n");

        LegacyParsedReceipt receipt = parser.parse(lines, ReceiptFormat.NEW_FORMAT);

        assertThat(receipt.format()).isEqualTo(ReceiptFormat.NEW_FORMAT);
        assertThat(receipt.storeName()).isEqualTo("ICA Kvantum Emporia");
        assertThat(receipt.receiptDate()).isEqualTo(LocalDate.of(2025, 10, 2));
        assertThat(receipt.totalAmount()).isEqualByComparingTo(new BigDecimal("1269.43"));

        List<LegacyReceiptItem> items = receipt.items();
        assertThat(items).hasSize(38);

        LegacyReceiptItem first = items.get(0);
        assertThat(first.getName()).isEqualTo("Broccoli filmad");
        assertThat(first.getEanCode()).isEqualTo("7318690662952");
        assertThat(first.getQuantity()).isEqualTo("2,00 st");
        assertThat(first.getTotalPrice()).isEqualByComparingTo(new BigDecimal("41.90"));

        LegacyReceiptItem gurka = items.stream()
            .filter(item -> item.getName().contains("Gurka"))
            .findFirst()
            .orElseThrow();
        assertThat(gurka.getDiscounts()).singleElement()
            .satisfies(discount -> {
                assertThat(discount.description()).isEqualTo("2 för 30:-");
                assertThat(discount.amount()).isEqualByComparingTo(new BigDecimal("-5.90"));
            });

        LegacyReceiptItem familyDiscount = items.stream()
            .filter(item -> item.getName().equals("Familjerabatt"))
            .findFirst()
            .orElseThrow();
        assertThat(familyDiscount.getTotalPrice()).isEqualByComparingTo(new BigDecimal("-66.81"));

        assertThat(receipt.vats()).hasSize(2);
        assertThat(receipt.vats().get(0).rate()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(receipt.vats().get(0).taxAmount()).isEqualByComparingTo(new BigDecimal("128.60"));

        assertThat(receipt.errors()).isEmpty();
    }

    private static final String RECEIPT_TEXT = String.join("\n",
        "Kvitto",
        "ICA Kvantum Emporia",
        "Hyllie stations väg 22",
        "21532 Malmö",
        "ICA KVANTUM MALMBORGS EMPORIA",
        "HYLLIE STATIONSVÄG 22",
        "215 32 MALMÖ",
        "Tel.nr. 040 - 651 80 00",
        "Org.nr. 559329-8556",
        "Datum",
        "Tid",
        "Org nr",
        "Kvitto nr",
        "Kassa",
        "Kassör",
        "2025-10-02",
        "20:20",
        "SE559329855601",
        "7800",
        "55",
        "1231231231",
        "Beskrivning Artikelnummer Pris Mängd Summa(SEK)",
        "Broccoli filmad 7318690662952 20,95 2,00 st 41,90",
        "Eko Mellanmjö 1,5% 7310867501583 26,95 2,00 st 53,90",
        "Filet Pieces 8445290003027 92,00 1,00 st 92,00",
        "Fisherm salm sf 50357116 12,95 1,00 st 12,95",
        "Fransk lantsalami 2000765300000 590,00 1,00 st 15,34",
        "Fuet 7350027793915 46,95 1,00 st 46,95",
        "GodM Äpplejuice 7310865081650 44,95 2,00 st 89,90",
        "*Gurka 2092459500000 14,25 2,00 st 35,90",
        "2 för 30:-  -5,90",
        "Ingefära 2092461200000 34,95 1,00 st 2,38",
        "Jamón Serrano 7331631007667 89,00 1,00 st 89,00",
        "Koriander 7350018560649 32,95 1,00 st 32,95",
        "Kål Vit Färsk 2092305100000 14,95 1,00 st 29,42",
        "*Lime 2092404800000 3,17 3,00 st 17,85",
        "3 för 10:-  -7,85",
        "Lösviktsgodis 2000136300000 129,00 1,00 st 19,09",
        "Mild Vaniljyog Pär 7310867512282 28,95 1,00 st 28,95",
        "Mozzarella 21% Riv 7311875904342 74,95 1,00 st 74,95",
        "Näsdukar 10p 7318690170501 13,95 1,00 st 13,95",
        "Panaeng curry mild 7311520010862 39,95 1,00 st 39,95",
        "Peppar Röd 2092469700000 4,95 2,00 st 9,90",
        "Pizza Kit 3392590601536 33,95 2,00 st 67,90",
        "Potatis fast 2092472800000 15,95 1,00 st 9,54",
        "Purjolök 2092462900000 24,95 1,00 st 12,48",
        "*Ravioli Tomat/Mozz 8001665700030 26,12 1,00 st 50,95",
        "Rana generell 5 kr  -5,00",
        "Rosor 5708870101029 59,00 1,00 st 59,00",
        "*Smör normalsaltat 7310865005168 52,25 1,00 st 74,95",
        "55:- Max 1  -19,95",
        "Snabbmakaroner 7310130003554 24,95 1,00 st 24,95",
        "Stillahavsspätta 5700001859786 35,95 2,00 st 71,90",
        "Svartrökt skinka 2000752300000 240,00 1,00 st 30,24",
        "Sweet chili sauce 8850058004657 34,95 1,00 st 34,95",
        "Teriyaki Sauce 7311310035341 47,95 1,00 st 47,95",
        "*Tomat Piccolini 7318690013563 19,00 1,00 st 35,95",
        "Scanning 20:- Max 1  -15,95",
        "*Tortelloni Svamp 8001665128698 30,87 1,00 st 50,95",
        "2 för 65:-  -36,90",
        "*Vanilj jordgubbsås 8711327462076 0,00 1,00 st 15,95",
        "GLASS  -15,95",
        "Vetekaka 24-p 7311800009531 34,95 1,00 st 34,95",
        "Vispgrädde 40% 7310867003339 24,95 1,00 st 24,95",
        "Wok Thai Style 7310500187150 28,95 1,00 st 28,95",
        "Yogh Van/blåb 2,5% 7310867512831 20,00 1,00 st 20,00",
        "Familjerabatt  -66,81",
        "Betalat 1269,43",
        "Moms % Moms Netto Brutto",
        "12,00 128,60 1071,46 1200,06",
        "25,00 13,87 55,50 69,37",
        "Erhållen rabatt -174,31",
        "Avrundning 0,00",
        "Kort 1269,43",
        "Betalningsinformation",
        "Term:1710026528      SWE:9847583",
        "Visa Credit          ************0490",
        "Butik:13771 ",
        "2025-10-02 20:19     AID:A0000000031010",
        "TVR:0000000000       TSI:0000",
        "Ref:002652874423 002 Rsp:00 199434 K-1 7",
        "Visa Contactless",
        "Verifierat av enhet",
        "Köp         1,269,43",
        "Varav moms    142,47",
        "Totalt SEK  1,269,43",
        "Spara kvittot",
        "INGEN BYTESRÄTT PÅ",
        "KYL- OCH FRYSVAROR",
        "TACK FÖR BESÖKET!",
        "VÄLKOMMEN ÅTER",
        "Returkod",
        "4401377155002477911002253"
    );
}
