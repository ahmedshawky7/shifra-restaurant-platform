package com.restaurant.platform.infrastructure.security;

import com.restaurant.platform.application.port.out.BranchStatusPort;
import com.restaurant.platform.domain.model.enums.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("tenantGuard")
public class TenantAccessGuard {

    private final BranchStatusPort branchStatusPort;

    public TenantAccessGuard(BranchStatusPort branchStatusPort) {
        this.branchStatusPort = branchStatusPort;
    }

    public boolean canAccessBranch(Long branchId) {
        try {
            assertCurrentUserCanAccessBranch(branchId);
            return true;
        } catch (AccessDeniedException ex) {
            return false;
        }
    }

    public void assertCurrentUserCanAccessBranch(Long branchId) {
        if (branchId == null) return;
        CustomUserDetails cud = requireAuthenticatedUser();
        if (cud.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }

        Long branchOrgId;
        try {
            branchOrgId = branchStatusPort.getOrganizationIdByBranchId(branchId);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("الفرع غير موجود أو غير مصرح بالوصول إليه");
        }

        if (cud.getOrganizationId() == null || !cud.getOrganizationId().equals(branchOrgId)) {
            throw new AccessDeniedException("غير مصرح بالوصول إلى بيانات فرع لمنشأة أخرى");
        }

        if (cud.getRole() != UserRole.ORG_ADMIN && !cud.isOwner()
                && cud.getBranchId() != null && !cud.getBranchId().equals(branchId)) {
            throw new AccessDeniedException("غير مصرح بالوصول إلى بيانات فرع آخر");
        }
    }

    public void assertCurrentUserCanAccessOrganization(Long organizationId) {
        if (organizationId == null) return;
        CustomUserDetails cud = requireAuthenticatedUser();
        if (cud.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }
        if (cud.getOrganizationId() == null || !cud.getOrganizationId().equals(organizationId)) {
            throw new AccessDeniedException("غير مصرح بالوصول إلى بيانات منشأة أخرى");
        }
    }

    private CustomUserDetails requireAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
            throw new AccessDeniedException("يجب تسجيل الدخول للوصول إلى هذا المورد");
        }
        return cud;
    }
}