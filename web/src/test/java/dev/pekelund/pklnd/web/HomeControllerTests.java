package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.config.SecurityConfig;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.web.assets.ViteManifest;
import dev.pekelund.pklnd.web.home.HomeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
@ContextConfiguration(classes = PknldApplication.class)
@Import({SecurityConfig.class, ViteManifest.class})
class HomeControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirestoreReadTotals firestoreReadTotals;

    @MockitoBean
    private GrantedAuthoritiesMapper oauthAuthoritiesMapper;

    @Test
    void homePage_ShouldRenderHomeView() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("home"));
    }

    @Test
    void aboutPage_ShouldRenderAboutView() throws Exception {
        mockMvc.perform(get("/about"))
            .andExpect(status().isOk())
            .andExpect(view().name("about"));
    }
}
