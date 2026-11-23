package com.restaurant.platform.application.port.in;

import com.restaurant.platform.application.result.OrderResult;

public interface TrackOrderUseCase {
    OrderResult trackByToken(String trackingToken);
}