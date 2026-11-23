package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.application.command.ModifyPendingOrderCommand;
import com.restaurant.platform.application.command.PlaceOrderCommand;
import com.restaurant.platform.application.command.UpdateOrderStatusCommand;
import com.restaurant.platform.application.port.in.*;
import com.restaurant.platform.application.port.out.OrderRepositoryPort;
import com.restaurant.platform.application.port.out.TableRepositoryPort;
import com.restaurant.platform.application.result.OrderResult;
import com.restaurant.platform.application.result.PublicMenuResult;
import com.restaurant.platform.domain.model.enums.OrderSource;
import com.restaurant.platform.domain.model.enums.OrderStatus;
import com.restaurant.platform.domain.model.enums.OrderType;
import com.restaurant.platform.infrastructure.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/waiter")
public class WaiterController {

    private final TableRepositoryPort tableRepo;
    private final OrderRepositoryPort orderRepo;
    private final GetPublicMenuUseCase getPublicMenuUseCase;
    private final GetPosOrdersUseCase getPosOrdersUseCase;
    private final PlaceOrderUseCase placeOrderUseCase;
    private final ModifyPendingOrderUseCase modifyPendingOrderUseCase;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final TenantGuard tenantGuard;

    public WaiterController(TableRepositoryPort tableRepo,
                            OrderRepositoryPort orderRepo,
                            GetPublicMenuUseCase getPublicMenuUseCase,
                            GetPosOrdersUseCase getPosOrdersUseCase,
                            PlaceOrderUseCase placeOrderUseCase,
                            ModifyPendingOrderUseCase modifyPendingOrderUseCase,
                            UpdateOrderStatusUseCase updateOrderStatusUseCase,
                            TenantGuard tenantGuard) {
        this.tableRepo = tableRepo;
        this.orderRepo = orderRepo;
        this.getPublicMenuUseCase = getPublicMenuUseCase;
        this.getPosOrdersUseCase = getPosOrdersUseCase;
        this.placeOrderUseCase = placeOrderUseCase;
        this.modifyPendingOrderUseCase = modifyPendingOrderUseCase;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.tenantGuard = tenantGuard;
    }

    public record WaiterOrderRequest(Long tableId, String notes, List<PlaceOrderCommand.OrderItemRequest> items) {}

    @GetMapping("/branches/{branchId}/tables")
    public List<Map<String, Object>> tables(@AuthenticationPrincipal CustomUserDetails principal,
                                            @PathVariable Long branchId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return tableRepo.findByBranchId(branchId).stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getId(),
                        "tableNumber", t.getTableNumber(),
                        "capacity", t.getCapacity(),
                        "status", t.getStatus().name(),
                        "active", t.isActive()
                )).toList();
    }

    @GetMapping("/branches/{branchId}/tables/{tableId}/orders")
    public List<OrderResult> tableOrders(@AuthenticationPrincipal CustomUserDetails principal,
                                         @PathVariable Long branchId,
                                         @PathVariable Long tableId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        var table = tableRepo.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("الطاولة غير موجودة"));
        var sessionOpt = tableRepo.findOpenSessionByTableId(tableId);
        if (sessionOpt.isEmpty()) return List.of();

        return orderRepo.findUnpaidOrdersBySessionId(sessionOpt.get().getId()).stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED && o.getStatus() != OrderStatus.COMPLETED)
                .map(o -> OrderResult.from(o, table.getTableNumber()))
                .toList();
    }

    @GetMapping("/branches/{branchId}/menu")
    public PublicMenuResult menu(@AuthenticationPrincipal CustomUserDetails principal,
                                 @PathVariable Long branchId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return getPublicMenuUseCase.getMenuByBranchId(branchId);
    }

    @GetMapping("/branches/{branchId}/ready-orders")
    public List<OrderResult> readyOrders(@AuthenticationPrincipal CustomUserDetails principal,
                                         @PathVariable Long branchId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return getPosOrdersUseCase.getReadyForWaiterOrders(branchId);
    }

    @PostMapping("/branches/{branchId}/orders")
    public OrderResult placeOrder(@AuthenticationPrincipal CustomUserDetails principal,
                                  @PathVariable Long branchId,
                                  @RequestBody WaiterOrderRequest req) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return placeOrderUseCase.placeOrder(new PlaceOrderCommand(
                branchId, null, req.tableId(), OrderType.DINE_IN, OrderSource.WAITER,
                principal.getUserId(), null, null, null, req.notes(), req.items()
        ));
    }

    @PutMapping("/branches/{branchId}/orders/{orderId}")
    public OrderResult modifyPendingOrder(@AuthenticationPrincipal CustomUserDetails principal,
                                          @PathVariable Long branchId,
                                          @PathVariable Long orderId,
                                          @RequestBody WaiterOrderRequest req) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return modifyPendingOrderUseCase.modifyPendingOrder(
                new ModifyPendingOrderCommand(orderId, branchId, req.notes(), req.items()));
    }

    @PostMapping("/branches/{branchId}/orders/{orderId}/cancel")
    public OrderResult cancelPendingOrder(@AuthenticationPrincipal CustomUserDetails principal,
                                          @PathVariable Long branchId,
                                          @PathVariable Long orderId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        var order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("الطلب غير موجود"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("لا يمكن للويتر إلغاء الطلب بعد بدء المطبخ في تحضيره. راجع الكاشير.");
        }
        return updateOrderStatusUseCase.updateStatus(
                new UpdateOrderStatusCommand(orderId, branchId, OrderStatus.CANCELLED, principal.getUserId()));
    }

    @PostMapping("/branches/{branchId}/orders/{orderId}/serve")
    public OrderResult markServed(@AuthenticationPrincipal CustomUserDetails principal,
                                  @PathVariable Long branchId,
                                  @PathVariable Long orderId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return updateOrderStatusUseCase.updateStatus(
                new UpdateOrderStatusCommand(orderId, branchId, OrderStatus.SERVED, principal.getUserId()));
    }
}