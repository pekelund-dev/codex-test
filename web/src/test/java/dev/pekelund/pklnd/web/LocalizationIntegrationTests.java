package dev.pekelund.pklnd.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LocalizationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void defaultsToSwedishContent() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Digitalisera kvittoredovisningen")));
    }

    @Test
    void switchingToEnglishSetsCookieAndRendersEnglishCopy() throws Exception {
        mockMvc.perform(get("/").param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(cookie().value("pklnd-lang", "en"))
            .andExpect(content().string(containsString("Digitise your receipt reporting")));
    }

    @Test
    void honoursLanguageCookieOnSubsequentRequests() throws Exception {
        Cookie englishCookie = new Cookie("pklnd-lang", "en");
        englishCookie.setPath("/");

        mockMvc.perform(get("/").cookie(englishCookie))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Digitise your receipt reporting")));
    }
}
