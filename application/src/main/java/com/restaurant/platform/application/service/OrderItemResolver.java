package com.restaurant.platform.application.service;

import com.restaurant.platform.application.command.PlaceOrderCommand;
import com.restaurant.platform.application.port.out.MenuRepositoryPort;
import com.restaurant.platform.domain.model.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class OrderItemResolver {

    private final MenuRepositoryPort menuRepo;

    OrderItemResolver(MenuRepositoryPort menuRepo) {
        this.menuRepo = menuRepo;
    }

    List<OrderItem> resolve(Long orgId, Long branchId, List<PlaceOrderCommand.OrderItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("يجب اختيار صنف واحد على الأقل");
        }

        List<OrderItem> items = new ArrayList<>();
        for (var req : requests) {
            MenuItem menuItem = menuRepo.findMenuItemById(req.menuItemId())
                    .orElseThrow(() -> new IllegalArgumentException("الصنف غير موجود: " + req.menuItemId()));

            if (!menuItem.organizationId().equals(orgId) || !menuItem.active()) {
                throw new IllegalArgumentException("الصنف غير متاح في هذا المطعم: " + menuItem.name());
            }
            if (!menuItem.available() || !menuRepo.isItemAvailableInBranch(branchId, menuItem.id())) {
                throw new IllegalStateException("الصنف غير متوفر حالياً: " + menuItem.name());
            }

            BigDecimal unitPrice = menuRepo.getBranchPriceOverride(branchId, menuItem.id())
                    .orElse(menuItem.basePrice());
            String variantName = null;
            Long variantId = req.variantId();

            if (variantId != null) {
                ItemVariant variant = menuItem.variants().stream()
                        .filter(v -> v.id().equals(variantId) && v.active())
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("الحجم المختار غير صالح للصنف: " + menuItem.name()));
                unitPrice = variant.price();
                variantName = variant.name();
            }

            List<OrderItemAddon> resolvedAddons = new ArrayList<>();
            for (Long addonId : req.addonIds()) {
                ItemAddon addon = menuItem.addons().stream()
                        .filter(a -> a.id().equals(addonId) && a.available())
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("الإضافة غير متاحة للصنف: " + menuItem.name()));
                resolvedAddons.add(new OrderItemAddon(null, addon.id(), addon.name(), addon.price()));
            }

            items.add(new OrderItem(
                    null,
                    menuItem.id(),
                    menuItem.name(),
                    variantId,
                    variantName,
                    req.quantity(),
                    unitPrice,
                    req.notes(),
                    resolvedAddons
            ));
        }
        return items;
    }
}