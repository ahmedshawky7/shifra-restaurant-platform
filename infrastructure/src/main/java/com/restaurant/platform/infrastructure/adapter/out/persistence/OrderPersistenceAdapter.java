package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.application.port.out.OrderRepositoryPort;
import com.restaurant.platform.domain.model.Order;
import com.restaurant.platform.domain.model.enums.OrderStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class OrderPersistenceAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository jpa;
    private final OrderPersistenceMapper mapper;

    public OrderPersistenceAdapter(OrderJpaRepository jpa, OrderPersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Order save(Order order) {
        return mapper.toDomain(jpa.save(mapper.toEntity(order)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByTrackingToken(String trackingToken) {
        return jpa.findByTrackingToken(trackingToken).map(mapper::toDomain);
    }

    @Override
    public int nextOrderNumber(Long branchId, LocalDate date) {
        return jpa.maxOrderNumber(branchId, date) + 1;
    }

    @Override
    public long countByOrganizationInMonth(Long organizationId, int year, int month) {
        return jpa.countByOrgInMonth(organizationId, year, month);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByBranchAndStatuses(Long branchId, LocalDate date, List<OrderStatus> statuses) {
        return jpa.findByBranchIdAndOrderDateAndStatusInOrderByOrderNumberAsc(branchId, date, statuses)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findUnpaidOrdersBySessionId(Long tableSessionId) {
        return jpa.findByTableSessionIdOrderByCreatedAtAsc(tableSessionId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByBranchAndDate(Long branchId, LocalDate date) {
        return jpa.findByBranchIdAndOrderDateOrderByCreatedAtDesc(branchId, date)
                .stream().map(mapper::toDomain).toList();
    }
}