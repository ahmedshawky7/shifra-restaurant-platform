package com.restaurant.platform.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MenuCategoryJpaRepository extends JpaRepository<MenuCategoryEntity, Long> {
    @Query("SELECT c FROM MenuCategoryEntity c WHERE c.organizationId = :orgId AND c.active = true " +
           "AND (c.branchId IS NULL OR c.branchId = :branchId) ORDER BY c.sortOrder ASC, c.id ASC")
    List<MenuCategoryEntity> findActiveForBranch(@Param("orgId") Long orgId, @Param("branchId") Long branchId);

    List<MenuCategoryEntity> findByOrganizationIdOrderBySortOrderAscIdAsc(Long organizationId);
}