package com.restaurant.platform.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TenantIsolationInterceptor implements HandlerInterceptor {

    private static final Pattern BRANCH_PATH_PATTERN =
            Pattern.compile("^/api/(?:(?:pos|kds|waiter|analytics)/)?branches/(\\d+)(?:/.*)?$");

    private final TenantAccessGuard tenantGuard;

    public TenantIsolationInterceptor(TenantAccessGuard tenantGuard) {
        this.tenantGuard = tenantGuard;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String path = request.getServletPath();
        if (path.startsWith("/api/public/") || path.startsWith("/api/auth/") || path.startsWith("/api/super-admin/")) {
            return true;
        }

        Matcher m = BRANCH_PATH_PATTERN.matcher(path);
        if (m.matches()) {
            Long branchId = Long.parseLong(m.group(1));
            tenantGuard.assertCurrentUserCanAccessBranch(branchId);
        }

        String branchParam = request.getParameter("branchId");
        if (branchParam != null && !branchParam.isBlank()) {
            try {
                tenantGuard.assertCurrentUserCanAccessBranch(Long.parseLong(branchParam.trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        String orgParam = request.getParameter("organizationId");
        if (orgParam != null && !orgParam.isBlank()) {
            try {
                tenantGuard.assertCurrentUserCanAccessOrganization(Long.parseLong(orgParam.trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        return true;
    }
}