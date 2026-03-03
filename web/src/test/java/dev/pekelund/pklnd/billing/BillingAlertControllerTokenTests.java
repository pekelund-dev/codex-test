package dev.pekelund.pklnd.billing;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.config.SecurityConfig;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.web.assets.ViteManifest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BillingAlertController.class)
@ContextConfiguration(classes = PknldApplication.class)
@Import({SecurityConfig.class, ViteManifest.class})
@TestPropertySource(properties = "billing.alert.token=test-secret-token")
class BillingAlertControllerTokenTests {

    private static final String VALID_PUBSUB_BODY = """
            {
              "message": {
                "data": "eyJjb3N0QW1vdW50IjogNS4wLCAiYnVkZ2V0QW1vdW50IjogMTAuMH0=",
                "messageId": "1234",
                "publishTime": "2024-01-01T00:00:00Z"
              },
              "subscription": "projects/test/subscriptions/billing"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingShutdownService billingShutdownService;

    @MockitoBean
    private FirestoreReadTotals firestoreReadTotals;

    @MockitoBean
    private GrantedAuthoritiesMapper oauthAuthoritiesMapper;

    @Test
    void billingAlert_WithValidToken_ShouldReturn200() throws Exception {
        mockMvc.perform(post("/api/billing/alerts")
                .param("token", "test-secret-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_PUBSUB_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void billingAlert_WithInvalidToken_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/api/billing/alerts")
                .param("token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_PUBSUB_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void billingAlert_WithMissingToken_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/api/billing/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_PUBSUB_BODY))
                .andExpect(status().isForbidden());
    }
}
