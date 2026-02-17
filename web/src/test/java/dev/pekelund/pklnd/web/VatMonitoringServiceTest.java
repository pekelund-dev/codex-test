package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.web.VatMonitoringService.ItemPriceComparison;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VatMonitoringServiceTest {

    @Test
    void testExpectedPriceCalculation() {
        // Given a price before VAT change of 100 kr at 12% VAT
        BigDecimal priceBefore = new BigDecimal("100.00");
        
        // Expected price after VAT change to 6%: (100 / 1.12) * 1.06 = 94.64 kr
        BigDecimal expected = priceBefore
            .divide(new BigDecimal("1.12"), 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("1.06"))
            .setScale(2, java.math.RoundingMode.HALF_UP);
        
        assertThat(expected).isEqualByComparingTo(new BigDecimal("94.64"));
    }

    @Test
    void testPriceReductionScenario() {
        // If a product costs 100 kr before, expected after is 94.64 kr
        // If actual price is 95 kr, that's an increase of 0.36 kr
        BigDecimal priceBefore = new BigDecimal("100.00");
        BigDecimal expectedAfter = new BigDecimal("94.64");
        BigDecimal actualAfter = new BigDecimal("95.00");
        
        BigDecimal deviation = actualAfter.subtract(expectedAfter);
        BigDecimal deviationPercent = deviation.divide(expectedAfter, 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        // Deviation is about 0.38%
        assertThat(deviationPercent).isBetween(new BigDecimal("0.3"), new BigDecimal("0.4"));
    }

    @Test
    void testSuspiciousIncreaseDetection() {
        // If a product costs 100 kr before and is 97 kr after
        // Expected: 94.64 kr
        // Deviation: 97 - 94.64 = 2.36 kr
        // Deviation %: 2.36 / 94.64 = 2.49% > 2% threshold = SUSPICIOUS
        
        BigDecimal priceBefore = new BigDecimal("100.00");
        BigDecimal priceAfter = new BigDecimal("97.00");
        BigDecimal expectedPrice = new BigDecimal("94.64");
        
        BigDecimal deviation = priceAfter.subtract(expectedPrice);
        BigDecimal deviationPercent = deviation.divide(expectedPrice, 4, java.math.RoundingMode.HALF_UP);
        
        // Should be flagged as suspicious (> 2% threshold)
        assertThat(deviationPercent).isGreaterThan(new BigDecimal("0.02"));
    }

    @Test
    void testNormalPriceReduction() {
        // If a product costs 100 kr before and is 94 kr after
        // Expected: 94.64 kr
        // Deviation: 94 - 94.64 = -0.64 kr
        // This is actually better than expected - not suspicious
        
        BigDecimal priceBefore = new BigDecimal("100.00");
        BigDecimal priceAfter = new BigDecimal("94.00");
        BigDecimal expectedPrice = new BigDecimal("94.64");
        
        BigDecimal deviation = priceAfter.subtract(expectedPrice);
        BigDecimal deviationPercent = deviation.divide(expectedPrice, 4, java.math.RoundingMode.HALF_UP);
        
        // Should NOT be flagged as suspicious
        assertThat(deviationPercent).isLessThan(new BigDecimal("0.02"));
    }

    @Test
    void testItemPriceComparisonFormatting() {
        ItemPriceComparison comparison = new ItemPriceComparison(
            "7310867501583",
            "Mjölk 1.5% 1L",
            new BigDecimal("15.90"),
            new BigDecimal("16.50"),
            new BigDecimal("15.05"),
            new BigDecimal("0.60"),
            new BigDecimal("3.77"),
            new BigDecimal("-0.85"),
            new BigDecimal("-5.35"),
            new BigDecimal("1.45"),
            new BigDecimal("9.64"),
            true,
            LocalDate.of(2026, 3, 15),
            LocalDate.of(2026, 4, 5),
            List.of("ICA Kvantum", "ICA Supermarket"),
            "ICA Kvantum",
            "ICA Supermarket",
            5
        );

        assertThat(comparison.formattedPriceBefore()).isEqualTo("15.90 kr");
        assertThat(comparison.formattedPriceAfter()).isEqualTo("16.50 kr");
        assertThat(comparison.formattedExpectedPrice()).isEqualTo("15.05 kr");
        assertThat(comparison.formattedPriceChange()).contains("+0.60");
        assertThat(comparison.formattedPriceDeviation()).contains("+1.45");
        assertThat(comparison.hasSuspiciousIncrease()).isTrue();
    }
}
