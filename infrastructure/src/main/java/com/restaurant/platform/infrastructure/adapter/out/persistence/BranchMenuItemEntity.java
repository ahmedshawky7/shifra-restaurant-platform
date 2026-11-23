package com.restaurant.platform.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "branch_menu_items",
       uniqueConstraints = @UniqueConstraint(columnNames = {"branchId", "menuItemId"}))
public class BranchMenuItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long organizationId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long menuItemId;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean available = true;

    @Column(precision = 10, scale = 2)
    private BigDecimal priceOverride;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Long getMenuItemId() { return menuItemId; }
    public void setMenuItemId(Long menuItemId) { this.menuItemId = menuItemId; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public BigDecimal getPriceOverride() { return priceOverride; }
    public void setPriceOverride(BigDecimal priceOverride) { this.priceOverride = priceOverride; }
}