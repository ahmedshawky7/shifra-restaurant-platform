package com.restaurant.platform.application.command;

import com.restaurant.platform.domain.model.enums.OrderStatus;
import java.util.Objects;

public record UpdateOrderStatusCommand(
        Long orderId,
        Long branchId,
        OrderStatus targetStatus,
        Long actorUserId
) {
    public UpdateOrderStatusCommand {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(branchId, "branchId");
        Objects.requireNonNull(targetStatus, "targetStatus");
    }
}