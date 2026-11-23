package com.restaurant.platform.application.service;

import com.restaurant.platform.application.command.ModifyPendingOrderCommand;
import com.restaurant.platform.application.port.in.ModifyPendingOrderUseCase;
import com.restaurant.platform.application.port.out.*;
import com.restaurant.platform.application.result.OrderResult;
import com.restaurant.platform.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class ModifyPendingOrderService implements ModifyPendingOrderUseCase {

    private final OrderRepositoryPort orderRepo;
    private final OrderItemResolver itemResolver;
    private final TableRepositoryPort tableRepo;
    private final BranchStatusPort branchStatusPort;
    private final OrderEventPublisherPort eventPublisher;

    public ModifyPendingOrderService(OrderRepositoryPort orderRepo,
                                     MenuRepositoryPort menuRepo,
                                     TableRepositoryPort tableRepo,
                                     BranchStatusPort branchStatusPort,
                                     OrderEventPublisherPort eventPublisher) {
        this.orderRepo = orderRepo;
        this.itemResolver = new OrderItemResolver(menuRepo);
        this.tableRepo = tableRepo;
        this.branchStatusPort = branchStatusPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OrderResult modifyPendingOrder(ModifyPendingOrderCommand command) {
        Order order = orderRepo.findById(command.orderId())
                .orElseThrow(() -> new IllegalArgumentException("الطلب غير موجود"));

        if (!order.getBranchId().equals(command.branchId())) {
            throw new IllegalArgumentException("الطلب لا ينتمي لهذا الفرع");
        }

        List<OrderItem> newItems = itemResolver.resolve(order.getOrganizationId(), order.getBranchId(), command.items());
        BigDecimal taxRate = branchStatusPort.getTaxRate(order.getBranchId());
        BigDecimal serviceRate = branchStatusPort.getServiceChargeRate(order.getBranchId());

        order.updatePendingItems(newItems, command.notes(), taxRate, serviceRate);
        Order saved = orderRepo.save(order);

        ZoneId zone = branchStatusPort.getBranchZoneId(saved.getBranchId());
        LocalDateTime now = LocalDateTime.now(zone);
        String tableNumber = saved.getTableId() != null
                ? tableRepo.findById(saved.getTableId()).map(RestaurantTable::getTableNumber).orElse(null)
                : null;

        OrderEventSnapshot event = OrderEventFactory.fromOrder("ORDER_UPDATED", saved, tableNumber, now);
        eventPublisher.publishToKds(event);
        eventPublisher.publishToPos(event);
        eventPublisher.publishToCustomerTracking(event);

        return OrderResult.from(saved, tableNumber);
    }
}