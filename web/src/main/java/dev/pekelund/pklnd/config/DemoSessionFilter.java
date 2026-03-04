package dev.pekelund.pklnd.config;

import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.web.DemoSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that activates demo-mode authentication for sessions that contain a demo user ID.
 *
 * <p>It runs after {@code AnonymousAuthenticationFilter}: if the current security context holds an
 * anonymous (or absent) authentication token and the HTTP session carries a demo user ID, a
 * {@link DemoAuthentication} token is placed into the context so that downstream authorization
 * checks treat the request as authenticated.</p>
 *
 * <p>The token is never persisted back into the HTTP session; it is recreated from the session
 * attribute on every request.</p>
 */
public class DemoSessionFilter extends OncePerRequestFilter {

    private final DemoSessionService demoSessionService;

    public DemoSessionFilter(DemoSessionService demoSessionService) {
        this.demoSessionService = demoSessionService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (demoSessionService != null) {
            Authentication existing = SecurityContextHolder.getContext().getAuthentication();
            if (existing == null || !existing.isAuthenticated() || existing instanceof AnonymousAuthenticationToken) {
                HttpSession session = request.getSession(false);
                if (session != null && demoSessionService.isDemoSession(session)) {
                    ReceiptOwner demoOwner = demoSessionService.getDemoOwner(session);
                    if (demoOwner != null) {
                        SecurityContextHolder.getContext().setAuthentication(new DemoAuthentication(demoOwner));
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
