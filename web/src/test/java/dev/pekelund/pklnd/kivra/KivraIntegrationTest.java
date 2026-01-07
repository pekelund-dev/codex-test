package dev.pekelund.pklnd.kivra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "kivra.enabled=true",
    "kivra.personal-number=199001011234"
})
class KivraIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertNotNull(context);
    }

    @Test
    void kivraPropertiesAreLoaded() {
        KivraProperties properties = context.getBean(KivraProperties.class);
        assertThat(properties).isNotNull();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getPersonalNumber()).isEqualTo("199001011234");
    }

    @Test
    void kivraClientIsAvailable() {
        KivraClient client = context.getBean(KivraClient.class);
        assertThat(client).isNotNull();
        assertThat(client.isAuthenticated()).isFalse();
    }

    @Test
    void kivraSyncServiceIsAvailable() {
        KivraSyncService service = context.getBean(KivraSyncService.class);
        assertThat(service).isNotNull();
    }

    @Test
    void kivraControllerIsAvailable() {
        KivraController controller = context.getBean(KivraController.class);
        assertThat(controller).isNotNull();
    }
}
