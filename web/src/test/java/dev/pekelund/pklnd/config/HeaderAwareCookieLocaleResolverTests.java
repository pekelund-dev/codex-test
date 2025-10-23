package dev.pekelund.pklnd.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletRequest;

class HeaderAwareCookieLocaleResolverTests {

    private HeaderAwareCookieLocaleResolver localeResolver;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        localeResolver =
            new HeaderAwareCookieLocaleResolver(
                "pklnd-lang",
                List.of(Locale.forLanguageTag("sv"), Locale.ENGLISH),
                Locale.forLanguageTag("sv"));
    }

    @Test
    void defaultsToSwedishWhenBrowserPrefersEnglish() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en-US,en;q=0.9");

        Locale resolved = localeResolver.resolveLocale(request);

        assertThat(resolved).isEqualTo(Locale.forLanguageTag("sv"));
    }

    @Test
    void honoursSwedishFromAcceptLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "sv-SE,sv;q=0.9,en;q=0.8");

        Locale resolved = localeResolver.resolveLocale(request);

        assertThat(resolved).isEqualTo(Locale.forLanguageTag("sv"));
    }

    @Test
    void usesCookieLocaleWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("pklnd-lang", "en"));

        Locale resolved = localeResolver.resolveLocale(request);

        assertThat(resolved).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void usesQueryParameterWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(HeaderAwareCookieLocaleResolver.LANGUAGE_PARAMETER_NAME, "en");

        Locale resolved = localeResolver.resolveLocale(request);

        assertThat(resolved).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void ignoresInvalidCookieValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("pklnd-lang", "zz"));

        Locale resolved = localeResolver.resolveLocale(request);

        assertThat(resolved).isEqualTo(Locale.forLanguageTag("sv"));
    }

    @Test
    void fallsBackToSwedishWhenHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        Locale resolved = localeResolver.resolveLocale(request);

        assertThat(resolved).isEqualTo(Locale.forLanguageTag("sv"));
    }
}
