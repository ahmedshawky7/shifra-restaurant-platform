package com.restaurant.platform.domain.model;

public record MenuCategory(
        Long id,
        Long organizationId,
        Long branchId,
        String name,
        String description,
        String imageUrl,
        int sortOrder,
        boolean active
) {}