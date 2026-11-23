package com.restaurant.platform.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ItemAddonJpaRepository extends JpaRepository<ItemAddonEntity, Long> {
    List<ItemAddonEntity> findByMenuItemId(Long menuItemId);
    List<ItemAddonEntity> findByMenuItemIdIn(List<Long> menuItemIds);
}