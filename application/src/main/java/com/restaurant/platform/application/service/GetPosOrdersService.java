package com.restaurant.platform.application.service;

import com.restaurant.platform.application.port.in.GetPosOrdersUseCase;
import com.restaurant.platform.application.port.out.BranchStatusPort;
import com.restaurant.platform.application.port.out.OrderRepositoryPort;
import com.restaurant.platform.application.port.out.TableRepositoryPort;
import com.restaurant.platform.application.result.OrderResult;
import com.restaurant.platform.domain.model.RestaurantTable;
import com.restaurant.platform.domain.model.enums.OrderStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GetPosOrdersService implements GetPosOrdersUseCase {

    private final OrderRepositoryPort orderRepo;
    private final TableRepositoryPort tableRepo;
    private final BranchStatusPort branchStatusPort;

    public GetPosOrdersService(OrderRepositoryPort orderRepo,
                               TableRepositoryPort tableRepo,
                               BranchStatusPort branchStatusPort) {
        this.orderRepo = orderRepo;
        this.tableRepo = tableRepo;
        this.branchStatusPort = branchStatusPort;
    }

    @Override
    public List<OrderResult> getActivePosOrders(Long branchId) {
        return fetchByStatuses(branchId, List.of(
                OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED));
    }

    @Override
    public List<OrderResult> getReadyForWaiterOrders(Long branchId) {
        return fetchByStatuses(branchId, List.of(OrderStatus.READY));
    }

    private List<OrderResult> fetchByStatuses(Long branchId, List<OrderStatus> statuses) {
        ZoneId zone = branchStatusPort.getBranchZoneId(branchId);
        LocalDate today = LocalDate.now(zone);

        Map<Long, String> tableNumbers = tableRepo.findByBranchId(branchId).stream()
                .collect(Collectors.toMap(RestaurantTable::getId, RestaurantTable::getTableNumber));

        return orderRepo.findByBranchAndStatuses(branchId, today, statuses)
                .stream()
                .map(o -> OrderResult.from(o, o.getTableId() != null ? tableNumbers.get(o.getTableId()) : null))
                .toList();
    }
}