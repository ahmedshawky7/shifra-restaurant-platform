package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.application.command.UpdateOrderStatusCommand;
import com.restaurant.platform.application.port.in.GetKdsOrdersUseCase;
import com.restaurant.platform.application.port.in.ToggleMenuItemAvailabilityUseCase;
import com.restaurant.platform.application.port.in.UpdateOrderStatusUseCase;
import com.restaurant.platform.application.port.out.MenuRepositoryPort;
import com.restaurant.platform.application.result.OrderResult;
import com.restaurant.platform.domain.model.enums.OrderStatus;
import com.restaurant.platform.infrastructure.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kds")
public class KdsController {

    private final GetKdsOrdersUseCase getKdsOrdersUseCase;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final ToggleMenuItemAvailabilityUseCase toggleAvailabilityUseCase;
    private final MenuRepositoryPort menuRepo;
    private final TenantGuard tenantGuard;

    public KdsController(GetKdsOrdersUseCase getKdsOrdersUseCase,
                         UpdateOrderStatusUseCase updateOrderStatusUseCase,
                         ToggleMenuItemAvailabilityUseCase toggleAvailabilityUseCase,
                         MenuRepositoryPort menuRepo,
                         TenantGuard tenantGuard) {
        this.getKdsOrdersUseCase = getKdsOrdersUseCase;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.toggleAvailabilityUseCase = toggleAvailabilityUseCase;
        this.menuRepo = menuRepo;
        this.tenantGuard = tenantGuard;
    }

    @GetMapping("/branches/{branchId}/orders")
    public List<OrderResult> activeOrders(@AuthenticationPrincipal CustomUserDetails principal,
                                          @PathVariable Long branchId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return getKdsOrdersUseCase.getActiveKdsOrders(branchId);
    }

    @PostMapping("/branches/{branchId}/orders/{orderId}/start")
    public OrderResult startPreparing(@AuthenticationPrincipal CustomUserDetails principal,
                                      @PathVariable Long branchId,
                                      @PathVariable Long orderId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return updateOrderStatusUseCase.updateStatus(
                new UpdateOrderStatusCommand(orderId, branchId, OrderStatus.PREPARING, principal.getUserId()));
    }

    @PostMapping("/branches/{branchId}/orders/{orderId}/ready")
    public OrderResult markReady(@AuthenticationPrincipal CustomUserDetails principal,
                                 @PathVariable Long branchId,
                                 @PathVariable Long orderId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return updateOrderStatusUseCase.updateStatus(
                new UpdateOrderStatusCommand(orderId, branchId, OrderStatus.READY, principal.getUserId()));
    }

    @GetMapping("/branches/{branchId}/menu-items")
    public List<Map<String, Object>> menuAvailability(@AuthenticationPrincipal CustomUserDetails principal,
                                                      @PathVariable Long branchId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return menuRepo.findActiveItemsByOrg(principal.getOrganizationId()).stream()
                .map(i -> Map.<String, Object>of(
                        "id", i.id(),
                        "name", i.name(),
                        "available", i.available() && menuRepo.isItemAvailableInBranch(branchId, i.id())
                )).toList();
    }

    @PutMapping("/branches/{branchId}/items/{menuItemId}/availability")
    public ResponseEntity<Void> toggleItem(@AuthenticationPrincipal CustomUserDetails principal,
                                           @PathVariable Long branchId,
                                           @PathVariable Long menuItemId,
                                           @RequestBody Map<String, Boolean> body) {
        tenantGuard.requireBranchAccess(principal, branchId);
        boolean available = Boolean.TRUE.equals(body.get("available"));
        toggleAvailabilityUseCase.setAvailability(principal.getOrganizationId(), branchId, menuItemId, available);
        return ResponseEntity.ok().build();
    }
}