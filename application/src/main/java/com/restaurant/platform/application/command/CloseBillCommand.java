package com.restaurant.platform.application.command;

import com.restaurant.platform.domain.model.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.Objects;

public record CloseBillCommand(
        Long branchId,
        Long tableId,
        Long singleOrderId,
        BigDecimal discountAmount,
        PaymentMethod paymentMethod,
        Long cashierUserId
) {
    public CloseBillCommand {
        Objects.requireNonNull(branchId, "branchId");
        Objects.requireNonNull(paymentMethod, "paymentMethod");
        Objects.requireNonNull(cashierUserId, "cashierUserId");
        discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
    }
}