package com.restaurant.platform.application.service;

import com.restaurant.platform.application.port.in.CancelCustomerOrderUseCase;
import com.restaurant.platform.application.port.out.AuditLogPort;
import com.restaurant.platform.application.port.out.BranchStatusPort;
import com.restaurant.platform.application.port.out.OrderEventPublisherPort;
import com.restaurant.platform.application.port.out.OrderRepositoryPort;
import com.restaurant.platform.application.port.out.TableRepositoryPort;
import com.restaurant.platform.application.result.OrderResult;
import com.restaurant.platform.domain.model.AuditLog;
import com.restaurant.platform.domain.model.Order;
import com.restaurant.platform.domain.model.OrderEventSnapshot;
import com.restaurant.platform.domain.model.RestaurantTable;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class CancelCustomerOrderService implements CancelCustomerOrderUseCase {

    private final OrderRepositoryPort orderRepo;
    private final TableRepositoryPort tableRepo;
    private final BranchStatusPort branchStatusPort;
    private final OrderEventPublisherPort eventPublisher;
    private final AuditLogPort auditLogPort;

    public CancelCustomerOrderService(OrderRepositoryPort orderRepo,
                                      TableRepositoryPort tableRepo,
                                      BranchStatusPort branchStatusPort,
                                      OrderEventPublisherPort eventPublisher) {
        this(orderRepo, tableRepo, branchStatusPort, eventPublisher, entry -> {});
    }

    public CancelCustomerOrderService(OrderRepositoryPort orderRepo,
                                      TableRepositoryPort tableRepo,
                                      BranchStatusPort branchStatusPort,
                                      OrderEventPublisherPort eventPublisher,
                                      AuditLogPort auditLogPort) {
        this.orderRepo = orderRepo;
        this.tableRepo = tableRepo;
        this.branchStatusPort = branchStatusPort;
        this.eventPublisher = eventPublisher;
        this.auditLogPort = auditLogPort != null ? auditLogPort : entry -> {};
    }

    @Override
    public OrderResult cancelByTrackingToken(String trackingToken) {
        Order order = orderRepo.findByTrackingToken(trackingToken)
                .orElseThrow(() -> new IllegalArgumentException("الطلب غير موجود"));

        String prev = order.getStatus().name();
        order.cancelByCustomer();
        Order saved = orderRepo.save(order);

        ZoneId zone = branchStatusPort.getBranchZoneId(saved.getBranchId());
        LocalDateTime now = LocalDateTime.now(zone);

        String tableNumber = saved.getTableId() != null
                ? tableRepo.findById(saved.getTableId()).map(RestaurantTable::getTableNumber).orElse(null)
                : null;

        auditLogPort.record(AuditLog.of(
                saved.getOrganizationId(),
                saved.getBranchId(),
                null,
                "QR_CUSTOMER",
                "CUSTOMER_CANCEL_ORDER",
                "ORDER",
                saved.getId(),
                prev,
                "CANCELLED (ORDER #" + saved.getOrderNumber() + ")",
                now
        ));

        OrderEventSnapshot event = OrderEventFactory.fromOrder("ORDER_CANCELLED", saved, tableNumber, now);
        eventPublisher.publishToKds(event);
        eventPublisher.publishToPos(event);
        eventPublisher.publishToCustomerTracking(event);
        eventPublisher.publishToPickupScreen(event);
        TableReleaseHelper.releaseTableIfNoActiveOrders(saved, orderRepo, tableRepo, eventPublisher, now);

        return OrderResult.from(saved, tableNumber);
    }
}