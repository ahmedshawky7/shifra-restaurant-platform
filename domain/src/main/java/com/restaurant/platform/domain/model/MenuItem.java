package com.restaurant.platform.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record MenuItem(
        Long id,
        Long organizationId,
        Long categoryId,
        String name,
        String description,
        BigDecimal basePrice,
        String imageUrl,
        int prepTimeMinutes,
        boolean available,
        boolean active,
        List<ItemVariant> variants,
        List<ItemAddon> addons
) {}