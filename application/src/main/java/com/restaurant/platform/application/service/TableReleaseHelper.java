package com.restaurant.platform.application.service;
import com.restaurant.platform.application.port.out.OrderEventPublisherPort;
import com.restaurant.platform.application.port.out.OrderRepositoryPort;
import com.restaurant.platform.application.port.out.TableRepositoryPort;
import com.restaurant.platform.domain.model.Order;
import com.restaurant.platform.domain.model.OrderEventSnapshot;
import com.restaurant.platform.domain.model.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

final class TableReleaseHelper {
    private TableReleaseHelper() {}

    static void releaseTableIfNoActiveOrders(Order order,
                                             OrderRepositoryPort orderRepo,
                                             TableRepositoryPort tableRepo,
                                             OrderEventPublisherPort eventPublisher,
                                             LocalDateTime now) {
        if (order.getTableSessionId() == null || order.getTableId() == null) {
            return;
        }

        boolean hasRemainingActive = orderRepo.findUnpaidOrdersBySessionId(order.getTableSessionId())
                .stream()
                .anyMatch(o -> o.getStatus() != OrderStatus.CANCELLED && o.getStatus() != OrderStatus.COMPLETED);

        if (!hasRemainingActive) {
            tableRepo.findSessionById(order.getTableSessionId()).ifPresent(session -> {
                if (session.isOpen()) {
                    session.close(now);
                    tableRepo.saveSession(session);
                }
            });
            tableRepo.findById(order.getTableId()).ifPresent(table -> {
                table.release();
                tableRepo.save(table);
                var tableEvent = new OrderEventSnapshot(
                        "TABLE_UPDATED", order.getBranchId(), null, null,
                        table.getId(), table.getTableNumber(), null, null, null, List.of(), now
                );
                eventPublisher.publishToPos(tableEvent);
                eventPublisher.publishToWaiter(tableEvent);
            });
        }
    }
}