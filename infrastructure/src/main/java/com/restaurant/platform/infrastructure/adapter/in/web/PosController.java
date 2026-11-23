package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.application.command.CloseBillCommand;
import com.restaurant.platform.application.command.ModifyPendingOrderCommand;
import com.restaurant.platform.application.command.PlaceOrderCommand;
import com.restaurant.platform.application.command.UpdateOrderStatusCommand;
import com.restaurant.platform.application.port.in.*;
import com.restaurant.platform.application.port.out.OrderRepositoryPort;
import com.restaurant.platform.application.port.out.TableRepositoryPort;
import com.restaurant.platform.application.result.InvoiceResult;
import com.restaurant.platform.application.result.OrderResult;
import com.restaurant.platform.application.result.PublicMenuResult;
import com.restaurant.platform.domain.model.Order;
import com.restaurant.platform.domain.model.enums.OrderSource;
import com.restaurant.platform.domain.model.enums.OrderStatus;
import com.restaurant.platform.domain.model.enums.OrderType;
import com.restaurant.platform.domain.model.enums.PaymentMethod;
import com.restaurant.platform.infrastructure.adapter.out.persistence.InvoiceEntity;
import com.restaurant.platform.infrastructure.adapter.out.persistence.InvoiceJpaRepository;
import com.restaurant.platform.infrastructure.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pos")
public class PosController {

    private final GetPosOrdersUseCase getPosOrdersUseCase;
    private final PlaceOrderUseCase placeOrderUseCase;
    private final ModifyPendingOrderUseCase modifyPendingOrderUseCase;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final CloseBillUseCase closeBillUseCase;
    private final GetPublicMenuUseCase getPublicMenuUseCase;
    private final TableRepositoryPort tableRepo;
    private final OrderRepositoryPort orderRepo;
    private final InvoiceJpaRepository invoiceRepo;
    private final TenantGuard tenantGuard;

    public PosController(GetPosOrdersUseCase getPosOrdersUseCase,
                         PlaceOrderUseCase placeOrderUseCase,
                         ModifyPendingOrderUseCase modifyPendingOrderUseCase,
                         UpdateOrderStatusUseCase updateOrderStatusUseCase,
                         CloseBillUseCase closeBillUseCase,
                         GetPublicMenuUseCase getPublicMenuUseCase,
                         TableRepositoryPort tableRepo,
                         OrderRepositoryPort orderRepo,
                         InvoiceJpaRepository invoiceRepo,
                         TenantGuard tenantGuard) {
        this.getPosOrdersUseCase = getPosOrdersUseCase;
        this.placeOrderUseCase = placeOrderUseCase;
        this.modifyPendingOrderUseCase = modifyPendingOrderUseCase;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.closeBillUseCase = closeBillUseCase;
        this.getPublicMenuUseCase = getPublicMenuUseCase;
        this.tableRepo = tableRepo;
        this.orderRepo = orderRepo;
        this.invoiceRepo = invoiceRepo;
        this.tenantGuard = tenantGuard;
    }

    public record PosOrderRequest(
            Long tableId,
            OrderType orderType,
            String customerName,
            String customerPhone,
            String deliveryAddress,
            String notes,
            List<PlaceOrderCommand.OrderItemRequest> items
    ) {}

    public record CloseBillRequest(
            Long tableId,
            Long singleOrderId,
            BigDecimal discountAmount,
            PaymentMethod paymentMethod
    ) {}

    @GetMapping("/branches/{branchId}/menu")
    public PublicMenuResult branchMenu(@AuthenticationPrincipal CustomUserDetails principal,
                                       @PathVariable Long branchId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return getPublicMenuUseCase.getMenuByBranchId(branchId);
    }

