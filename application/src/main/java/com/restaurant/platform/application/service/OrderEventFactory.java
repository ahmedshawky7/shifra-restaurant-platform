package com.restaurant.platform.application.service;

import com.restaurant.platform.domain.model.Order;
import com.restaurant.platform.domain.model.OrderEventSnapshot;
import com.restaurant.platform.domain.model.OrderItemAddon;

import java.time.LocalDateTime;

final class OrderEventFactory {
    private OrderEventFactory() {}

    static OrderEventSnapshot fromOrder(String eventType, Order order, String tableNumber, LocalDateTime now) {
        var summaries = order.getItems().stream()
                .map(i -> new OrderEventSnapshot.OrderItemSummary(
                        i.getItemNameSnapshot(),
                        i.getVariantNameSnapshot(),
                        i.getQuantity(),
                        i.getAddons().stream().map(OrderItemAddon::addonNameSnapshot).toList(),
                        i.getNotes()
                ))
                .toList();

        return new OrderEventSnapshot(
                eventType,
                order.getBranchId(),
                order.getId(),
                order.getOrderNumber(),
                order.getTableId(),
                tableNumber,
                order.getOrderType(),
                order.getStatus(),
                order.getTrackingToken(),
                summaries,
                now
        );
    }
}