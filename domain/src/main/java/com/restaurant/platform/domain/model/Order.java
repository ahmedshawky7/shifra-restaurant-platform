package com.restaurant.platform.domain.model;

import com.restaurant.platform.domain.exception.InvalidOrderTransitionException;
import com.restaurant.platform.domain.model.enums.OrderSource;
import com.restaurant.platform.domain.model.enums.OrderStatus;
import com.restaurant.platform.domain.model.enums.OrderType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class Order {

    private final Long id;
    private final Long organizationId;
    private final Long branchId;
    private final Long tableId;
    private final Long tableSessionId;
    private final Integer orderNumber;
    private final String trackingToken;
    private final OrderType orderType;
    private final OrderSource orderSource;
    private final Long createdByUserId;
    private final String customerName;
    private final String customerPhone;
    private final String deliveryAddress;
    private String notes;
    private List<OrderItem> items;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal serviceCharge;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private LocalDateTime prepStartedAt;
    private LocalDateTime readyAt;
    private final LocalDateTime createdAt;

    private Order(Long id, Long organizationId, Long branchId, Long tableId, Long tableSessionId,
            Integer orderNumber, String trackingToken, OrderType orderType, OrderSource orderSource,
            Long createdByUserId, String customerName, String customerPhone, String deliveryAddress,
            String notes, List<OrderItem> items, BigDecimal subtotal, BigDecimal taxAmount,
            BigDecimal serviceCharge, BigDecimal discountAmount, BigDecimal totalAmount,
            OrderStatus status, LocalDateTime prepStartedAt, LocalDateTime readyAt,
            LocalDateTime createdAt) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.branchId = Objects.requireNonNull(branchId, "branchId");
        this.tableId = tableId;
        this.tableSessionId = tableSessionId;
        this.orderNumber = Objects.requireNonNull(orderNumber, "orderNumber");
        this.trackingToken = Objects.requireNonNull(trackingToken, "trackingToken");
        this.orderType = Objects.requireNonNull(orderType, "orderType");
        this.orderSource = Objects.requireNonNull(orderSource, "orderSource");
        this.createdByUserId = createdByUserId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.deliveryAddress = deliveryAddress;
        this.notes = notes;
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.serviceCharge = serviceCharge;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.totalAmount = totalAmount;
        this.status = Objects.requireNonNull(status, "status");
        this.prepStartedAt = prepStartedAt;
        this.readyAt = readyAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Order place(Long organizationId, Long branchId, Long tableId, Long tableSessionId,
            int orderNumber, String trackingToken, OrderType orderType,
            OrderSource orderSource, Long createdByUserId, String customerName,
            String customerPhone, String deliveryAddress, String notes,
            List<OrderItem> items, BigDecimal taxRatePercent,
            BigDecimal serviceChargeRatePercent, LocalDateTime nowInBranchZone) {
        if (orderNumber <= 0) {
            throw new IllegalArgumentException("orderNumber must be positive");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("لا يمكن إرسال طلب فارغ بدون أصناف");
        }
        if (orderType == OrderType.DINE_IN && orderSource == OrderSource.QR_CUSTOMER && tableId == null) {
            throw new IllegalArgumentException("طلب الطاولة يتطلب تحديد الطاولة");
        }

        BigDecimal[] totals = computeTotals(items, orderType, taxRatePercent, serviceChargeRatePercent);

        return new Order(null, organizationId, branchId, tableId, tableSessionId,
                orderNumber, trackingToken, orderType, orderSource, createdByUserId,
                customerName, customerPhone, deliveryAddress, notes, items,
                totals[0], totals[1], totals[2], BigDecimal.ZERO, totals[3],
                OrderStatus.PENDING, null, null, nowInBranchZone);
    }

    public static Order reconstruct(Long id, Long organizationId, Long branchId, Long tableId, Long tableSessionId,
            Integer orderNumber, String trackingToken, OrderType orderType,
            OrderSource orderSource, Long createdByUserId, String customerName,
            String customerPhone, String deliveryAddress, String notes,
            List<OrderItem> items, BigDecimal subtotal, BigDecimal taxAmount,
            BigDecimal serviceCharge, BigDecimal discountAmount, BigDecimal totalAmount,
            OrderStatus status, LocalDateTime prepStartedAt, LocalDateTime readyAt,
            LocalDateTime createdAt) {
        return new Order(id, organizationId, branchId, tableId, tableSessionId, orderNumber, trackingToken,
                orderType, orderSource, createdByUserId, customerName, customerPhone, deliveryAddress, notes,
                items, subtotal, taxAmount, serviceCharge, discountAmount, totalAmount,
                status, prepStartedAt, readyAt, createdAt);
    }

    public void updatePendingItems(List<OrderItem> newItems, String newNotes,
            BigDecimal taxRatePercent, BigDecimal serviceChargeRatePercent) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("لا يمكن تعديل الطلب بعد بدء المطبخ في تحضيره.");
        }
        if (newItems == null || newItems.isEmpty()) {
            throw new IllegalArgumentException("يجب أن يحتوي الطلب على صنف واحد على الأقل (أو قم بإلغائه).");
        }
        BigDecimal[] totals = computeTotals(newItems, this.orderType, taxRatePercent, serviceChargeRatePercent);
        this.items = List.copyOf(newItems);
        this.notes = newNotes;
        this.subtotal = totals[0];
        this.taxAmount = totals[1];
        this.serviceCharge = totals[2];
        this.discountAmount = BigDecimal.ZERO;
        this.totalAmount = totals[3];
    }

    private static BigDecimal[] computeTotals(List<OrderItem> items, OrderType orderType,
            BigDecimal taxRatePercent, BigDecimal serviceChargeRatePercent) {
        BigDecimal sub = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxRate = taxRatePercent != null ? taxRatePercent : BigDecimal.ZERO;
        BigDecimal tax = sub.multiply(taxRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal svcRate = (orderType == OrderType.DINE_IN && serviceChargeRatePercent != null)
                ? serviceChargeRatePercent
                : BigDecimal.ZERO;
        BigDecimal service = sub.multiply(svcRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal total = sub.add(tax).add(service).setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal[] { sub, tax, service, total };
    }

    public void startPreparing(LocalDateTime now) {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderTransitionException(status, "startPreparing");
        }
        this.status = OrderStatus.PREPARING;
        this.prepStartedAt = now;
    }

    public void markReady(LocalDateTime now) {
        if (status != OrderStatus.PREPARING) {
            throw new InvalidOrderTransitionException(status, "markReady");
        }
        this.status = OrderStatus.READY;
        this.readyAt = now;
    }

    public void markServed() {
        if (status != OrderStatus.READY) {
            throw new InvalidOrderTransitionException(status, "markServed");
        }
        this.status = OrderStatus.SERVED;
    }

    public void complete() {
        if (status != OrderStatus.READY && status != OrderStatus.SERVED && status != OrderStatus.PENDING) {
            throw new InvalidOrderTransitionException(status, "complete");
        }
        this.status = OrderStatus.COMPLETED;
    }

    public void cancel() {
        cancelByStaff(true);
    }

    public void cancelByStaff(boolean isManager) {
        if (status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED) {
            throw new InvalidOrderTransitionException(status, "cancel");
        }
        if (!isManager && status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "لا يمكن للكاشير إلغاء الطلب بعد بدء تحضيره في المطبخ؛ يتطلب ذلك صلاحية مدير المطعم.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void cancelByCustomer() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("لا يمكن إلغاء الطلب بعد بدء المطبخ في تحضيره.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void applyDiscount(BigDecimal discount) {
        if (discount == null || discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("قيمة الخصم غير صالحة");
        }
        BigDecimal gross = subtotal.add(taxAmount).add(serviceCharge);
        if (discount.compareTo(gross) > 0) {
            throw new IllegalArgumentException("الخصم لا يمكن أن يتجاوز إجمالي الطلب");
        }
        this.discountAmount = discount.setScale(2, RoundingMode.HALF_UP);
        this.totalAmount = gross.subtract(this.discountAmount).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public Long getTableId() {
        return tableId;
    }

    public Long getTableSessionId() {
        return tableSessionId;
    }

    public Integer getOrderNumber() {
        return orderNumber;
    }

    public String getTrackingToken() {
        return trackingToken;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public OrderSource getOrderSource() {
        return orderSource;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getNotes() {
        return notes;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getServiceCharge() {
        return serviceCharge;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getPrepStartedAt() {
        return prepStartedAt;
    }

    public LocalDateTime getReadyAt() {
        return readyAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}