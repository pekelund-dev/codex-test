package dev.pekelund.responsiveauth.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

/**
 * LocaleResolver that persists the chosen locale in a cookie while still honouring the
 * {@code Accept-Language} header the first time a visitor arrives.
 */
public class HeaderAwareCookieLocaleResolver extends CookieLocaleResolver {

    private final List<Locale> supportedLocales;
    private final Locale fallbackLocale;

    public HeaderAwareCookieLocaleResolver(List<Locale> supportedLocales, Locale fallbackLocale) {
        this.supportedLocales = List.copyOf(Objects.requireNonNull(supportedLocales));
        this.fallbackLocale = Objects.requireNonNull(fallbackLocale);
        setDefaultLocale(fallbackLocale);
        setLanguageTagCompliant(true);
    }

    @Override
    protected Locale determineDefaultLocale(HttpServletRequest request) {
        if (request == null) {
            return fallbackLocale;
        }

        String header = request.getHeader("Accept-Language");
        if (header == null || header.isBlank()) {
            return fallbackLocale;
        }

        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
            Locale matched = Locale.lookup(ranges, supportedLocales);
            return matched != null ? matched : fallbackLocale;
        } catch (IllegalArgumentException ex) {
            return fallbackLocale;
        }
    }
}
