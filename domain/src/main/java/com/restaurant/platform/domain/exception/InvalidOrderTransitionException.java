package com.restaurant.platform.domain.exception;

import com.restaurant.platform.domain.model.enums.OrderStatus;

public class InvalidOrderTransitionException extends RuntimeException {

    private final OrderStatus currentStatus;
    private final String attemptedAction;

    public InvalidOrderTransitionException(OrderStatus currentStatus, String attemptedAction) {
        super("Cannot perform '%s' when order status is '%s'"
                .formatted(attemptedAction, currentStatus));
        this.currentStatus = currentStatus;
        this.attemptedAction = attemptedAction;
    }

    public OrderStatus getCurrentStatus() { return currentStatus; }
    public String getAttemptedAction() { return attemptedAction; }
}