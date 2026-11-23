package com.restaurant.platform.domain.model;

import java.math.BigDecimal;

public record ItemVariant(
        Long id,
        Long menuItemId,
        String name,
        BigDecimal price,
        boolean isDefault,
        boolean active
) {}