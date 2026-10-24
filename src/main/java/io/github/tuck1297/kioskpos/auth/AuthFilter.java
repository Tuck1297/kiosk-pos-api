package io.github.tuck1297.kioskpos.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs before any controller on every /v1 request:
 *
 * <ol>
 *   <li>CSRF: state-changing requests must carry {@code X-Kiosk-Register: 1}. Browsers
 *       won't let another site's page set a custom header without a CORS preflight, which
 *       this API never approves, so a forged cross-site request can't get past here.</li>
 *   <li>Authentication: everything except /v1/health and /v1/auth/login needs a valid,
 *       unexpired {@code kiosk_session} cookie.</li>
 * </ol>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthFilter extends OncePerRequestFilter {

    public static final String COOKIE = "kiosk_session";
    public static final String CSRF_HEADER = "X-Kiosk-Register";
    public static final String CLAIMS_ATTRIBUTE = AuthFilter.class.getName() + ".claims";

    private static final Set<String> PUBLIC = Set.of("/v1/health", "/v1/auth/login");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final SessionToken tokens;

    public AuthFilter(SessionToken tokens) {
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!SAFE_METHODS.contains(request.getMethod()) && !"1".equals(request.getHeader(CSRF_HEADER))) {
            problem(response, 403, "Forbidden", "CSRF_HEADER_MISSING",
                    "Requests that change data must send the header X-Kiosk-Register: 1.");
            return;
        }
        if (PUBLIC.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        Optional<SessionToken.Claims> claims = sessionCookie(request).flatMap(tokens::verify);
        if (claims.isEmpty()) {
            problem(response, 401, "Sign in required", "UNAUTHORIZED",
                    "Sign in at the register to continue.");
            return;
        }
        request.setAttribute(CLAIMS_ATTRIBUTE, claims.get());
        chain.doFilter(request, response);
    }

    private static Optional<String> sessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName())) {
                return Optional.ofNullable(c.getValue());
            }
        }
        return Optional.empty();
    }

    /** Filters run before Spring MVC's error handling, so the problem+json is written here. */
    private static void problem(HttpServletResponse response, int status, String title, String code, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String type = "https://kiosk-pos.example.com/problems/" + code.toLowerCase().replace('_', '-');
        response.getWriter().write("{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
    }
}
