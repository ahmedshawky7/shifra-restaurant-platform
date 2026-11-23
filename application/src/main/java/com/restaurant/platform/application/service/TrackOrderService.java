package com.restaurant.platform.application.service;

import com.restaurant.platform.application.port.in.TrackOrderUseCase;
import com.restaurant.platform.application.port.out.OrderRepositoryPort;
import com.restaurant.platform.application.port.out.TableRepositoryPort;
import com.restaurant.platform.application.result.OrderResult;
import com.restaurant.platform.domain.model.Order;
import com.restaurant.platform.domain.model.RestaurantTable;

public class TrackOrderService implements TrackOrderUseCase {

    private final OrderRepositoryPort orderRepo;
    private final TableRepositoryPort tableRepo;

    public TrackOrderService(OrderRepositoryPort orderRepo, TableRepositoryPort tableRepo) {
        this.orderRepo = orderRepo;
        this.tableRepo = tableRepo;
    }

    @Override
    public OrderResult trackByToken(String trackingToken) {
        Order order = orderRepo.findByTrackingToken(trackingToken)
                .orElseThrow(() -> new IllegalArgumentException("الطلب غير موجود"));
        String tableNumber = order.getTableId() != null
                ? tableRepo.findById(order.getTableId()).map(RestaurantTable::getTableNumber).orElse(null)
                : null;
        return OrderResult.from(order, tableNumber);
    }
}