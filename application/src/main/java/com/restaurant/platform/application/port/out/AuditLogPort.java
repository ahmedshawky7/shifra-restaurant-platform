package com.restaurant.platform.application.port.out;

import com.restaurant.platform.domain.model.AuditLog;

public interface AuditLogPort {
    void record(AuditLog entry);
}