package com.ifoto.ifoto_backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private record PathLimit(String method, String pattern, Bandwidth bandwidth) {
    }

    // More-specific patterns (e.g. /rentals/*/pay) must come before broader ones
    // (/rentals).
    private static final List<PathLimit> PATH_LIMITS = List.of(
            // Public endpoints — keyed by client IP
            new PathLimit("POST", "/api/v1/auth/login",
                    Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofMinutes(15))
                            .build()),
            new PathLimit("POST", "/api/v1/auth/forgot-password",
                    Bandwidth.builder().capacity(3).refillIntervally(3, Duration.ofHours(1))
                            .build()),
            new PathLimit("POST", "/api/v1/register",
                    Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofHours(1))
                            .build()),
            new PathLimit("POST", "/api/v1/auth/refresh",
                    Bandwidth.builder().capacity(20).refillIntervally(20, Duration.ofMinutes(15))
                            .build()),
            // Authenticated endpoints — keyed by username (more specific pattern first)
            new PathLimit("POST", "/api/v1/rentals/*/pay",
                    Bandwidth.builder().capacity(6).refillIntervally(6, Duration.ofMinutes(15))
                            .build()),
            new PathLimit("POST", "/api/v1/rentals",
                    Bandwidth.builder().capacity(6).refillIntervally(6, Duration.ofMinutes(10))
                            .build()));

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(100_000)
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String matchedPattern = null;
        Bandwidth limit = null;

        for (PathLimit entry : PATH_LIMITS) {
            if (entry.method().equalsIgnoreCase(request.getMethod())
                    && PATH_MATCHER.match(entry.pattern(), path)) {
                matchedPattern = entry.pattern();
                limit = entry.bandwidth();
                break;
            }
        }

        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        String bucketKey = resolveKey(request, matchedPattern);
        Bandwidth finalLimit = limit;
        Bucket bucket = buckets.get(bucketKey,
                k -> Bucket.builder().addLimit(finalLimit).build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write("{\"message\":\"Too many requests. Please try again later.\"}");
    }

    private String resolveKey(HttpServletRequest request, String matchedPattern) {
        // Key refresh requests by cookie value — each session gets its own bucket,
        // avoiding shared-IP exhaustion while still limiting per token.
        if ("/api/v1/auth/refresh".equals(matchedPattern)) {
            jakarta.servlet.http.Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (jakarta.servlet.http.Cookie c : cookies) {
                    if ("refreshToken".equals(c.getName()) && c.getValue() != null) {
                        return "token:" + c.getValue() + ":" + matchedPattern;
                    }
                }
            }
            return "ip:" + extractClientIp(request) + ":" + matchedPattern;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);

        String subject = isAuthenticated
                ? "user:" + auth.getName()
                : "ip:" + extractClientIp(request);

        return subject + ":" + matchedPattern;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
