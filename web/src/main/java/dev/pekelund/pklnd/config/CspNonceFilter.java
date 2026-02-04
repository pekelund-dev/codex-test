package dev.pekelund.pklnd.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.web.filter.OncePerRequestFilter;

public class CspNonceFilter extends OncePerRequestFilter {

    private static final int NONCE_SIZE = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] nonceBytes = new byte[NONCE_SIZE];
        SECURE_RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getEncoder().encodeToString(nonceBytes);
        request.setAttribute("cspNonce", nonce);

        String policy = "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https:; "
            + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; "
            + "font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net data:; "
            + "img-src 'self' https: data:; "
            + "connect-src 'self' https://cdn.jsdelivr.net https:; "
            + "frame-src https:; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'; "
            + "frame-ancestors 'none'";

        response.setHeader("Content-Security-Policy", policy);

        filterChain.doFilter(request, response);
    }
}
