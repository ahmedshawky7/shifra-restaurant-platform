package com.restaurant.platform.application.port.in;

import com.restaurant.platform.application.result.OrderResult;
import java.util.List;

public interface GetPosOrdersUseCase {
    List<OrderResult> getActivePosOrders(Long branchId);
    List<OrderResult> getReadyForWaiterOrders(Long branchId);
}