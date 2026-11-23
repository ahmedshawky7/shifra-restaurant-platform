package com.restaurant.platform.application.service;

import com.restaurant.platform.application.command.PlaceOrderCommand;
import com.restaurant.platform.application.port.in.PlaceOrderUseCase;
import com.restaurant.platform.application.port.out.*;
import com.restaurant.platform.application.result.OrderResult;
import com.restaurant.platform.domain.model.*;
import com.restaurant.platform.domain.model.enums.OrderSource;
import com.restaurant.platform.domain.model.enums.OrderType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public class PlaceOrderService implements PlaceOrderUseCase {

    private final OrderRepositoryPort orderRepo;
    private final OrderItemResolver itemResolver;
    private final TableRepositoryPort tableRepo;
    private final BranchStatusPort branchStatusPort;
    private final OrganizationRepositoryPort organizationRepo;
    private final OrderEventPublisherPort eventPublisher;

    public PlaceOrderService(OrderRepositoryPort orderRepo,
                             MenuRepositoryPort menuRepo,
                             TableRepositoryPort tableRepo,
                             BranchStatusPort branchStatusPort,
                             OrganizationRepositoryPort organizationRepo,
                             OrderEventPublisherPort eventPublisher) {
        this.orderRepo = orderRepo;
        this.itemResolver = new OrderItemResolver(menuRepo);
        this.tableRepo = tableRepo;
        this.branchStatusPort = branchStatusPort;
        this.organizationRepo = organizationRepo;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OrderResult placeOrder(PlaceOrderCommand command) {
        RestaurantTable table = null;
        Long resolvedBranchId = command.branchId();

        if (command.tableQrToken() != null && !command.tableQrToken().isBlank()) {
            table = tableRepo.findByQrToken(command.tableQrToken())
                    .orElseThrow(() -> new IllegalArgumentException("رمز الطاولة غير صالح"));
            resolvedBranchId = table.getBranchId();
        } else if (command.tableId() != null) {
            table = tableRepo.findById(command.tableId())
                    .orElseThrow(() -> new IllegalArgumentException("الطاولة غير موجودة"));
            resolvedBranchId = table.getBranchId();
        }

        if (resolvedBranchId == null) {
            throw new IllegalArgumentException("يجب تحديد الفرع أو الطاولة");
        }

        if (!branchStatusPort.isBranchOpen(resolvedBranchId)) {
            throw new IllegalStateException("الفرع مغلق اليوم: " + branchStatusPort.getClosureReason(resolvedBranchId));
        }
        if (command.orderSource() == OrderSource.QR_CUSTOMER && !branchStatusPort.isWithinWorkingHours(resolvedBranchId)) {
            throw new IllegalStateException(branchStatusPort.getClosedHoursMessage(resolvedBranchId));
        }

        Long orgId = branchStatusPort.getOrganizationIdByBranchId(resolvedBranchId);
        Organization org = organizationRepo.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("المطعم غير موجود"));

        ZoneId zone = branchStatusPort.getBranchZoneId(resolvedBranchId);
        Clock branchClock = Clock.system(zone);
        LocalDateTime now = LocalDateTime.now(branchClock);
        LocalDate today = now.toLocalDate();

        long monthlyOrders = orderRepo.countByOrganizationInMonth(orgId, today.getYear(), today.getMonthValue());
        org.ensureCanCreateOrderThisMonth(monthlyOrders, branchClock);

        Long tableSessionId = null;
        String tableNumber = null;

        if (command.orderType() == OrderType.DINE_IN) {
            if (table == null) {
                throw new IllegalArgumentException("طلب الصالة يتطلب تحديد الطاولة");
            }
            if (!table.getBranchId().equals(resolvedBranchId)) {
                throw new IllegalArgumentException("الطاولة لا تتبع هذا الفرع");
            }
            tableNumber = table.getTableNumber();
            table.occupy();
            tableRepo.save(table);

            final RestaurantTable boundTable = table;
            TableSession session = tableRepo.findOpenSessionByTableId(boundTable.getId())
                    .orElseGet(() -> tableRepo.saveSession(TableSession.openNew(
                            orgId, boundTable.getBranchId(), boundTable.getId(),
                            UUID.randomUUID().toString(), now
                    )));
            tableSessionId = session.getId();
        }

        List<OrderItem> resolvedItems = itemResolver.resolve(orgId, resolvedBranchId, command.items());
        int orderNumber = orderRepo.nextOrderNumber(resolvedBranchId, today);
        String trackingToken = UUID.randomUUID().toString().replace("-", "");

        BigDecimal taxRate = branchStatusPort.getTaxRate(resolvedBranchId);
        BigDecimal serviceRate = branchStatusPort.getServiceChargeRate(resolvedBranchId);

        Order order = Order.place(
                orgId,
                resolvedBranchId,
                table != null ? table.getId() : null,
                tableSessionId,
                orderNumber,
                trackingToken,
                command.orderType(),
                command.orderSource(),
                command.createdByUserId(),
                command.customerName(),
                command.customerPhone(),
                command.deliveryAddress(),
                command.notes(),
                resolvedItems,
                taxRate,
                serviceRate,
                now
        );

        Order saved = orderRepo.save(order);
        OrderEventSnapshot event = OrderEventFactory.fromOrder("ORDER_CREATED", saved, tableNumber, now);

        eventPublisher.publishToKds(event);
        eventPublisher.publishToPos(event);
        eventPublisher.publishToCustomerTracking(event);
        eventPublisher.publishToPickupScreen(event);

        return OrderResult.from(saved, tableNumber);
    }
}