package com.restaurant.platform.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BranchMenuItemJpaRepository extends JpaRepository<BranchMenuItemEntity, Long> {
    Optional<BranchMenuItemEntity> findByBranchIdAndMenuItemId(Long branchId, Long menuItemId);
    List<BranchMenuItemEntity> findByBranchId(Long branchId);
}