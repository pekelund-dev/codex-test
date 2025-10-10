package dev.pekelund.responsiveauth.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

/**
 * LocaleResolver that persists the chosen locale in a cookie while still honouring the
 * {@code Accept-Language} header the first time a visitor arrives.
 */
public class HeaderAwareCookieLocaleResolver extends CookieLocaleResolver {

    public static final String LANGUAGE_PARAMETER_NAME = "lang";

    private final Locale fallbackLocale;
    private final List<Locale> supportedLocales;
    private String cookieName = DEFAULT_COOKIE_NAME;

    public HeaderAwareCookieLocaleResolver(List<Locale> supportedLocales, Locale fallbackLocale) {
        this.supportedLocales = List.copyOf(Objects.requireNonNull(supportedLocales));
        if (!this.supportedLocales.contains(Objects.requireNonNull(fallbackLocale))) {
            throw new IllegalArgumentException("Fallback locale must be included in supported locales");
        }
        this.fallbackLocale = Objects.requireNonNull(fallbackLocale);
        setDefaultLocale(fallbackLocale);
        setLanguageTagCompliant(true);
    }

    @Override
    public void setCookieName(String cookieName) {
        super.setCookieName(cookieName);
        this.cookieName = cookieName;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        if (request == null) {
            return fallbackLocale;
        }

        Locale requestedLocale = (Locale) request.getAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME);
        if (requestedLocale != null) {
            Locale normalised = normaliseSupportedLocale(requestedLocale);
            if (normalised != null) {
                return normalised;
            }
        }

        Locale parameterLocale = resolveFromParameter(request.getParameter(LANGUAGE_PARAMETER_NAME));
        if (parameterLocale != null) {
            request.setAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME, parameterLocale);
            return parameterLocale;
        }

        Locale cookieLocale = extractLocaleFromCookie(request);
        if (cookieLocale != null) {
            return cookieLocale;
        }

        Locale headerLocale = resolveFromAcceptLanguage(request.getHeader("Accept-Language"));
        if (headerLocale != null) {
            return headerLocale;
        }

        return fallbackLocale;
    }

    private Locale extractLocaleFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                try {
                    Locale parsed = parseLocaleValue(cookie.getValue());
                    return normaliseSupportedLocale(parsed);
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    private Locale resolveFromAcceptLanguage(String header) {
        if (!StringUtils.hasText(header)) {
            return null;
        }

        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
            Locale matched = Locale.lookup(ranges, supportedLocales);
            if (matched != null && matched.equals(fallbackLocale)) {
                return fallbackLocale;
            }
        } catch (IllegalArgumentException ignored) {
            // ignore invalid header values and fall back to Swedish
        }
        return null;
    }

    private Locale resolveFromParameter(String parameterValue) {
        if (!StringUtils.hasText(parameterValue)) {
            return null;
        }
        try {
            Locale parsed = parseLocaleValue(parameterValue);
            return normaliseSupportedLocale(parsed);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Locale normaliseSupportedLocale(Locale locale) {
        if (locale == null) {
            return null;
        }
        for (Locale supported : supportedLocales) {
            if (supported.getLanguage().equalsIgnoreCase(locale.getLanguage())) {
                return supported;
            }
        }
        return null;
    }
}
