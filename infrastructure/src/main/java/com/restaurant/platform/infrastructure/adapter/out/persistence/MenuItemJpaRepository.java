package com.restaurant.platform.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MenuItemJpaRepository extends JpaRepository<MenuItemEntity, Long> {
    List<MenuItemEntity> findByOrganizationIdAndActiveTrueOrderByIdAsc(Long organizationId);
    List<MenuItemEntity> findByOrganizationIdOrderByIdAsc(Long organizationId);
}