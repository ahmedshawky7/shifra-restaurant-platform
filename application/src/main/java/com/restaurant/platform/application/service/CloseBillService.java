package com.restaurant.platform.application.service;

import com.restaurant.platform.application.command.CloseBillCommand;
import com.restaurant.platform.application.port.in.CloseBillUseCase;
import com.restaurant.platform.application.port.out.*;
import com.restaurant.platform.application.result.InvoiceResult;
import com.restaurant.platform.domain.model.*;
import com.restaurant.platform.domain.model.enums.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class CloseBillService implements CloseBillUseCase {

    private final OrderRepositoryPort orderRepo;
    private final TableRepositoryPort tableRepo;
    private final InvoiceRepositoryPort invoiceRepo;
    private final BranchStatusPort branchStatusPort;
    private final OrderEventPublisherPort eventPublisher;
    private final OrganizationRepositoryPort organizationRepo;
    private final AuditLogPort auditLogPort;

    public CloseBillService(OrderRepositoryPort orderRepo,
                            TableRepositoryPort tableRepo,
                            InvoiceRepositoryPort invoiceRepo,
                            BranchStatusPort branchStatusPort,
                            OrderEventPublisherPort eventPublisher) {
        this(orderRepo, tableRepo, invoiceRepo, branchStatusPort, eventPublisher, null, entry -> {});
    }

    public CloseBillService(OrderRepositoryPort orderRepo,
                            TableRepositoryPort tableRepo,
                            InvoiceRepositoryPort invoiceRepo,
                            BranchStatusPort branchStatusPort,
                            OrderEventPublisherPort eventPublisher,
                            OrganizationRepositoryPort organizationRepo,
                            AuditLogPort auditLogPort) {
        this.orderRepo = orderRepo;
        this.tableRepo = tableRepo;
        this.invoiceRepo = invoiceRepo;
        this.branchStatusPort = branchStatusPort;
        this.eventPublisher = eventPublisher;
        this.organizationRepo = organizationRepo;
        this.auditLogPort = auditLogPort != null ? auditLogPort : entry -> {};
    }

    @Override
    public InvoiceResult closeBill(CloseBillCommand command) {
        ZoneId zone = branchStatusPort.getBranchZoneId(command.branchId());
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        Long orgId = branchStatusPort.getOrganizationIdByBranchId(command.branchId());
        String sellerName = resolveSellerName(orgId);

        if (command.tableId() != null) {
            return closeTableSessionBill(command, orgId, sellerName, today, now);
        } else if (command.singleOrderId() != null) {
            return closeSingleOrderBill(command, orgId, sellerName, today, now);
        } else {
            throw new IllegalArgumentException("يجب تحديد الطاولة أو رقم الطلب لإغلاق الفاتورة");
        }
    }

    private String resolveSellerName(Long orgId) {
        if (organizationRepo == null || orgId == null) return "مطعم";
        return organizationRepo.findById(orgId).map(Organization::getName).orElse("مطعم");
    }

    private InvoiceResult closeTableSessionBill(CloseBillCommand command, Long orgId, String sellerName,
                                                LocalDate today, LocalDateTime now) {
        RestaurantTable table = tableRepo.findById(command.tableId())
                .orElseThrow(() -> new IllegalArgumentException("الطاولة غير موجودة"));

        if (!table.getBranchId().equals(command.branchId())) {
            throw new IllegalArgumentException("الطاولة لا تتبع هذا الفرع");
        }

        TableSession session = tableRepo.findOpenSessionByTableId(table.getId())
                .orElseThrow(() -> new IllegalStateException("لا توجد جلسة مفتوحة لهذه الطاولة"));

        List<Order> unpaidOrders = orderRepo.findUnpaidOrdersBySessionId(session.getId()).stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED && o.getStatus() != OrderStatus.COMPLETED)
                .toList();

        if (unpaidOrders.isEmpty()) {
            throw new IllegalStateException("لا توجد طلبات غير مدفوعة لإصدار الفاتورة");
        }

        boolean hasPreparingOrder = unpaidOrders.stream()
                .anyMatch(o -> o.getStatus() == OrderStatus.PREPARING);
        if (hasPreparingOrder) {
            throw new IllegalStateException("يوجد طلب ما زال قيد التحضير في المطبخ، لا يمكن إغلاق الفاتورة قبل جاهزيته");
        }

        BigDecimal subtotal = unpaidOrders.stream().map(Order::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = unpaidOrders.stream().map(Order::getTaxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal service = unpaidOrders.stream().map(Order::getServiceCharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalDiscount = (command.discountAmount() != null ? command.discountAmount() : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        List<BigDecimal> orderDiscounts = allocateDiscountAcrossOrders(unpaidOrders, totalDiscount);

        String invoiceNo = invoiceRepo.nextInvoiceNumber(command.branchId(), today);
        Invoice invoice = Invoice.issue(
                orgId, command.branchId(), session.getId(), null, invoiceNo,
                subtotal, tax, service, totalDiscount,
                command.paymentMethod(), command.cashierUserId(), now
        );
        Invoice savedInvoice = invoiceRepo.save(invoice);

        for (int i = 0; i < unpaidOrders.size(); i++) {
            Order o = unpaidOrders.get(i);
            o.applyDiscount(orderDiscounts.get(i));
            o.complete();
            Order savedOrder = orderRepo.save(o);
            var event = OrderEventFactory.fromOrder("ORDER_COMPLETED", savedOrder, table.getTableNumber(), now);
            eventPublisher.publishToPos(event);
            eventPublisher.publishToKds(event);
            eventPublisher.publishToCustomerTracking(event);
            eventPublisher.publishToPickupScreen(event);
        }

        session.close(now);
        tableRepo.saveSession(session);

        table.release();
        tableRepo.save(table);

        var tableEvent = new OrderEventSnapshot(
                "TABLE_UPDATED", command.branchId(), null, null,
                table.getId(), table.getTableNumber(), null, null, null, List.of(), now
        );
        eventPublisher.publishToPos(tableEvent);
        eventPublisher.publishToWaiter(tableEvent);

        if (totalDiscount.compareTo(BigDecimal.ZERO) > 0) {
            auditLogPort.record(AuditLog.of(
                    orgId, command.branchId(), command.cashierUserId(), null,
                    "APPLY_DISCOUNT", "INVOICE", savedInvoice.getId(),
                    "0.00", totalDiscount.toPlainString(), now
            ));
        }
        auditLogPort.record(AuditLog.of(
                orgId, command.branchId(), command.cashierUserId(), null,
                "CLOSE_BILL", "INVOICE", savedInvoice.getId(),
                "TABLE_SESSION:" + session.getId(),
                "INV=" + savedInvoice.getInvoiceNumber() + ",TOTAL=" + savedInvoice.getTotalAmount() + ",METHOD=" + command.paymentMethod(),
                now
        ));

        return InvoiceResult.from(savedInvoice, sellerName, null);
    }

    private InvoiceResult closeSingleOrderBill(CloseBillCommand command, Long orgId, String sellerName,
                                               LocalDate today, LocalDateTime now) {
        Order order = orderRepo.findById(command.singleOrderId())
                .orElseThrow(() -> new IllegalArgumentException("الطلب غير موجود"));

        if (!order.getBranchId().equals(command.branchId())) {
            throw new IllegalArgumentException("الطلب لا ينتمي لهذا الفرع");
        }
        if (order.getStatus() == OrderStatus.PREPARING) {
            throw new IllegalStateException("الطلب ما زال قيد التحضير في المطبخ، لا يمكن إغلاق الفاتورة قبل جاهزيته");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("لا يمكن تحصيل فاتورة لطلب ملغي أو مكتمل بالفعل");
        }

        BigDecimal oldTotal = order.getTotalAmount();
        order.applyDiscount(command.discountAmount());
        order.complete();
        Order savedOrder = orderRepo.save(order);

        String tableNumber = savedOrder.getTableId() != null
                ? tableRepo.findById(savedOrder.getTableId()).map(RestaurantTable::getTableNumber).orElse(null)
                : null;

        String invoiceNo = invoiceRepo.nextInvoiceNumber(command.branchId(), today);
        Invoice invoice = Invoice.issue(
                orgId, command.branchId(), savedOrder.getTableSessionId(), savedOrder.getId(), invoiceNo,
                savedOrder.getSubtotal(), savedOrder.getTaxAmount(), savedOrder.getServiceCharge(),
                savedOrder.getDiscountAmount(), command.paymentMethod(), command.cashierUserId(), now
        );
        Invoice savedInvoice = invoiceRepo.save(invoice);

        var event = OrderEventFactory.fromOrder("ORDER_COMPLETED", savedOrder, tableNumber, now);
        eventPublisher.publishToPos(event);
        eventPublisher.publishToKds(event);
        eventPublisher.publishToCustomerTracking(event);
        eventPublisher.publishToPickupScreen(event);

        if (savedOrder.getTableSessionId() != null) {
            TableReleaseHelper.releaseTableIfNoActiveOrders(savedOrder, orderRepo, tableRepo, eventPublisher, now);
        }

        if (savedOrder.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            auditLogPort.record(AuditLog.of(
                    orgId, command.branchId(), command.cashierUserId(), null,
                    "APPLY_DISCOUNT", "ORDER", savedOrder.getId(),
                    oldTotal.toPlainString(),
                    "DISCOUNT=" + savedOrder.getDiscountAmount() + ",NEW_TOTAL=" + savedOrder.getTotalAmount(),
                    now
            ));
        }
        auditLogPort.record(AuditLog.of(
                orgId, command.branchId(), command.cashierUserId(), null,
                "CLOSE_BILL", "INVOICE", savedInvoice.getId(),
                "ORDER:" + savedOrder.getId(),
                "INV=" + savedInvoice.getInvoiceNumber() + ",TOTAL=" + savedInvoice.getTotalAmount() + ",METHOD=" + command.paymentMethod(),
                now
        ));

        return InvoiceResult.from(savedInvoice, sellerName, null);
    }

    private List<BigDecimal> allocateDiscountAcrossOrders(List<Order> orders, BigDecimal totalDiscount) {
        if (totalDiscount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("قيمة الخصم غير صالحة");
        }
        List<BigDecimal> grossList = orders.stream()
                .map(o -> o.getSubtotal().add(o.getTaxAmount()).add(o.getServiceCharge()).setScale(2, RoundingMode.HALF_UP))
                .toList();
        BigDecimal totalGross = grossList.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);

        if (totalDiscount.compareTo(totalGross) > 0) {
            throw new IllegalArgumentException("الخصم أكبر من قيمة الفاتورة");
        }

        List<BigDecimal> shares = new ArrayList<>(orders.size());
        if (totalDiscount.compareTo(BigDecimal.ZERO) == 0 || totalGross.compareTo(BigDecimal.ZERO) == 0) {
            for (int i = 0; i < orders.size(); i++) shares.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return shares;
        }

        BigDecimal remaining = totalDiscount.setScale(2, RoundingMode.HALF_UP);
        for (int i = 0; i < orders.size(); i++) {
            BigDecimal gross = grossList.get(i);
            BigDecimal share = (i == orders.size() - 1)
                    ? remaining.min(gross)
                    : totalDiscount.multiply(gross).divide(totalGross, 2, RoundingMode.DOWN).min(gross).min(remaining);
            shares.add(share);
            remaining = remaining.subtract(share);
        }

        for (int i = 0; i < orders.size() && remaining.compareTo(BigDecimal.ZERO) > 0; i++) {
            BigDecimal capacity = grossList.get(i).subtract(shares.get(i));
            if (capacity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal extra = remaining.min(capacity);
                shares.set(i, shares.get(i).add(extra));
                remaining = remaining.subtract(extra);
            }
        }
        return shares;
    }
}