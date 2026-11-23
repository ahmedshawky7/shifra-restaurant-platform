package com.restaurant.platform.application.port.in;

import com.restaurant.platform.application.result.OrderResult;
import java.util.List;

public interface GetKdsOrdersUseCase {
    List<OrderResult> getActiveKdsOrders(Long branchId);
}