package com.restaurant.platform.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ItemVariantJpaRepository extends JpaRepository<ItemVariantEntity, Long> {
    List<ItemVariantEntity> findByMenuItemIdAndActiveTrue(Long menuItemId);
    List<ItemVariantEntity> findByMenuItemIdInAndActiveTrue(List<Long> menuItemIds);
}