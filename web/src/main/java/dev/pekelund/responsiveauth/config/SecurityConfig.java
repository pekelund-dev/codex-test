package dev.pekelund.responsiveauth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider
    ) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/",
                    "/home",
                    "/about",
                    "/login",
                    "/register",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/webjars/**",
                    "/oauth2/**",
                    "/favicon.ico",
                    "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .csrf(Customizer.withDefaults());

        ClientRegistrationRepository clientRegistrationRepository =
            clientRegistrationRepositoryProvider.getIfAvailable();

        boolean oauthEnabled = false;
        if (clientRegistrationRepository != null) {
            if (clientRegistrationRepository instanceof Iterable<?>) {
                oauthEnabled = ((Iterable<?>) clientRegistrationRepository).iterator().hasNext();
            } else {
                oauthEnabled = true;
            }
        }

        if (oauthEnabled) {
            http.oauth2Login(oauth -> oauth.loginPage("/login"));
        } else {
            log.info("OAuth2 login disabled - no client registrations configured. Set the 'oauth' profile and provide Google client credentials to enable it.");
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
