package com.restaurant.platform.application.command;

import com.restaurant.platform.domain.model.enums.OrderSource;
import com.restaurant.platform.domain.model.enums.OrderType;

import java.util.List;
import java.util.Objects;

public record PlaceOrderCommand(
        Long branchId,
        String tableQrToken,
        Long tableId,
        OrderType orderType,
        OrderSource orderSource,
        Long createdByUserId,
        String customerName,
        String customerPhone,
        String deliveryAddress,
        String notes,
        List<OrderItemRequest> items
) {
    public PlaceOrderCommand {
        Objects.requireNonNull(orderType, "orderType");
        Objects.requireNonNull(orderSource, "orderSource");
        Objects.requireNonNull(items, "items");
    }

    public record OrderItemRequest(
            Long menuItemId,
            Long variantId,
            int quantity,
            String notes,
            List<Long> addonIds
    ) {
        public OrderItemRequest {
            Objects.requireNonNull(menuItemId, "menuItemId");
            addonIds = addonIds != null ? List.copyOf(addonIds) : List.of();
        }
    }
}