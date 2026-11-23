package com.restaurant.platform.application.result;

import com.restaurant.platform.domain.model.Order;
import com.restaurant.platform.domain.model.OrderItem;
import com.restaurant.platform.domain.model.OrderItemAddon;
import com.restaurant.platform.domain.model.enums.OrderSource;
import com.restaurant.platform.domain.model.enums.OrderStatus;
import com.restaurant.platform.domain.model.enums.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResult(
        Long orderId,
        Integer orderNumber,
        String trackingToken,
        Long branchId,
        Long tableId,
        String tableNumber,
        OrderType orderType,
        OrderSource orderSource,
        OrderStatus status,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal serviceCharge,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String customerName,
        String notes,
        List<OrderItemResult> items,
        LocalDateTime prepStartedAt,
        LocalDateTime readyAt,
        LocalDateTime createdAt
) {
    public record OrderItemResult(
            Long id,
            Long menuItemId,
            String itemName,
            String variantName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String notes,
            List<String> addonNames
    ) {
        public static OrderItemResult from(OrderItem item) {
            return new OrderItemResult(
                    item.getId(),
                    item.getMenuItemId(),
                    item.getItemNameSnapshot(),
                    item.getVariantNameSnapshot(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getLineTotal(),
                    item.getNotes(),
                    item.getAddons().stream().map(OrderItemAddon::addonNameSnapshot).toList()
            );
        }
    }

    public static OrderResult from(Order order, String tableNumber) {
        return new OrderResult(
                order.getId(),
                order.getOrderNumber(),
                order.getTrackingToken(),
                order.getBranchId(),
                order.getTableId(),
                tableNumber,
                order.getOrderType(),
                order.getOrderSource(),
                order.getStatus(),
                order.getSubtotal(),
                order.getTaxAmount(),
                order.getServiceCharge(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getCustomerName(),
                order.getNotes(),
                order.getItems().stream().map(OrderItemResult::from).toList(),
                order.getPrepStartedAt(),
                order.getReadyAt(),
                order.getCreatedAt()
        );
    }
}