package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.config.SecurityConfig;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.firestore.FirestoreUserService;
import dev.pekelund.pklnd.web.assets.ViteManifest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = PknldApplication.class)
@Import({SecurityConfig.class, ViteManifest.class, FirestoreReadTotals.class})
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirestoreUserService firestoreUserService;

    @MockitoBean
    private GrantedAuthoritiesMapper oauthAuthoritiesMapper;

    @Test
    void registerPage_WhenEnabled_ShouldRenderRegisterView() throws Exception {
        when(firestoreUserService.isEnabled()).thenReturn(true);

        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(content().string(containsString("action=\"/register\"")));
    }

    @Test
    void registerPage_WhenDisabled_ShouldRenderRegisterViewWithWarning() throws Exception {
        when(firestoreUserService.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"));
    }
}
