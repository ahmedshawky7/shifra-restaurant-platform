package com.restaurant.platform.application.port.in;

import com.restaurant.platform.application.command.PlaceOrderCommand;
import com.restaurant.platform.application.result.OrderResult;

public interface PlaceOrderUseCase {
    OrderResult placeOrder(PlaceOrderCommand command);
}