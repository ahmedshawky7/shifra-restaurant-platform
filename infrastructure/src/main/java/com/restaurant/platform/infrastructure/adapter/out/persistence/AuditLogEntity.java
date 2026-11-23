package com.restaurant.platform.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Immutable
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_branch_created", columnList = "branch_id, created_at"),
        @Index(name = "idx_audit_org_action", columnList = "organization_id, action_type, created_at")
    }
)
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", updatable = false)
    private Long organizationId;

    @Column(name = "branch_id", updatable = false)
    private Long branchId;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "user_name", length = 120, updatable = false)
    private String userName;

    @Column(name = "action_type", nullable = false, length = 64, updatable = false)
    private String actionType;

    @Column(name = "entity_type", nullable = false, length = 64, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Column(name = "old_value", length = 1000, updatable = false)
    private String oldValue;

    @Column(name = "new_value", length = 1000, updatable = false)
    private String newValue;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PreUpdate
    @PreRemove
    protected void preventModification() {
        throw new UnsupportedOperationException("سجل الرقابة المالي غير قابل للتعديل أو الحذف.");
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}