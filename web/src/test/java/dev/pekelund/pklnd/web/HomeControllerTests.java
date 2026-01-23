package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.web.home.HomeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
@ContextConfiguration(classes = PknldApplication.class)
class HomeControllerTests {

    @Autowired
    private MockMvc mockMvc;

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
