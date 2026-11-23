package com.restaurant.platform.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class TableSession {
    private final Long id;
    private final Long organizationId;
    private final Long branchId;
    private final Long tableId;
    private final String sessionToken;
    private boolean open;
    private final LocalDateTime openedAt;
    private LocalDateTime closedAt;

    public TableSession(Long id, Long organizationId, Long branchId, Long tableId,
                        String sessionToken, boolean open, LocalDateTime openedAt, LocalDateTime closedAt) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.branchId = Objects.requireNonNull(branchId, "branchId");
        this.tableId = Objects.requireNonNull(tableId, "tableId");
        this.sessionToken = Objects.requireNonNull(sessionToken, "sessionToken");
        this.open = open;
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        this.closedAt = closedAt;
    }

    public static TableSession openNew(Long organizationId, Long branchId, Long tableId,
                                       String sessionToken, LocalDateTime now) {
        return new TableSession(null, organizationId, branchId, tableId, sessionToken, true, now, null);
    }

    public void close(LocalDateTime now) {
        if (!open) {
            throw new IllegalStateException("جلسة الطاولة مغلقة بالفعل.");
        }
        this.open = false;
        this.closedAt = now;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getBranchId() { return branchId; }
    public Long getTableId() { return tableId; }
    public String getSessionToken() { return sessionToken; }
    public boolean isOpen() { return open; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
}