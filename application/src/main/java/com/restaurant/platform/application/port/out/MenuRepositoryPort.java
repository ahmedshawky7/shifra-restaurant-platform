package com.restaurant.platform.application.port.out;

import com.restaurant.platform.domain.model.MenuCategory;
import com.restaurant.platform.domain.model.MenuItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MenuRepositoryPort {
    List<MenuCategory> findActiveCategoriesByOrgAndBranch(Long organizationId, Long branchId);
    List<MenuItem> findActiveItemsByOrg(Long organizationId);
    Optional<MenuItem> findMenuItemById(Long menuItemId);
    boolean isItemAvailableInBranch(Long branchId, Long menuItemId);
    Optional<BigDecimal> getBranchPriceOverride(Long branchId, Long menuItemId);
    void setItemAvailability(Long organizationId, Long branchId, Long menuItemId, boolean available);
}