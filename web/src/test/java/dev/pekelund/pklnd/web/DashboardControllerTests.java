package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.firestore.FirestoreBackupService;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.firestore.FirestoreUserService;
import dev.pekelund.pklnd.web.assets.ViteManifest;
import dev.pekelund.pklnd.web.dashboard.DashboardController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.MessageSource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DashboardController.class)
@ContextConfiguration(classes = PknldApplication.class)
@Import(ViteManifest.class)
class DashboardControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirestoreUserService firestoreUserService;

    @MockitoBean
    private MessageSource messageSource;

    @MockitoBean
    private FirestoreBackupService firestoreBackupService;

    @MockitoBean
    private FirestoreReadTotals firestoreReadTotals;

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void dashboard_ShouldRenderDashboardView() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard"));
    }
}
