package com.restaurant.platform.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderItemAddon(
        Long id,
        Long addonId,
        String addonNameSnapshot,
        BigDecimal addonPriceSnapshot
) {
    public OrderItemAddon {
        Objects.requireNonNull(addonId, "addonId");
        Objects.requireNonNull(addonNameSnapshot, "addonNameSnapshot");
        addonPriceSnapshot = addonPriceSnapshot != null ? addonPriceSnapshot : BigDecimal.ZERO;
    }
}