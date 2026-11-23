package com.restaurant.platform.application.command;

import java.util.List;
import java.util.Objects;

public record ModifyPendingOrderCommand(
        Long orderId,
        Long branchId,
        String notes,
        List<PlaceOrderCommand.OrderItemRequest> items
) {
    public ModifyPendingOrderCommand {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(branchId, "branchId");
        Objects.requireNonNull(items, "items");
    }
}