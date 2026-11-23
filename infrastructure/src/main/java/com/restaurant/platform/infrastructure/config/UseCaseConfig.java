package com.restaurant.platform.infrastructure.config;

import com.restaurant.platform.application.port.in.*;
import com.restaurant.platform.application.port.out.*;
import com.restaurant.platform.application.service.*;
import com.restaurant.platform.domain.model.RestaurantTable;
import com.restaurant.platform.domain.model.enums.UserRole;
import com.restaurant.platform.infrastructure.adapter.out.persistence.UserJpaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

@Configuration
public class UseCaseConfig {

    private final ConcurrentHashMap<Long, ReentrantLock> branchOrderLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ReentrantLock> branchBillLocks = new ConcurrentHashMap<>();

    @Bean
    public GetPublicMenuUseCase getPublicMenuUseCase(
            TableRepositoryPort tableRepo,
            BranchStatusPort branchStatusPort,
            OrganizationRepositoryPort orgRepo,
            MenuRepositoryPort menuRepo) {
        return new GetPublicMenuService(tableRepo, branchStatusPort, orgRepo, menuRepo);
    }

    @Bean
    public PlaceOrderUseCase placeOrderUseCase(
            OrderRepositoryPort orderRepo,
            MenuRepositoryPort menuRepo,
            TableRepositoryPort tableRepo,
            BranchStatusPort branchStatusPort,
            OrganizationRepositoryPort orgRepo,
            OrderEventPublisherPort eventPublisher,
            TransactionTemplate tx) {
        var core = new PlaceOrderService(orderRepo, menuRepo, tableRepo, branchStatusPort, orgRepo, eventPublisher);
        return command -> {
            Long lockBranchId = command.branchId();
            if (lockBranchId == null && command.tableQrToken() != null && !command.tableQrToken().isBlank()) {
                lockBranchId = tableRepo.findByQrToken(command.tableQrToken())
                        .map(RestaurantTable::getBranchId)
                        .orElse(-1L);
            } else if (lockBranchId == null && command.tableId() != null) {
                lockBranchId = tableRepo.findById(command.tableId())
                        .map(RestaurantTable::getBranchId)
                        .orElse(-1L);
            }
            if (lockBranchId == null) {
                lockBranchId = -1L;
            }
            ReentrantLock lock = branchOrderLocks.computeIfAbsent(lockBranchId, k -> new ReentrantLock());
            lock.lock();
            try {
                return tx.execute(status -> core.placeOrder(command));
            } finally {
                lock.unlock();
            }
        };
    }

    @Bean
    public TrackOrderUseCase trackOrderUseCase(
            OrderRepositoryPort orderRepo,
            TableRepositoryPort tableRepo) {
        return new TrackOrderService(orderRepo, tableRepo);
    }

    @Bean
    public CancelCustomerOrderUseCase cancelCustomerOrderUseCase(
            OrderRepositoryPort orderRepo,
            TableRepositoryPort tableRepo,
            BranchStatusPort branchStatusPort,
            OrderEventPublisherPort eventPublisher,
            AuditLogPort auditLogPort,
            TransactionTemplate tx) {
        var core = new CancelCustomerOrderService(orderRepo, tableRepo, branchStatusPort, eventPublisher, auditLogPort);
        return trackingToken -> tx.execute(status -> core.cancelByTrackingToken(trackingToken));
    }

    @Bean
    public SendTableAlertUseCase sendTableAlertUseCase(
            TableRepositoryPort tableRepo,
            BranchStatusPort branchStatusPort,
            OrderEventPublisherPort eventPublisher) {
        return new SendTableAlertService(tableRepo, branchStatusPort, eventPublisher);
    }

    @Bean
    public UpdateOrderStatusUseCase updateOrderStatusUseCase(
            OrderRepositoryPort orderRepo,
            TableRepositoryPort tableRepo,
            BranchStatusPort branchStatusPort,
            OrderEventPublisherPort eventPublisher,
            AuditLogPort auditLogPort,
            UserJpaRepository userRepo,
            TransactionTemplate tx) {
        Predicate<Long> isManager = userId -> userRepo.findById(userId)
                .map(u -> u.isOwner() || u.getRole() == UserRole.ORG_ADMIN)
                .orElse(false);
        var core = new UpdateOrderStatusService(orderRepo, tableRepo, branchStatusPort, eventPublisher, auditLogPort, isManager);
        return command -> tx.execute(status -> core.updateStatus(command));
    }

    @Bean
    public GetKdsOrdersUseCase getKdsOrdersUseCase(
            OrderRepositoryPort orderRepo,
            TableRepositoryPort tableRepo,
            BranchStatusPort branchStatusPort) {
        return new GetKdsOrdersService(orderRepo, tableRepo, branchStatusPort);
    }

    @Bean
    public GetPosOrdersUseCase getPosOrdersUseCase(
            OrderRepositoryPort orderRepo,
            TableRepositoryPort tableRepo,
            BranchStatusPort branchStatusPort) {
        return new GetPosOrdersService(orderRepo, tableRepo, branchStatusPort);
    }

    @Bean
    public CloseBillUseCase closeBillUseCase(
            OrderRepositoryPort orderRepo,
            TableRepositoryPort tableRepo,
            InvoiceRepositoryPort invoiceRepo,
            BranchStatusPort branchStatusPort,
            OrderEventPublisherPort eventPublisher,
            OrganizationRepositoryPort orgRepo,
            AuditLogPort auditLogPort,
            TransactionTemplate tx) {
        var core = new CloseBillService(orderRepo, tableRepo, invoiceRepo, branchStatusPort, eventPublisher, orgRepo, auditLogPort);
        return command -> {
            ReentrantLock lock = branchBillLocks.computeIfAbsent(command.branchId(), k -> new ReentrantLock());
            lock.lock();
            try {
                return tx.execute(status -> core.closeBill(command));
            } finally {
                lock.unlock();
            }
        };
    }

    @Bean
    public ToggleMenuItemAvailabilityUseCase toggleMenuItemAvailabilityUseCase(
            MenuRepositoryPort menuRepo,
            TransactionTemplate tx) {
        var core = new ToggleMenuItemAvailabilityService(menuRepo);
        return (orgId, branchId, menuItemId, available) ->
                tx.executeWithoutResult(status -> core.setAvailability(orgId, branchId, menuItemId, available));
    }

    @Bean
    public ModifyPendingOrderUseCase modifyPendingOrderUseCase(
            OrderRepositoryPort orderRepo,
            MenuRepositoryPort menuRepo,
            TableRepositoryPort tableRepo,
            BranchStatusPort branchStatusPort,
            OrderEventPublisherPort eventPublisher,
            TransactionTemplate tx) {
        var core = new ModifyPendingOrderService(orderRepo, menuRepo, tableRepo, branchStatusPort, eventPublisher);
        return command -> tx.execute(status -> core.modifyPendingOrder(command));
    }
}