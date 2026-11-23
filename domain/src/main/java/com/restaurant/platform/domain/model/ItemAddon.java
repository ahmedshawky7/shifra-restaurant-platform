package com.restaurant.platform.domain.model;

import java.math.BigDecimal;

public record ItemAddon(
        Long id,
        Long menuItemId,
        String name,
        BigDecimal price,
        boolean available
) {}