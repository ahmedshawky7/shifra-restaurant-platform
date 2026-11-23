package com.restaurant.platform.application.service;

import com.restaurant.platform.application.port.in.ToggleMenuItemAvailabilityUseCase;
import com.restaurant.platform.application.port.out.MenuRepositoryPort;

public class ToggleMenuItemAvailabilityService implements ToggleMenuItemAvailabilityUseCase {

    private final MenuRepositoryPort menuRepo;

    public ToggleMenuItemAvailabilityService(MenuRepositoryPort menuRepo) {
        this.menuRepo = menuRepo;
    }

    @Override
    public void setAvailability(Long organizationId, Long branchId, Long menuItemId, boolean available) {
        menuRepo.setItemAvailability(organizationId, branchId, menuItemId, available);
    }
}