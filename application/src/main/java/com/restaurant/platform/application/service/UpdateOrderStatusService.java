package com.restaurant.platform.application.service;

import com.restaurant.platform.application.command.UpdateOrderStatusCommand;
import com.restaurant.platform.application.port.in.UpdateOrderStatusUseCase;
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
import com.restaurant.platform.domain.model.enums.OrderStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Predicate;

public class UpdateOrderStatusService implements UpdateOrderStatusUseCase {

    private final OrderRepositoryPort orderRepo;
    private final TableRepositoryPort tableRepo;
    private final BranchStatusPort branchStatusPort;
    private final OrderEventPublisherPort eventPublisher;
    private final AuditLogPort auditLogPort;
    private final Predicate<Long> isManagerCheck;

    public UpdateOrderStatusService(OrderRepositoryPort orderRepo,
            TableRepositoryPort tableRepo,
            BranchStatusPort branchStatusPort,
            OrderEventPublisherPort eventPublisher) {
        this(orderRepo, tableRepo, branchStatusPort, eventPublisher, entry -> {
        }, null);
    }

    public UpdateOrderStatusService(OrderRepositoryPort orderRepo,
            TableRepositoryPort tableRepo,
            BranchStatusPort branchStatusPort,
            OrderEventPublisherPort eventPublisher,
            AuditLogPort auditLogPort) {
        this(orderRepo, tableRepo, branchStatusPort, eventPublisher, auditLogPort, null);
    }

    public UpdateOrderStatusService(OrderRepositoryPort orderRepo,
            TableRepositoryPort tableRepo,
            BranchStatusPort branchStatusPort,
            OrderEventPublisherPort eventPublisher,
            AuditLogPort auditLogPort,
            Predicate<Long> isManagerCheck) {
        this.orderRepo = orderRepo;
        this.tableRepo = tableRepo;
        this.branchStatusPort = branchStatusPort;
        this.eventPublisher = eventPublisher;
        this.auditLogPort = auditLogPort != null ? auditLogPort : entry -> {
        };
        this.isManagerCheck = isManagerCheck != null ? isManagerCheck : id -> true;
    }

    @Override
    public OrderResult updateStatus(UpdateOrderStatusCommand command) {
        Order order = orderRepo.findById(command.orderId())
                .orElseThrow(() -> new IllegalArgumentException("الطلب غير موجود"));

        if (!order.getBranchId().equals(command.branchId())) {
            throw new IllegalArgumentException("الطلب لا ينتمي لهذا الفرع");
        }

        OrderStatus previousStatus = order.getStatus();
        ZoneId zone = branchStatusPort.getBranchZoneId(order.getBranchId());
        LocalDateTime now = LocalDateTime.now(zone);

        switch (command.targetStatus()) {
            case PREPARING -> order.startPreparing(now);
            case READY -> order.markReady(now);
            case SERVED -> order.markServed();
            case COMPLETED -> order.complete();
            case CANCELLED -> {
                boolean isManager = command.actorUserId() != null && isManagerCheck.test(command.actorUserId());
                order.cancelByStaff(isManager);
            }
            default -> throw new IllegalArgumentException("حالة غير مدعومة للانتقال: " + command.targetStatus());
        }

        Order saved = orderRepo.save(order);
        if (saved.getStatus() == OrderStatus.CANCELLED) {
            TableReleaseHelper.releaseTableIfNoActiveOrders(saved, orderRepo, tableRepo, eventPublisher, now);
            auditLogPort.record(AuditLog.of(
                    saved.getOrganizationId(),
                    saved.getBranchId(),
                    command.actorUserId(),
                    null,
                    "CANCEL_ORDER",
                    "ORDER",
                    saved.getId(),
                    previousStatus.name(),
                    "CANCELLED (ORDER #" + saved.getOrderNumber() + ", TOTAL=" + saved.getTotalAmount() + ")",
                    now));
        }

        String tableNumber = saved.getTableId() != null
                ? tableRepo.findById(saved.getTableId()).map(RestaurantTable::getTableNumber).orElse(null)
                : null;

        String eventType = saved.getStatus() == OrderStatus.READY
                ? "ORDER_READY"
                : (saved.getStatus() == OrderStatus.CANCELLED ? "ORDER_CANCELLED" : "STATUS_CHANGED");

        OrderEventSnapshot event = OrderEventFactory.fromOrder(eventType, saved, tableNumber, now);

        eventPublisher.publishToKds(event);
        eventPublisher.publishToPos(event);
        eventPublisher.publishToCustomerTracking(event);
        eventPublisher.publishToPickupScreen(event);

        if (saved.getStatus() == OrderStatus.READY || saved.getStatus() == OrderStatus.SERVED) {
            eventPublisher.publishToWaiter(event);
        }

        return OrderResult.from(saved, tableNumber);
    }
}