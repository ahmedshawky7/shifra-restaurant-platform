package com.restaurant.platform.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_item_addons")
public class OrderItemAddonEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItemEntity orderItem;

    @Column(nullable = false)
    private Long addonId;

    @Column(nullable = false)
    private String addonNameSnapshot;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal addonPriceSnapshot;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OrderItemEntity getOrderItem() { return orderItem; }
    public void setOrderItem(OrderItemEntity orderItem) { this.orderItem = orderItem; }
    public Long getAddonId() { return addonId; }
    public void setAddonId(Long addonId) { this.addonId = addonId; }
    public String getAddonNameSnapshot() { return addonNameSnapshot; }
    public void setAddonNameSnapshot(String addonNameSnapshot) { this.addonNameSnapshot = addonNameSnapshot; }
    public BigDecimal getAddonPriceSnapshot() { return addonPriceSnapshot; }
    public void setAddonPriceSnapshot(BigDecimal addonPriceSnapshot) { this.addonPriceSnapshot = addonPriceSnapshot; }
}