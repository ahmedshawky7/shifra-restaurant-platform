package com.restaurant.platform.domain.model;

import java.time.LocalDateTime;

public record AuditLog(
        Long id,
        Long organizationId,
        Long branchId,
        Long userId,
        String userName,
        String actionType,
        String entityType,
        Long entityId,
        String oldValue,
        String newValue,
        String ipAddress,
        LocalDateTime createdAt
) {
    public static AuditLog of(Long organizationId, Long branchId, Long userId, String userName,
                              String actionType, String entityType, Long entityId,
                              String oldValue, String newValue, LocalDateTime now) {
        return new AuditLog(
                null, organizationId, branchId, userId, userName,
                actionType, entityType, entityId, oldValue, newValue, null, now
        );
    }
}