package com.restaurant.platform.application.result;

import com.restaurant.platform.domain.model.ItemAddon;
import com.restaurant.platform.domain.model.ItemVariant;

import java.math.BigDecimal;
import java.util.List;

public record PublicMenuResult(
        Long organizationId,
        String organizationName,
        Long branchId,
        String branchName,
        boolean branchOpen,
        String closureMessage,
        Long tableId,
        String tableNumber,
        BigDecimal taxRate,
        BigDecimal serviceChargeRate,
        List<CategoryMenuDto> categories
) {
    public record CategoryMenuDto(
            Long id,
            String name,
            String description,
            String imageUrl,
            List<MenuItemDto> items
    ) {}

    public record MenuItemDto(
            Long id,
            String name,
            String description,
            BigDecimal effectivePrice,
            String imageUrl,
            int prepTimeMinutes,
            boolean available,
            List<ItemVariant> variants,
            List<ItemAddon> addons
    ) {}
}