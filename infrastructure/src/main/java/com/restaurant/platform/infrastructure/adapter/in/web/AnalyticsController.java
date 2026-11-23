package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.application.port.out.BranchStatusPort;
import com.restaurant.platform.domain.model.enums.OrderSource;
import com.restaurant.platform.domain.model.enums.OrderStatus;
import com.restaurant.platform.domain.model.enums.OrderType;
import com.restaurant.platform.domain.model.enums.PaymentMethod;
import com.restaurant.platform.domain.model.enums.PaymentStatus;
import com.restaurant.platform.infrastructure.adapter.out.persistence.InvoiceEntity;
import com.restaurant.platform.infrastructure.adapter.out.persistence.InvoiceJpaRepository;
import com.restaurant.platform.infrastructure.adapter.out.persistence.OrderEntity;
import com.restaurant.platform.infrastructure.adapter.out.persistence.OrderItemEntity;
import com.restaurant.platform.infrastructure.adapter.out.persistence.OrderJpaRepository;
import com.restaurant.platform.infrastructure.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final OrderJpaRepository orderRepo;
    private final InvoiceJpaRepository invoiceRepo;
    private final BranchStatusPort branchStatusPort;
    private final TenantGuard tenantGuard;

    public AnalyticsController(OrderJpaRepository orderRepo,
                               InvoiceJpaRepository invoiceRepo,
                               BranchStatusPort branchStatusPort,
                               TenantGuard tenantGuard) {
        this.orderRepo = orderRepo;
        this.invoiceRepo = invoiceRepo;
        this.branchStatusPort = branchStatusPort;
        this.tenantGuard = tenantGuard;
    }

    public record OrderHistoryRow(
            Long orderId,
            Integer orderNumber,
            OrderType orderType,
            OrderSource orderSource,
            OrderStatus status,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal serviceCharge,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            int itemsCount,
            LocalDateTime createdAt
    ) {}

    public record InvoiceHistoryRow(
            Long invoiceId,
            String invoiceNumber,
            Long tableSessionId,
            Long singleOrderId,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal serviceCharge,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus,
            LocalDateTime paidAt
    ) {}

    @GetMapping("/branches/{branchId}")
    @Transactional(readOnly = true)
    public Map<String, Object> branchAnalytics(@AuthenticationPrincipal CustomUserDetails principal,
                                               @PathVariable Long branchId) {
        tenantGuard.requireBranchAccess(principal, branchId);
        ZoneId zone = branchStatusPort.getBranchZoneId(branchId);
        LocalDate today = LocalDate.now(zone);

        List<OrderEntity> todayOrders = orderRepo.findByBranchIdAndOrderDateOrderByCreatedAtDesc(branchId, today);
        List<OrderEntity> validToday = todayOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .toList();

        BigDecimal todayRevenue = validToday.stream()
                .map(OrderEntity::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal avgTicket = validToday.isEmpty() ? BigDecimal.ZERO
                : todayRevenue.divide(BigDecimal.valueOf(validToday.size()), 2, RoundingMode.HALF_UP);

        long activeOrders = validToday.stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED)
                .count();

        long qrCount = orderRepo.countByBranchIdAndOrderDateAndOrderSource(branchId, today, OrderSource.QR_CUSTOMER);
        long posCount = orderRepo.countByBranchIdAndOrderDateAndOrderSource(branchId, today, OrderSource.CASHIER_POS);
        long waiterCount = orderRepo.countByBranchIdAndOrderDateAndOrderSource(branchId, today, OrderSource.WAITER);

        Map<String, Integer> itemCounts = new HashMap<>();
        for (OrderEntity o : validToday) {
            for (OrderItemEntity i : o.getItems()) {
                itemCounts.merge(i.getItemNameSnapshot(), i.getQuantity(), Integer::sum);
            }
        }
        var topItems = itemCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> Map.of("name", e.getKey(), "quantity", e.getValue()))
                .toList();

        LocalDate weekStart = today.minusDays(6);
        var rawWeekly = orderRepo.countAndRevenueDailyBetween(branchId, weekStart, today);
        Map<LocalDate, Object[]> weeklyMap = rawWeekly.stream()
                .collect(Collectors.toMap(r -> (LocalDate) r[0], r -> r));

        List<Map<String, Object>> weekly = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = weekStart.plusDays(i);
            Object[] row = weeklyMap.get(d);
            weekly.add(Map.of(
                    "day", d.toString(),
                    "orders", row != null ? ((Number) row[1]).longValue() : 0L,
                    "revenue", row != null ? row[2] : BigDecimal.ZERO
            ));
        }

        return Map.of(
                "todayOrdersCount", validToday.size(),
                "activeOrdersCount", activeOrders,
                "todayRevenue", todayRevenue,
                "averageTicket", avgTicket,
                "qrCount", qrCount,
                "posCount", posCount,
                "waiterCount", waiterCount,
                "topItems", topItems,
                "weekly", weekly
        );
    }

    @GetMapping("/branches/{branchId}/orders")
    @Transactional(readOnly = true)
    public Page<OrderHistoryRow> orderHistory(@AuthenticationPrincipal CustomUserDetails principal,
                                              @PathVariable Long branchId,
                                              @RequestParam(required = false) OrderStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        tenantGuard.requireBranchAccess(principal, branchId);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);

        Page<OrderEntity> result = status != null
                ? orderRepo.findByBranchIdAndStatusOrderByCreatedAtDesc(branchId, status, pageable)
                : orderRepo.findByBranchIdOrderByCreatedAtDesc(branchId, pageable);

        return result.map(o -> new OrderHistoryRow(
                o.getId(),
                o.getOrderNumber(),
                o.getOrderType(),
                o.getOrderSource(),
                o.getStatus(),
                o.getSubtotal(),
                o.getTaxAmount(),
                o.getServiceCharge(),
                o.getDiscountAmount(),
                o.getTotalAmount(),
                o.getItems() != null ? o.getItems().size() : 0,
                o.getCreatedAt()
        ));
    }

    @GetMapping("/branches/{branchId}/invoices")
    @Transactional(readOnly = true)
    public Page<InvoiceHistoryRow> invoiceHistory(@AuthenticationPrincipal CustomUserDetails principal,
                                                  @PathVariable Long branchId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        tenantGuard.requireBranchAccess(principal, branchId);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);

        Page<InvoiceEntity> result = invoiceRepo.findByBranchIdOrderByPaidAtDesc(branchId, pageable);
        return result.map(inv -> new InvoiceHistoryRow(
                inv.getId(),
                inv.getInvoiceNumber(),
                inv.getTableSessionId(),
                inv.getSingleOrderId(),
                inv.getSubtotal(),
                inv.getTaxAmount(),
                inv.getServiceCharge(),
                inv.getDiscountAmount(),
                inv.getTotalAmount(),
                inv.getPaymentMethod(),
                inv.getPaymentStatus(),
                inv.getPaidAt()
        ));
    }
}