package com.example.responsiveauth.config;

import com.example.responsiveauth.firebase.FirebaseAuthService;
import com.example.responsiveauth.firebase.FirebaseAuthenticationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final FirebaseAuthService firebaseAuthService;
    private final FirebaseAuthenticationProvider firebaseAuthenticationProvider;

    public SecurityConfig(FirebaseAuthService firebaseAuthService,
                          FirebaseAuthenticationProvider firebaseAuthenticationProvider) {
        this.firebaseAuthService = firebaseAuthService;
        this.firebaseAuthenticationProvider = firebaseAuthenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
            .oauth2Login(oauth -> oauth
                .loginPage("/login")
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .csrf(Customizer.withDefaults());

        if (firebaseAuthService.isEnabled()) {
            http.authenticationProvider(firebaseAuthenticationProvider);
        }

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(value = "firebase.enabled", havingValue = "false", matchIfMissing = true)
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails user = User.builder()
            .username("jane")
            .password(passwordEncoder.encode("password"))
            .roles("USER")
            .build();
        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder.encode("password"))
            .roles("USER", "ADMIN")
            .build();
        return new InMemoryUserDetailsManager(user, admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
