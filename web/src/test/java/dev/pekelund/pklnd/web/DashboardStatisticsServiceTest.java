package dev.pekelund.pklnd.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.pekelund.pklnd.firestore.FirestoreUserService;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.web.DashboardStatisticsService.DashboardStatistics;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class DashboardStatisticsServiceTest {

    private FirestoreUserService firestoreUserService;
    private ReceiptExtractionService receiptExtractionService;
    private ReceiptOwnerResolver receiptOwnerResolver;
    private DashboardStatisticsService service;

    @BeforeEach
    void setUp() {
        firestoreUserService = mock(FirestoreUserService.class);
        receiptExtractionService = mock(ReceiptExtractionService.class);
        receiptOwnerResolver = mock(ReceiptOwnerResolver.class);

        when(firestoreUserService.isEnabled()).thenReturn(true);
        when(firestoreUserService.countUsers()).thenReturn(10L);

        service = new DashboardStatisticsService(
            firestoreUserService,
            receiptExtractionService,
            receiptOwnerResolver
        );
    }

    @Test
    void calculatesYearlyTotalsCorrectly() throws Exception {
        // Arrange
        ReceiptOwner owner = new ReceiptOwner("user1", "Test User", "user1@example.com");
        Authentication auth = mock(Authentication.class);

        when(receiptOwnerResolver.resolve(auth)).thenReturn(owner);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        List<ParsedReceipt> receipts = List.of(
            createReceipt("2024-01-15", new BigDecimal("100.00")),
            createReceipt("2024-02-20", new BigDecimal("150.50")),
            createReceipt("2023-12-10", new BigDecimal("75.25")),
            createReceipt("2023-11-05", new BigDecimal("50.00"))
        );

        when(receiptExtractionService.listReceiptsForOwner(owner)).thenReturn(receipts);

        // Act
        DashboardStatistics stats = service.loadStatistics(auth);

        // Assert
        assertThat(stats.yearlyStatisticsAvailable()).isTrue();
        assertThat(stats.yearlyTotals()).hasSize(2);
        assertThat(stats.yearlyTotals().get(2024)).isEqualByComparingTo(new BigDecimal("250.50"));
        assertThat(stats.yearlyTotals().get(2023)).isEqualByComparingTo(new BigDecimal("125.25"));
    }

    @Test
    void calculatesMonthlyTotalsCorrectly() throws Exception {
        // Arrange
        ReceiptOwner owner = new ReceiptOwner("user1", "Test User", "user1@example.com");
        Authentication auth = mock(Authentication.class);

        when(receiptOwnerResolver.resolve(auth)).thenReturn(owner);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        List<ParsedReceipt> receipts = List.of(
            createReceipt("2024-01-15", new BigDecimal("100.00")),
            createReceipt("2024-01-20", new BigDecimal("50.00")),
            createReceipt("2024-02-10", new BigDecimal("75.50")),
            createReceipt("2023-12-25", new BigDecimal("200.00"))
        );

        when(receiptExtractionService.listReceiptsForOwner(owner)).thenReturn(receipts);

        // Act
        DashboardStatistics stats = service.loadStatistics(auth);

        // Assert
        assertThat(stats.yearlyStatisticsAvailable()).isTrue();
        assertThat(stats.monthlyTotals()).hasSize(2);

        Map<Month, BigDecimal> year2024 = stats.monthlyTotals().get(2024);
        assertThat(year2024).isNotNull();
        assertThat(year2024.get(Month.JANUARY)).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(year2024.get(Month.FEBRUARY)).isEqualByComparingTo(new BigDecimal("75.50"));

        Map<Month, BigDecimal> year2023 = stats.monthlyTotals().get(2023);
        assertThat(year2023).isNotNull();
        assertThat(year2023.get(Month.DECEMBER)).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void handlesEmptyReceiptList() throws Exception {
        // Arrange
        ReceiptOwner owner = new ReceiptOwner("user1", "Test User", "user1@example.com");
        Authentication auth = mock(Authentication.class);

        when(receiptOwnerResolver.resolve(auth)).thenReturn(owner);
        when(receiptExtractionService.isEnabled()).thenReturn(true);
        when(receiptExtractionService.listReceiptsForOwner(owner)).thenReturn(List.of());

        // Act
        DashboardStatistics stats = service.loadStatistics(auth);

        // Assert
        assertThat(stats.yearlyStatisticsAvailable()).isTrue();
        assertThat(stats.yearlyTotals()).isEmpty();
        assertThat(stats.monthlyTotals()).isEmpty();
    }

    @Test
    void handlesNullAmounts() throws Exception {
        // Arrange
        ReceiptOwner owner = new ReceiptOwner("user1", "Test User", "user1@example.com");
        Authentication auth = mock(Authentication.class);

        when(receiptOwnerResolver.resolve(auth)).thenReturn(owner);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        List<ParsedReceipt> receipts = List.of(
            createReceipt("2024-01-15", null),
            createReceipt("2024-02-20", new BigDecimal("100.00"))
        );

        when(receiptExtractionService.listReceiptsForOwner(owner)).thenReturn(receipts);

        // Act
        DashboardStatistics stats = service.loadStatistics(auth);

        // Assert
        assertThat(stats.yearlyStatisticsAvailable()).isTrue();
        assertThat(stats.yearlyTotals().get(2024)).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void returnsUnavailableWhenReceiptsDisabled() {
        // Arrange
        Authentication auth = mock(Authentication.class);
        when(receiptExtractionService.isEnabled()).thenReturn(false);

        // Act
        DashboardStatistics stats = service.loadStatistics(auth);

        // Assert
        assertThat(stats.yearlyStatisticsAvailable()).isFalse();
        assertThat(stats.yearlyTotals()).isEmpty();
        assertThat(stats.monthlyTotals()).isEmpty();
    }

    private ParsedReceipt createReceipt(String date, BigDecimal totalAmount) {
        Map<String, Object> general = totalAmount != null
            ? Map.of("receiptDate", date, "totalAmount", totalAmount)
            : Map.of("receiptDate", date);

        Instant instant = LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant();

        return new ParsedReceipt(
            "receipt-" + date,
            "test-bucket",
            "test-object.pdf",
            "/receipts/test-object.pdf",
            new ReceiptOwner("user1", "Test User", "user1@example.com"),
            "COMPLETED",
            null,
            instant,
            general,
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null
        );
    }
}
