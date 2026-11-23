package com.restaurant.platform.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RestaurantTableJpaRepository extends JpaRepository<RestaurantTableEntity, Long> {
    Optional<RestaurantTableEntity> findByQrToken(String qrToken);
    List<RestaurantTableEntity> findByBranchIdOrderByIdAsc(Long branchId);
    boolean existsByBranchIdAndTableNumber(Long branchId, String tableNumber);
}