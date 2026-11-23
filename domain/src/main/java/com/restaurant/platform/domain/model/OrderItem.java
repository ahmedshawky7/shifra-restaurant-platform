package com.restaurant.platform.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public class OrderItem {
    private final Long id;
    private final Long menuItemId;
    private final String itemNameSnapshot;
    private final Long variantId;
    private final String variantNameSnapshot;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final String notes;
    private final List<OrderItemAddon> addons;
    private final BigDecimal lineTotal;

    public OrderItem(Long id, Long menuItemId, String itemNameSnapshot, Long variantId,
                     String variantNameSnapshot, int quantity, BigDecimal unitPrice,
                     String notes, List<OrderItemAddon> addons) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.id = id;
        this.menuItemId = Objects.requireNonNull(menuItemId, "menuItemId");
        this.itemNameSnapshot = Objects.requireNonNull(itemNameSnapshot, "itemNameSnapshot");
        this.variantId = variantId;
        this.variantNameSnapshot = variantNameSnapshot;
        this.quantity = quantity;
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice").setScale(2, RoundingMode.HALF_UP);
        this.notes = notes;
        this.addons = addons != null ? List.copyOf(addons) : List.of();

        BigDecimal addonsTotal = this.addons.stream()
                .map(OrderItemAddon::addonPriceSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        this.lineTotal = this.unitPrice.add(addonsTotal)
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public Long getMenuItemId() { return menuItemId; }
    public String getItemNameSnapshot() { return itemNameSnapshot; }
    public Long getVariantId() { return variantId; }
    public String getVariantNameSnapshot() { return variantNameSnapshot; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getNotes() { return notes; }
    public List<OrderItemAddon> getAddons() { return addons; }
    public BigDecimal getLineTotal() { return lineTotal; }
}