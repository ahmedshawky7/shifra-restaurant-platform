package com.restaurant.platform.application.port.in;

import com.restaurant.platform.application.command.ModifyPendingOrderCommand;
import com.restaurant.platform.application.result.OrderResult;

public interface ModifyPendingOrderUseCase {
    OrderResult modifyPendingOrder(ModifyPendingOrderCommand command);
}