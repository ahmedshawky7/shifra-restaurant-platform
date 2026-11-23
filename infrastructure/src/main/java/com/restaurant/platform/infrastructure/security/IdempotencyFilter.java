package com.restaurant.platform.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IdempotencyFilter extends OncePerRequestFilter {

    private static final long TTL_MS = Duration.ofMinutes(10).toMillis();

    private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        return !(path.equals("/api/public/orders")
                || path.matches("^/api/pos/branches/\\d+/(orders|close-bill)$")
                || path.matches("^/api/waiter/branches/\\d+/orders$"));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        evictExpired();

        String actorScope = resolveActorScope(request);
        String compositeKey = actorScope + "|" + request.getServletPath() + "|" + idempotencyKey.trim();

        CachedResponse existing = cache.putIfAbsent(compositeKey, CachedResponse.pending());
        if (existing != null) {
            if (existing.inFlight()) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"الطلب قيد المعالجة بالفعل، يرجى الانتظار.\"}");
                return;
            }
            response.setStatus(existing.status());
            if (existing.contentType() != null) {
                response.setContentType(existing.contentType());
            }
            response.getOutputStream().write(existing.body());
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrappedResponse);
            int status = wrappedResponse.getStatus();
            if (status >= 200 && status < 300) {
                cache.put(compositeKey, new CachedResponse(
                        false,
                        status,
                        wrappedResponse.getContentType(),
                        wrappedResponse.getContentAsByteArray(),
                        System.currentTimeMillis() + TTL_MS
                ));
            } else {
                cache.remove(compositeKey);
            }
            wrappedResponse.copyBodyToResponse();
        } catch (Throwable ex) {
            cache.remove(compositeKey);
            throw ex;
        }
    }

    private String resolveActorScope(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomUserDetails cud) {
            return "U:" + cud.getUserId();
        }
        String xff = request.getHeader("X-Forwarded-For");
        String ip = (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
        return "IP:" + ip;
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, CachedResponse> entry : cache.entrySet()) {
            if (!entry.getValue().inFlight() && entry.getValue().expiresAt() <= now) {
                cache.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private record CachedResponse(boolean inFlight, int status, String contentType, byte[] body, long expiresAt) {
        static CachedResponse pending() {
            return new CachedResponse(true, 0, null, new byte[0], Long.MAX_VALUE);
        }
    }
}