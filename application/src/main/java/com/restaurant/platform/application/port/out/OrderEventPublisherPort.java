package com.restaurant.platform.application.port.out;

import com.restaurant.platform.domain.model.OrderEventSnapshot;

public interface OrderEventPublisherPort {
    void publishToKds(OrderEventSnapshot event);
    void publishToPos(OrderEventSnapshot event);
    void publishToWaiter(OrderEventSnapshot event);
    void publishToCustomerTracking(OrderEventSnapshot event);
    void publishToPickupScreen(OrderEventSnapshot event);
}