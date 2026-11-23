package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.domain.model.enums.TableStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "restaurant_tables",
       uniqueConstraints = @UniqueConstraint(columnNames = {"branchId", "tableNumber"}))
public class RestaurantTableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long organizationId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private String tableNumber;

    @Column(nullable = false, unique = true, length = 64)
    private String qrToken;

    private int capacity = 4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TableStatus status = TableStatus.AVAILABLE;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getTableNumber() { return tableNumber; }
    public void setTableNumber(String tableNumber) { this.tableNumber = tableNumber; }
    public String getQrToken() { return qrToken; }
    public void setQrToken(String qrToken) { this.qrToken = qrToken; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public TableStatus getStatus() { return status; }
    public void setStatus(TableStatus status) { this.status = status; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}