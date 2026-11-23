package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.infrastructure.adapter.out.persistence.AuditLogEntity;
import com.restaurant.platform.infrastructure.adapter.out.persistence.AuditLogJpaRepository;
import com.restaurant.platform.infrastructure.security.TenantAccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics/branches/{branchId}/audit-logs")
public class AuditLogController {

    private final AuditLogJpaRepository auditLogRepo;
    private final TenantAccessGuard tenantGuard;

    public AuditLogController(AuditLogJpaRepository auditLogRepo, TenantAccessGuard tenantGuard) {
        this.auditLogRepo = auditLogRepo;
        this.tenantGuard = tenantGuard;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORG_ADMIN','CASHIER')")
    public Page<AuditLogEntity> getBranchAuditLogs(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        tenantGuard.assertCurrentUserCanAccessBranch(branchId);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return auditLogRepo.findByBranchIdOrderByCreatedAtDesc(branchId, pageable);
    }
}