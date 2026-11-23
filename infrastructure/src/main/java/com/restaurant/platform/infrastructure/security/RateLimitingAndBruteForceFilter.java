package com.restaurant.platform.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RateLimitingAndBruteForceFilter extends OncePerRequestFilter {

    private static final int MAX_ORDERS_PER_TABLE_WINDOW = 3;
    private static final int MAX_ORDERS_PER_IP_CEILING = 25;
    private static final long PUBLIC_ORDER_WINDOW_MS = Duration.ofMinutes(5).toMillis();

    private static final int MAX_ALERTS_PER_TABLE_WINDOW = 5;
    private static final long PUBLIC_ALERT_WINDOW_MS = Duration.ofMinutes(5).toMillis();

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long LOGIN_BLOCK_DURATION_MS = Duration.ofMinutes(15).toMillis();

    private static final int MAX_PUBLIC_BODY_BYTES = 128 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean trustProxyHeaders;
    private final Set<String> trustedProxies;

    private final ConcurrentHashMap<String, Deque<Long>> orderTableWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> orderIpWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> alertWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LoginAttemptState> loginAttempts = new ConcurrentHashMap<>();

    public RateLimitingAndBruteForceFilter() {
        this(false, List.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1"));
    }

    public RateLimitingAndBruteForceFilter(boolean trustProxyHeaders, List<String> trustedProxies) {
        this.trustProxyHeaders = trustProxyHeaders;
        this.trustedProxies = trustedProxies == null
                ? Set.of()
                : trustedProxies.stream().map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getServletPath();
        String method = request.getMethod();
        String clientIp = extractTrustedClientIp(request);

        if ("POST".equalsIgnoreCase(method) && "/api/public/orders".equals(path)) {
            byte[] body = request.getInputStream().readNBytes(MAX_PUBLIC_BODY_BYTES + 1);
            if (body.length > MAX_PUBLIC_BODY_BYTES) {
                response.setStatus(413);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"حجم الطلب أكبر من الحد المسموح.\"}");
                return;
            }

            String tableToken = extractTableOrBranchKey(body);
            String perTableKey = clientIp + ":" + tableToken;

            if (!tryAcquire(orderTableWindows, perTableKey, MAX_ORDERS_PER_TABLE_WINDOW, PUBLIC_ORDER_WINDOW_MS)
                    || !tryAcquire(orderIpWindows, clientIp, MAX_ORDERS_PER_IP_CEILING, PUBLIC_ORDER_WINDOW_MS)) {
                writeTooManyRequests(response, "تم تجاوز الحد المسموح لإرسال الطلبات مؤقتاً. يرجى الانتظار قليلاً.");
                return;
            }

            filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
            return;
        }

        if ("POST".equalsIgnoreCase(method) && "/api/public/tables/alert".equals(path)) {
            byte[] body = request.getInputStream().readNBytes(MAX_PUBLIC_BODY_BYTES + 1);
            if (body.length > MAX_PUBLIC_BODY_BYTES) {
                response.setStatus(413);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"حجم الطلب أكبر من الحد المسموح.\"}");
                return;
            }

            String tableToken = extractTableOrBranchKey(body);
            String perTableKey = clientIp + ":" + tableToken;
            if (!tryAcquire(alertWindows, perTableKey, MAX_ALERTS_PER_TABLE_WINDOW, PUBLIC_ALERT_WINDOW_MS)) {
                writeTooManyRequests(response, "تم إرسال عدة تنبيهات متتالية، يرجى الانتظار قليلاً.");
                return;
            }

            filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
            return;
        }

        if ("POST".equalsIgnoreCase(method) && "/api/auth/login".equals(path)) {
            LoginAttemptState state = loginAttempts.get(clientIp);
            if (state != null && state.isBlocked(System.currentTimeMillis())) {
                writeTooManyRequests(response, "تم حظر تسجيل الدخول مؤقتاً لمدة 15 دقيقة بسبب تكرار المحاولات الفاشلة.");
                return;
            }

            filterChain.doFilter(request, response);

            int status = response.getStatus();
            if (status == HttpServletResponse.SC_OK) {
                loginAttempts.remove(clientIp);
            } else if (status == HttpServletResponse.SC_UNAUTHORIZED || status == HttpServletResponse.SC_FORBIDDEN) {
                loginAttempts.compute(clientIp, (k, existing) -> {
                    LoginAttemptState s = existing != null ? existing : new LoginAttemptState();
                    s.recordFailure(System.currentTimeMillis());
                    return s;
                });
            }
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractTableOrBranchKey(byte[] body) {
        if (body == null || body.length == 0) {
            return "ANON";
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            if (node.hasNonNull("tableQrToken")) {
                String token = node.get("tableQrToken").asText("").trim();
                if (!token.isEmpty()) return "QR_" + token;
            }
            if (node.hasNonNull("branchId")) {
                return "BR_" + node.get("branchId").asText("0").trim();
            }
        } catch (Exception ignored) {
        }
        return "ANON";
    }

    private boolean tryAcquire(ConcurrentHashMap<String, Deque<Long>> store, String key, int maxHits, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = store.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) >= windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxHits) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private String extractTrustedClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustProxyHeaders && isTrustedProxy(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String candidate = xff.split(",")[0].trim();
                if (!candidate.isEmpty()) return candidate;
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) return false;
        if (trustedProxies.contains(remoteAddr)) return true;
        for (String prefix : trustedProxies) {
            if (prefix.endsWith("*") && remoteAddr.startsWith(prefix.substring(0, prefix.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    private void writeTooManyRequests(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private static final class LoginAttemptState {
        private int failures = 0;
        private long blockedUntil = 0;

        synchronized boolean isBlocked(long now) {
            if (blockedUntil > now) {
                return true;
            }
            if (blockedUntil > 0 && blockedUntil <= now) {
                failures = 0;
                blockedUntil = 0;
            }
            return false;
        }

        synchronized void recordFailure(long now) {
            if (blockedUntil > 0 && blockedUntil <= now) {
                failures = 0;
                blockedUntil = 0;
            }
            failures++;
            if (failures >= MAX_LOGIN_FAILURES) {
                blockedUntil = now + LOGIN_BLOCK_DURATION_MS;
            }
        }
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = cachedBody != null ? cachedBody : new byte[0];
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return bais.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name();
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        }
    }
}