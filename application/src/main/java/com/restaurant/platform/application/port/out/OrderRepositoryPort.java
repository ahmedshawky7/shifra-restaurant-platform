package com.restaurant.platform.application.port.out;

import com.restaurant.platform.domain.model.Order;
import com.restaurant.platform.domain.model.enums.OrderStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {
    Order save(Order order);
    Optional<Order> findById(Long id);
    Optional<Order> findByTrackingToken(String trackingToken);
    int nextOrderNumber(Long branchId, LocalDate date);
    long countByOrganizationInMonth(Long organizationId, int year, int month);
    List<Order> findByBranchAndStatuses(Long branchId, LocalDate date, List<OrderStatus> statuses);
    List<Order> findUnpaidOrdersBySessionId(Long tableSessionId);
    List<Order> findByBranchAndDate(Long branchId, LocalDate date);
}