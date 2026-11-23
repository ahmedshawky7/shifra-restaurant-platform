package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.domain.model.enums.TableStatus;
import com.restaurant.platform.infrastructure.adapter.out.persistence.RestaurantTableEntity;
import com.restaurant.platform.infrastructure.adapter.out.persistence.RestaurantTableJpaRepository;
import com.restaurant.platform.infrastructure.adapter.out.persistence.TableSessionJpaRepository;
import com.restaurant.platform.infrastructure.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/org/tables")
public class TableManagementController {

    private final RestaurantTableJpaRepository tableRepo;
    private final TableSessionJpaRepository sessionRepo;
    private final TenantGuard tenantGuard;

    public TableManagementController(RestaurantTableJpaRepository tableRepo,
                                     TableSessionJpaRepository sessionRepo,
                                     TenantGuard tenantGuard) {
        this.tableRepo = tableRepo;
        this.sessionRepo = sessionRepo;
        this.tenantGuard = tenantGuard;
    }

    public record TableRequest(Long branchId, String tableNumber, Integer capacity, Boolean active) {}

    @GetMapping
    public List<RestaurantTableEntity> listByBranch(@AuthenticationPrincipal CustomUserDetails principal,
                                                    @RequestParam Long branchId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return tableRepo.findByBranchIdOrderByIdAsc(branchId);
    }

    @PostMapping
    public RestaurantTableEntity createTable(@AuthenticationPrincipal CustomUserDetails principal,
                                             @RequestBody TableRequest req) {
        tenantGuard.requireBranchAccess(principal, req.branchId());
        if (req.tableNumber() == null || req.tableNumber().isBlank()) {
            throw new IllegalArgumentException("رقم الطاولة مطلوب");
        }
        String trimmedNum = req.tableNumber().trim();
        if (tableRepo.existsByBranchIdAndTableNumber(req.branchId(), trimmedNum)) {
            throw new IllegalArgumentException("رقم الطاولة مسجل بالفعل في هذا الفرع");
        }
        RestaurantTableEntity t = new RestaurantTableEntity();
        t.setOrganizationId(principal.getOrganizationId());
        t.setBranchId(req.branchId());
        t.setTableNumber(trimmedNum);
        t.setQrToken(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        t.setCapacity(req.capacity() != null && req.capacity() > 0 ? req.capacity() : 4);
        t.setStatus(TableStatus.AVAILABLE);
        t.setActive(true);
        return tableRepo.save(t);
    }

    @PutMapping("/{id}")
    public RestaurantTableEntity updateTable(@AuthenticationPrincipal CustomUserDetails principal,
                                             @PathVariable Long id,
                                             @RequestBody TableRequest req) {
        RestaurantTableEntity t = tableRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("الطاولة غير موجودة"));
        if (!principal.getOrganizationId().equals(t.getOrganizationId())) throw new SecurityException("غير مصرح");

        if (req.tableNumber() != null && !req.tableNumber().isBlank()) {
            String newNum = req.tableNumber().trim();
            if (!newNum.equals(t.getTableNumber())
                    && tableRepo.existsByBranchIdAndTableNumber(t.getBranchId(), newNum)) {
                throw new IllegalArgumentException("يوجد طاولة أخرى بنفس الرقم في هذا الفرع");
            }
            t.setTableNumber(newNum);
        }
        if (req.capacity() != null && req.capacity() > 0) {
            t.setCapacity(req.capacity());
        }
        if (req.active() != null) {
            if (!req.active() && (t.getStatus() == TableStatus.OCCUPIED
                    || sessionRepo.findFirstByTableIdAndOpenTrueOrderByOpenedAtDesc(t.getId()).isPresent())) {
                throw new IllegalStateException("لا يمكن تعطيل طاولة مشغولة حالياً. أغلق فاتورتها أولاً.");
            }
            t.setActive(req.active());
        }
        return tableRepo.save(t);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTable(@AuthenticationPrincipal CustomUserDetails principal,
                                            @PathVariable Long id) {
        RestaurantTableEntity t = tableRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("الطاولة غير موجودة"));
        if (!principal.getOrganizationId().equals(t.getOrganizationId())) throw new SecurityException("غير مصرح");

        if (t.getStatus() == TableStatus.OCCUPIED
                || sessionRepo.findFirstByTableIdAndOpenTrueOrderByOpenedAtDesc(t.getId()).isPresent()) {
            throw new IllegalStateException("لا يمكن حذف طاولة مشغولة حالياً. أغلق فاتورتها أولاً.");
        }
        tableRepo.delete(t);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/regenerate-qr")
    public RestaurantTableEntity regenerateQr(@AuthenticationPrincipal CustomUserDetails principal,
                                              @PathVariable Long id) {
        RestaurantTableEntity t = tableRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("الطاولة غير موجودة"));
        if (!principal.getOrganizationId().equals(t.getOrganizationId())) throw new SecurityException("غير مصرح");
        t.setQrToken(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        return tableRepo.save(t);
    }
}