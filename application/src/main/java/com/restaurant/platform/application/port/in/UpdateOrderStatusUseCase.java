package com.restaurant.platform.application.port.in;

import com.restaurant.platform.application.command.UpdateOrderStatusCommand;
import com.restaurant.platform.application.result.OrderResult;

public interface UpdateOrderStatusUseCase {
    OrderResult updateStatus(UpdateOrderStatusCommand command);
}