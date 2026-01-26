package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.PknldApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PknldApplication.class)
@AutoConfigureMockMvc
class SecurityRedirectTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void receiptsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/receipts"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrlPattern("**/login"));
    }
}
