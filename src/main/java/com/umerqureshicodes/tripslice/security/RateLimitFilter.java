package com.umerqureshicodes.tripslice.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.umerqureshicodes.tripslice.JWT.JwtUtil;
import com.umerqureshicodes.tripslice.users.AppUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory fixed window rate limits. Two instances sit in the security chain: the per-IP one runs
 * first (before login is handled), the per-user one runs after the JWT is validated. Counters live
 * in this process only, which is fine for a single Railway replica.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String name, String method, String path, int limit, Duration window, boolean perUser) {
        boolean matches(HttpServletRequest request) {
            return (method == null || method.equalsIgnoreCase(request.getMethod()))
                    && (path == null || path.equals(JwtUtil.pathOf(request)));
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("login", "POST", "/generate-token", 10, Duration.ofMinutes(5), false),
            new Rule("register", "POST", "/register", 5, Duration.ofHours(1), false),
            new Rule("refresh", "POST", "/refresh-token", 60, Duration.ofMinutes(5), false),
            new Rule("upload", "POST", "/videos", 10, Duration.ofHours(1), true),
            new Rule("montage", "POST", "/montages", 5, Duration.ofMinutes(10), true),
            new Rule("search", "GET", "/search", 30, Duration.ofMinutes(1), true),
            // Catch-all so nothing can be hammered
            new Rule("global", null, null, 300, Duration.ofMinutes(1), false)
    );

    // An entry expires one window after it is created, which gives a fixed window per key
    private final Cache<String, AtomicInteger> counters;
    private final String clientIpHeader;
    private final boolean perUser;

    public RateLimitFilter(String clientIpHeader, boolean perUser) {
        this.perUser = perUser;
        this.clientIpHeader = clientIpHeader == null ? "" : clientIpHeader.trim();
        this.counters = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfter(new com.github.benmanes.caffeine.cache.Expiry<String, AtomicInteger>() {
                    public long expireAfterCreate(String key, AtomicInteger value, long currentTime) {
                        return TimeUnit.SECONDS.toNanos(windowSeconds(key));
                    }
                    public long expireAfterUpdate(String key, AtomicInteger value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                    public long expireAfterRead(String key, AtomicInteger value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    // Both instances are the same class, without this the second would think it had already run and skip
    @Override
    protected String getAlreadyFilteredAttributeName() {
        return RateLimitFilter.class.getName() + (perUser ? ".USER" : ".IP") + ".FILTERED";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // CORS preflights carry no credentials and do no work
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || JwtUtil.pathOf(request).startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        for (Rule rule : RULES) {
            if (rule.perUser() != perUser || !rule.matches(request)) {
                continue;
            }
            String subject = perUser ? userKey() : "ip:" + clientIp(request);
            if (subject == null) {
                continue; // not logged in, the request is rejected further down the chain
            }
            String key = rule.name() + "|" + rule.window().toSeconds() + "|" + subject;
            int count = counters.get(key, k -> new AtomicInteger()).incrementAndGet();
            if (count > rule.limit()) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(rule.window().toSeconds()));
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests. Please slow down and try again shortly.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static long windowSeconds(String key) {
        String[] parts = key.split("\\|", 3);
        return Long.parseLong(parts[1]);
    }

    private static String userKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUser user) {
            return "user:" + user.getId();
        }
        return null;
    }

    /**
     * Behind a proxy every request comes from the proxy's address. Set CLIENT_IP_HEADER to the
     * header the platform overwrites with the real client IP (on Railway, X-Real-IP). Never use a
     * header the client can set freely, or the IP limits can be dodged by sending a fake one.
     */
    private String clientIp(HttpServletRequest request) {
        if (!clientIpHeader.isEmpty()) {
            String value = request.getHeader(clientIpHeader);
            if (value != null && !value.isBlank()) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
