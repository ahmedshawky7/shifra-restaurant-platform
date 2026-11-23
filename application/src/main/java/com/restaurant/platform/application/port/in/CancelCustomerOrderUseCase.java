package com.restaurant.platform.application.port.in;

import com.restaurant.platform.application.result.OrderResult;

public interface CancelCustomerOrderUseCase {
    OrderResult cancelByTrackingToken(String trackingToken);
}