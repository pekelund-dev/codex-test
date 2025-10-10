package dev.pekelund.responsiveauth.config;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

@Configuration
public class LocalizationConfig implements WebMvcConfigurer {

    private static final String LANGUAGE_COOKIE_NAME = "pklnd-lang";
    private static final List<Locale> SUPPORTED_LOCALES =
        List.of(Locale.forLanguageTag("sv"), Locale.ENGLISH);

    @Bean
    public LocaleResolver localeResolver() {
        HeaderAwareCookieLocaleResolver localeResolver =
            new HeaderAwareCookieLocaleResolver(SUPPORTED_LOCALES, Locale.forLanguageTag("sv"));
        localeResolver.setCookieName(LANGUAGE_COOKIE_NAME);
        localeResolver.setCookieMaxAge((int) Duration.ofDays(365).getSeconds());
        localeResolver.setCookiePath("/");
        localeResolver.setCookieHttpOnly(true);
        return localeResolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(HeaderAwareCookieLocaleResolver.LANGUAGE_PARAMETER_NAME);
        interceptor.setHttpMethods("GET");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
