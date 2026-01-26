package dev.pekelund.pklnd.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.HeaderWriterFilter;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
        GrantedAuthoritiesMapper oauthAuthoritiesMapper
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
            .headers(headers -> headers
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(true)
                    .maxAgeInSeconds(31536000))
            )
            .addFilterBefore(new CspNonceFilter(), HeaderWriterFilter.class)
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
            log.info("OAuth2 login enabled - configuring Google authentication");
            OAuth2AuthorizationRequestResolver authorizationRequestResolver = createAuthorizationRequestResolver(
                clientRegistrationRepository
            );
            http.oauth2Login(oauth -> oauth
                .loginPage("/login")
                .authorizationEndpoint(authorization -> authorization.authorizationRequestResolver(authorizationRequestResolver))
                .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(oauthAuthoritiesMapper))
                .successHandler((request, response, authentication) -> {
                    log.info("OAuth2 login SUCCESS - User: {}, Principal type: {}",
                        authentication.getName(),
                        authentication.getPrincipal().getClass().getSimpleName());
                    response.sendRedirect("/receipts");
                })
                .failureHandler((request, response, exception) -> {
                    log.error("OAuth2 login FAILED - Error: {}", exception.getMessage(), exception);
                    response.sendRedirect("/login?error");
                }));
        } else {
            log.info("OAuth2 login disabled - no client registrations configured. Set the 'oauth' profile and provide Google client credentials to enable it.");
        }

        return http.build();
    }

    private OAuth2AuthorizationRequestResolver createAuthorizationRequestResolver(
        ClientRegistrationRepository clientRegistrationRepository
    ) {
        DefaultOAuth2AuthorizationRequestResolver defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository,
            OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI
        );

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                log.debug("Resolving OAuth2 authorization request from: {}", request.getRequestURI());
                OAuth2AuthorizationRequest authorizationRequest = defaultResolver.resolve(request);
                return customizeAuthorizationRequest(authorizationRequest);
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                log.info("Resolving OAuth2 authorization request for client: {}", clientRegistrationId);
                OAuth2AuthorizationRequest authorizationRequest = defaultResolver.resolve(request, clientRegistrationId);
                return customizeAuthorizationRequest(authorizationRequest);
            }

            private OAuth2AuthorizationRequest customizeAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest) {
                if (authorizationRequest == null) {
                    log.debug("Authorization request is null, skipping customization");
                    return null;
                }

                log.debug("Customizing OAuth2 authorization request - redirect URI: {}",
                    authorizationRequest.getRedirectUri());

                Map<String, Object> additionalParameters = new HashMap<>(authorizationRequest.getAdditionalParameters());
                additionalParameters.put("prompt", "select_account");

                return OAuth2AuthorizationRequest
                    .from(authorizationRequest)
                    .additionalParameters(additionalParameters)
                    .build();
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