    @GetMapping("/branches/{branchId}/tables")
    public List<Map<String, Object>> branchTables(@AuthenticationPrincipal CustomUserDetails principal,
                                                  @PathVariable Long branchId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return tableRepo.findByBranchId(branchId).stream().map(t -> {
            var sessionOpt = tableRepo.findOpenSessionByTableId(t.getId());
            BigDecimal unpaidSubtotal = BigDecimal.ZERO;
            BigDecimal unpaidTax = BigDecimal.ZERO;
            BigDecimal unpaidService = BigDecimal.ZERO;
            BigDecimal unpaidTotal = BigDecimal.ZERO;
            int activeOrders = 0;
            List<Map<String, Object>> unpaidItems = List.of();

            if (sessionOpt.isPresent()) {
                var orders = orderRepo.findUnpaidOrdersBySessionId(sessionOpt.get().getId()).stream()
                        .filter(o -> o.getStatus() != OrderStatus.CANCELLED && o.getStatus() != OrderStatus.COMPLETED)
                        .toList();
                activeOrders = orders.size();
                unpaidSubtotal = orders.stream().map(Order::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                unpaidTax = orders.stream().map(Order::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                unpaidService = orders.stream().map(Order::getServiceCharge).reduce(BigDecimal.ZERO, BigDecimal::add);
                unpaidTotal = orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                unpaidItems = orders.stream()
                        .flatMap(o -> o.getItems().stream())
                        .map(i -> Map.<String, Object>of(
                                "name", i.getItemNameSnapshot()
                                        + (i.getVariantNameSnapshot() != null ? " (" + i.getVariantNameSnapshot() + ")" : ""),
                                "quantity", i.getQuantity(),
                                "lineTotal", i.getLineTotal()))
                        .toList();
            }

            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", t.getId());
            dto.put("tableNumber", t.getTableNumber());
            dto.put("capacity", t.getCapacity());
            dto.put("status", t.getStatus().name());
            dto.put("active", t.isActive());
            dto.put("qrToken", t.getQrToken());
            dto.put("activeOrdersCount", activeOrders);
            dto.put("unpaidSubtotal", unpaidSubtotal);
            dto.put("unpaidTax", unpaidTax);
            dto.put("unpaidService", unpaidService);
            dto.put("unpaidTotal", unpaidTotal);
            dto.put("unpaidItems", unpaidItems);
            return dto;
        }).toList();
    }

    @GetMapping("/branches/{branchId}/orders")
    public List<OrderResult> activeOrders(@AuthenticationPrincipal CustomUserDetails principal,
                                          @PathVariable Long branchId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return getPosOrdersUseCase.getActivePosOrders(branchId);
    }

    @PostMapping("/branches/{branchId}/orders")
    public OrderResult createOrder(@AuthenticationPrincipal CustomUserDetails principal,
                                   @PathVariable Long branchId,
                                   @RequestBody PosOrderRequest req) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return placeOrderUseCase.placeOrder(new PlaceOrderCommand(
                branchId,
                null,
                req.tableId(),
                req.orderType() != null ? req.orderType() : OrderType.TAKEAWAY,
                OrderSource.CASHIER_POS,
                principal.getUserId(),
                req.customerName(),
                req.customerPhone(),
                req.deliveryAddress(),
                req.notes(),
                req.items()));
    }

    @PutMapping("/branches/{branchId}/orders/{orderId}")
    public OrderResult modifyOrder(@AuthenticationPrincipal CustomUserDetails principal,
                                   @PathVariable Long branchId,
                                   @PathVariable Long orderId,
                                   @RequestBody PosOrderRequest req) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return modifyPendingOrderUseCase.modifyPendingOrder(
                new ModifyPendingOrderCommand(orderId, branchId, req.notes(), req.items()));
    }

    @PostMapping("/branches/{branchId}/tables/{tableId}/release")
    public ResponseEntity<Void> releaseEmptyTable(@AuthenticationPrincipal CustomUserDetails principal,
                                                  @PathVariable Long branchId,
                                                  @PathVariable Long tableId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        var table = tableRepo.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("الطاولة غير موجودة"));
        if (!table.getBranchId().equals(branchId)) throw new SecurityException("غير مصرح");

        var sessionOpt = tableRepo.findOpenSessionByTableId(tableId);
        if (sessionOpt.isPresent()) {
            var remaining = orderRepo.findUnpaidOrdersBySessionId(sessionOpt.get().getId()).stream()
                    .filter(o -> o.getStatus() != OrderStatus.CANCELLED && o.getStatus() != OrderStatus.COMPLETED)
                    .toList();
            if (!remaining.isEmpty()) {
                throw new IllegalStateException("الطاولة لا تزال تحتوي على طلبات غير مدفوعة.");
            }
            var session = sessionOpt.get();
            session.close(LocalDateTime.now());
            tableRepo.saveSession(session);
        }
        table.release();
        tableRepo.save(table);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/branches/{branchId}/orders/{orderId}/serve")
    public OrderResult serveOrder(@AuthenticationPrincipal CustomUserDetails principal,
                                  @PathVariable Long branchId,
                                  @PathVariable Long orderId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return updateOrderStatusUseCase.updateStatus(
                new UpdateOrderStatusCommand(orderId, branchId, OrderStatus.SERVED, principal.getUserId()));
    }

    @PostMapping("/branches/{branchId}/orders/{orderId}/cancel")
    public OrderResult cancelOrder(@AuthenticationPrincipal CustomUserDetails principal,
                                   @PathVariable Long branchId,
                                   @PathVariable Long orderId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return updateOrderStatusUseCase.updateStatus(
                new UpdateOrderStatusCommand(orderId, branchId, OrderStatus.CANCELLED, principal.getUserId()));
    }

    @PostMapping("/branches/{branchId}/close-bill")
    public InvoiceResult closeBill(@AuthenticationPrincipal CustomUserDetails principal,
                                   @PathVariable Long branchId,
                                   @RequestBody CloseBillRequest req) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return closeBillUseCase.closeBill(new CloseBillCommand(
                branchId,
                req.tableId(),
                req.singleOrderId(),
                req.discountAmount(),
                req.paymentMethod(),
                principal.getUserId()));
    }

    @GetMapping("/branches/{branchId}/invoices/today")
    public List<InvoiceEntity> todayInvoices(@AuthenticationPrincipal CustomUserDetails principal,
                                             @PathVariable Long branchId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        return invoiceRepo.findByBranchIdAndPaidDateOrderByPaidAtDesc(branchId, LocalDate.now());
    }
}