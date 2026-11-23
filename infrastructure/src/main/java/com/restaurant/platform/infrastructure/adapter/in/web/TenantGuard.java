package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.infrastructure.adapter.out.persistence.BranchEntity;
import com.restaurant.platform.infrastructure.adapter.out.persistence.BranchJpaRepository;
import com.restaurant.platform.infrastructure.security.CustomUserDetails;
import org.springframework.stereotype.Component;

@Component("legacyTenantGuard")
public class TenantGuard {

    private final BranchJpaRepository branchRepo;

    public TenantGuard(BranchJpaRepository branchRepo) {
        this.branchRepo = branchRepo;
    }

    public BranchEntity requireBranchAccess(CustomUserDetails user, Long branchId) {
        BranchEntity branch = branchRepo.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("الفرع غير موجود"));
        if (user.getOrganizationId() == null || !branch.getOrganizationId().equals(user.getOrganizationId())) {
            throw new SecurityException("غير مصرح لك بالوصول إلى بيانات هذا الفرع");
        }
        return branch;
    }
}
