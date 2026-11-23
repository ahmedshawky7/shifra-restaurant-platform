package com.restaurant.platform.domain.model;

import com.restaurant.platform.domain.model.enums.OrderStatus;
import com.restaurant.platform.domain.model.enums.OrderType;

import java.time.LocalDateTime;
import java.util.List;

public record OrderEventSnapshot(
        String eventType,
        Long branchId,
        Long orderId,
        Integer orderNumber,
        Long tableId,
        String tableNumber,
        OrderType orderType,
        OrderStatus status,
        String trackingToken,
        List<OrderItemSummary> items,
        LocalDateTime updatedAt
) {
    public record OrderItemSummary(
            String itemName,
            String variantName,
            int quantity,
            List<String> addons,
            String notes
    ) {}
}