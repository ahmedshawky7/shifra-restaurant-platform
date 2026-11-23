package com.restaurant.platform.domain.model;

import com.restaurant.platform.domain.model.enums.TableStatus;
import java.util.Objects;

public class RestaurantTable {
    private final Long id;
    private final Long organizationId;
    private final Long branchId;
    private final String tableNumber;
    private final String qrToken;
    private final int capacity;
    private TableStatus status;
    private boolean active;

    public RestaurantTable(Long id, Long organizationId, Long branchId, String tableNumber,
                           String qrToken, int capacity, TableStatus status, boolean active) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.branchId = Objects.requireNonNull(branchId, "branchId");
        this.tableNumber = Objects.requireNonNull(tableNumber, "tableNumber");
        this.qrToken = Objects.requireNonNull(qrToken, "qrToken");
        this.capacity = capacity > 0 ? capacity : 4;
        this.status = status != null ? status : TableStatus.AVAILABLE;
        this.active = active;
    }

    public void occupy() {
        if (!active) {
            throw new IllegalStateException("هذه الطاولة غير مفعلة حالياً.");
        }
        this.status = TableStatus.OCCUPIED;
    }

    public void release() {
        this.status = TableStatus.AVAILABLE;
    }

    public void reserve() {
        if (status == TableStatus.OCCUPIED) {
            throw new IllegalStateException("الطاولة مشغولة حالياً ولا يمكن حجزها.");
        }
        this.status = TableStatus.RESERVED;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getBranchId() { return branchId; }
    public String getTableNumber() { return tableNumber; }
    public String getQrToken() { return qrToken; }
    public int getCapacity() { return capacity; }
    public TableStatus getStatus() { return status; }
    public boolean isActive() { return active; }
}