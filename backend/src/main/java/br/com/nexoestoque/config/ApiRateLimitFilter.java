package br.com.nexoestoque.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory guard for the single-instance API.
 * Auth endpoints keep their stricter dedicated limits in AuthSessionService.
 */
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);
    private static final int MAX_ENTRIES = 10_000;
    private static final long WINDOW_SECONDS = 60;

    private final int readLimit;
    private final int writeLimit;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public ApiRateLimitFilter() {
        this(300, 120, Clock.systemUTC());
    }

    ApiRateLimitFilter(int readLimit, int writeLimit, Clock clock) {
        if (readLimit < 1 || writeLimit < 1) throw new IllegalArgumentException("Rate limits must be positive");
        this.readLimit = readLimit;
        this.writeLimit = writeLimit;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) return true;
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/demo")
                || path.equals("/api/v1/system/health")
                || path.equals("/api/v1/system/readiness");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        boolean read = "GET".equalsIgnoreCase(request.getMethod())
                || "HEAD".equalsIgnoreCase(request.getMethod());
        int limit = read ? readLimit : writeLimit;
        String key = (read ? "r:" : "w:") + clientKey(request);

        if (!allow(key, limit)) {
            log.warn("security_event=api_rate_limited client={} method_group={}", shortHash(key), read ? "read" : "write");
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Retry-After", Long.toString(WINDOW_SECONDS));
            response.getWriter().write("{\"status\":429,\"code\":\"RATE_LIMITED\",\"message\":\"Muitas requisições. Tente novamente em instantes.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean allow(String key, int limit) {
        Instant now = clock.instant();
        synchronized (windows) {
            windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));

            Window current = windows.get(key);
            if (current == null) {
                if (windows.size() >= MAX_ENTRIES) return false;
                windows.put(key, new Window(1, now.plusSeconds(WINDOW_SECONDS)));
                return true;
            }

            if (current.count() >= limit) return false;
            windows.put(key, new Window(current.count() + 1, current.expiresAt()));
            return true;
        }
    }

    private String clientKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            if (token.matches("[A-Za-z0-9_-]{43}")) return "token:" + digest(token);
        }
        return "addr:" + digest(String.valueOf(request.getRemoteAddr()));
    }

    private static String shortHash(String value) {
        String hash = digest(value);
        return hash.substring(0, Math.min(12, hash.length()));
    }

    private static String digest(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record Window(int count, Instant expiresAt) {}
}
