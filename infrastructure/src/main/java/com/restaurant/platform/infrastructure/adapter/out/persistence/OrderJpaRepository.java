package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.domain.model.enums.OrderSource;
import com.restaurant.platform.domain.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByTrackingToken(String trackingToken);

    @Query("SELECT COALESCE(MAX(o.orderNumber), 0) FROM OrderEntity o WHERE o.branchId = :branchId AND o.orderDate = :date")
    int maxOrderNumber(@Param("branchId") Long branchId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.organizationId = :orgId " +
           "AND YEAR(o.orderDate) = :year AND MONTH(o.orderDate) = :month")
    long countByOrgInMonth(@Param("orgId") Long orgId, @Param("year") int year, @Param("month") int month);

    List<OrderEntity> findByBranchIdAndOrderDateAndStatusInOrderByOrderNumberAsc(
            Long branchId, LocalDate orderDate, List<OrderStatus> statuses);

    List<OrderEntity> findByTableSessionIdOrderByCreatedAtAsc(Long tableSessionId);
    List<OrderEntity> findByBranchIdAndOrderDateOrderByCreatedAtDesc(Long branchId, LocalDate orderDate);

    Page<OrderEntity> findByBranchIdOrderByCreatedAtDesc(Long branchId, Pageable pageable);
    Page<OrderEntity> findByBranchIdAndStatusOrderByCreatedAtDesc(Long branchId, OrderStatus status, Pageable pageable);

    long countByBranchIdAndOrderDate(Long branchId, LocalDate orderDate);
    long countByBranchIdAndOrderDateAndOrderSource(Long branchId, LocalDate orderDate, OrderSource source);

    @Query("SELECT o.orderDate, COUNT(o), COALESCE(SUM(o.totalAmount), 0) FROM OrderEntity o " +
           "WHERE o.branchId = :branchId AND o.status <> 'CANCELLED' " +
           "AND o.orderDate BETWEEN :start AND :end GROUP BY o.orderDate ORDER BY o.orderDate")
    List<Object[]> countAndRevenueDailyBetween(@Param("branchId") Long branchId,
                                               @Param("start") LocalDate start,
                                               @Param("end") LocalDate end);
}