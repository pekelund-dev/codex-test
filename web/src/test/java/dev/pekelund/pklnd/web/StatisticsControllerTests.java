package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.firestore.ItemCategorizationService;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.firestore.TagService;
import dev.pekelund.pklnd.web.DashboardStatisticsService.DashboardStatistics;
import dev.pekelund.pklnd.web.TagStatisticsService;
import dev.pekelund.pklnd.web.assets.ViteManifest;
import dev.pekelund.pklnd.web.statistics.StatisticsController;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(StatisticsController.class)
@ContextConfiguration(classes = PknldApplication.class)
@Import(ViteManifest.class)
class StatisticsControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardStatisticsService dashboardStatisticsService;

    @MockitoBean
    private TagService tagService;

    @MockitoBean
    private ItemCategorizationService itemCategorizationService;

    @MockitoBean
    private ReceiptExtractionService receiptExtractionService;

    @MockitoBean
    private TagStatisticsService tagStatisticsService;

    @MockitoBean
    private FirestoreReadTotals firestoreReadTotals;

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void statisticsDashboard_ShouldRenderStatisticsView() throws Exception {
        when(dashboardStatisticsService.loadStatistics(any()))
            .thenReturn(new DashboardStatistics(
                0L,
                false,
                0L,
                0L,
                0L,
                false,
                0L,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                false,
                0L
            ));

        mockMvc.perform(get("/dashboard/statistics"))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard-statistics"));
    }
}
