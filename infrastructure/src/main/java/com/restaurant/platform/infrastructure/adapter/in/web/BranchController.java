package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.application.port.out.BranchStatusPort;
import com.restaurant.platform.infrastructure.adapter.out.persistence.BranchEntity;
import com.restaurant.platform.infrastructure.adapter.out.persistence.BranchJpaRepository;
import com.restaurant.platform.infrastructure.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchJpaRepository branchRepo;
    private final BranchStatusPort branchStatusPort;
    private final TenantGuard tenantGuard;

    public BranchController(BranchJpaRepository branchRepo,
                            BranchStatusPort branchStatusPort,
                            TenantGuard tenantGuard) {
        this.branchRepo = branchRepo;
        this.branchStatusPort = branchStatusPort;
        this.tenantGuard = tenantGuard;
    }

    @GetMapping("/my")
    public List<BranchEntity> myBranches(@AuthenticationPrincipal CustomUserDetails principal) {
        List<BranchEntity> all = branchRepo.findByOrganizationId(principal.getOrganizationId());
        if (!principal.isOwner() && principal.getBranchId() != null) {
            return all.stream().filter(b -> b.getId().equals(principal.getBranchId())).toList();
        }
        return all;
    }

    @GetMapping("/{branchId}")
    public Map<String, Object> getBranch(@AuthenticationPrincipal CustomUserDetails principal,
                                         @PathVariable Long branchId) {
        BranchEntity b = tenantGuard.requireBranchAccess(principal, branchId);
        return Map.of(
                "branch", b,
                "isOpenNow", branchStatusPort.isBranchOpen(branchId) && branchStatusPort.isWithinWorkingHours(branchId)
        );
    }

    @PutMapping("/{branchId}/status")
    public BranchEntity toggleStatus(@AuthenticationPrincipal CustomUserDetails principal,
                                     @PathVariable Long branchId,
                                     @RequestBody Map<String, Object> body) {
        BranchEntity b = tenantGuard.requireBranchAccess(principal, branchId);
        boolean open = Boolean.TRUE.equals(body.get("isOpen"));
        b.setOpenToday(open);
        b.setClosureReason(open ? null : (String) body.get("reason"));
        return branchRepo.save(b);
    }

    @PutMapping("/{branchId}/schedule")
    public BranchEntity updateSchedule(@AuthenticationPrincipal CustomUserDetails principal,
                                       @PathVariable Long branchId,
                                       @RequestBody Map<String, Object> body) {
        BranchEntity b = tenantGuard.requireBranchAccess(principal, branchId);
        if (body.get("openTime") != null) b.setOpenTime(body.get("openTime").toString());
        if (body.get("closeTime") != null) b.setCloseTime(body.get("closeTime").toString());
        if (body.get("workingDays") != null) b.setWorkingDays(body.get("workingDays").toString());
        if (body.get("name") != null) b.setName(body.get("name").toString());
        if (body.get("address") != null) b.setAddress(body.get("address").toString());
        if (body.get("city") != null) b.setCity(body.get("city").toString());
        if (body.get("taxRate") != null) b.setTaxRate(new BigDecimal(body.get("taxRate").toString()));
        if (body.get("serviceChargeRate") != null) {
            b.setServiceChargeRate(new BigDecimal(body.get("serviceChargeRate").toString()));
        }
        return branchRepo.save(b);
    }
}