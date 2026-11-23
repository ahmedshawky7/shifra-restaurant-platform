package com.restaurant.platform.application.service;

import com.restaurant.platform.application.command.TableAlertCommand;
import com.restaurant.platform.application.port.in.SendTableAlertUseCase;
import com.restaurant.platform.application.port.out.BranchStatusPort;
import com.restaurant.platform.application.port.out.OrderEventPublisherPort;
import com.restaurant.platform.application.port.out.TableRepositoryPort;
import com.restaurant.platform.domain.model.OrderEventSnapshot;
import com.restaurant.platform.domain.model.RestaurantTable;
import com.restaurant.platform.domain.model.enums.OrderType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class SendTableAlertService implements SendTableAlertUseCase {

    private final TableRepositoryPort tableRepo;
    private final BranchStatusPort branchStatusPort;
    private final OrderEventPublisherPort eventPublisher;

    public SendTableAlertService(TableRepositoryPort tableRepo,
                                 BranchStatusPort branchStatusPort,
                                 OrderEventPublisherPort eventPublisher) {
        this.tableRepo = tableRepo;
        this.branchStatusPort = branchStatusPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void sendAlert(TableAlertCommand command) {
        String normalizedType = command.alertType().trim().toUpperCase();
        if (!normalizedType.equals("CALL_WAITER") && !normalizedType.equals("REQUEST_BILL")) {
            throw new IllegalArgumentException("نوع التنبيه غير صالح: " + command.alertType());
        }

        RestaurantTable table = tableRepo.findByQrToken(command.tableQrToken())
                .orElseThrow(() -> new IllegalArgumentException("رمز الطاولة غير صالح"));

        ZoneId zone = branchStatusPort.getBranchZoneId(table.getBranchId());
        OrderEventSnapshot alert = new OrderEventSnapshot(
                normalizedType,
                table.getBranchId(),
                null,
                null,
                table.getId(),
                table.getTableNumber(),
                OrderType.DINE_IN,
                null,
                null,
                List.of(),
                LocalDateTime.now(zone)
        );

        eventPublisher.publishToWaiter(alert);
        eventPublisher.publishToPos(alert);
    }
}