package com.restaurant.platform.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_items")
public class OrderItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(nullable = false)
    private Long menuItemId;

    @Column(nullable = false)
    private String itemNameSnapshot;

    private Long variantId;
    private String variantNameSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    private String notes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @OrderBy("id ASC")
    private List<OrderItemAddonEntity> addons = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OrderEntity getOrder() { return order; }
    public void setOrder(OrderEntity order) { this.order = order; }
    public Long getMenuItemId() { return menuItemId; }
    public void setMenuItemId(Long menuItemId) { this.menuItemId = menuItemId; }
    public String getItemNameSnapshot() { return itemNameSnapshot; }
    public void setItemNameSnapshot(String itemNameSnapshot) { this.itemNameSnapshot = itemNameSnapshot; }
    public Long getVariantId() { return variantId; }
    public void setVariantId(Long variantId) { this.variantId = variantId; }
    public String getVariantNameSnapshot() { return variantNameSnapshot; }
    public void setVariantNameSnapshot(String variantNameSnapshot) { this.variantNameSnapshot = variantNameSnapshot; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
    public List<OrderItemAddonEntity> getAddons() { return addons; }
    public void setAddons(List<OrderItemAddonEntity> addons) { this.addons = addons; }
}