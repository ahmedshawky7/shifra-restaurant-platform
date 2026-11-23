package com.restaurant.platform.application.port.in;

public interface ToggleMenuItemAvailabilityUseCase {
    void setAvailability(Long organizationId, Long branchId, Long menuItemId, boolean available);
}