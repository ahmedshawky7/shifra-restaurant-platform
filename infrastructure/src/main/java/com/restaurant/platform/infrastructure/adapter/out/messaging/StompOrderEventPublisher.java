package com.restaurant.platform.infrastructure.adapter.out.messaging;

import com.restaurant.platform.application.port.out.OrderEventPublisherPort;
import com.restaurant.platform.domain.model.OrderEventSnapshot;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StompOrderEventPublisher implements OrderEventPublisherPort {

    private final SimpMessagingTemplate messagingTemplate;

    public StompOrderEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publishToKds(OrderEventSnapshot event) {
        messagingTemplate.convertAndSend("/topic/branch/" + event.branchId() + "/kds", event);
    }

    @Override
    public void publishToPos(OrderEventSnapshot event) {
        messagingTemplate.convertAndSend("/topic/branch/" + event.branchId() + "/pos", event);
    }

    @Override
    public void publishToWaiter(OrderEventSnapshot event) {
        messagingTemplate.convertAndSend("/topic/branch/" + event.branchId() + "/waiter", event);
    }

    @Override
    public void publishToCustomerTracking(OrderEventSnapshot event) {
        if (event.trackingToken() != null && !event.trackingToken().isBlank()) {
            messagingTemplate.convertAndSend("/topic/orders/" + event.trackingToken(), event);
        }
    }

    @Override
    public void publishToPickupScreen(OrderEventSnapshot event) {
        messagingTemplate.convertAndSend("/topic/branch/" + event.branchId() + "/pickup", event);
    }
}