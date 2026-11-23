package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.application.port.out.AuditLogPort;
import com.restaurant.platform.domain.model.AuditLog;
import com.restaurant.platform.infrastructure.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Component
public class AuditLogPersistenceAdapter implements AuditLogPort {

    private final AuditLogJpaRepository repo;

    public AuditLogPersistenceAdapter(AuditLogJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditLog entry) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setOrganizationId(entry.organizationId());
        entity.setBranchId(entry.branchId());

        Long userId = entry.userId();
        String userName = entry.userName();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomUserDetails cud) {
            if (userId == null) userId = cud.getUserId();
            if (userName == null || userName.isBlank()) userName = cud.getUsername();
        }

        entity.setUserId(userId);
        entity.setUserName(userName);
        entity.setActionType(entry.actionType());
        entity.setEntityType(entry.entityType());
        entity.setEntityId(entry.entityId());
        entity.setOldValue(truncate(entry.oldValue(), 1000));
        entity.setNewValue(truncate(entry.newValue(), 1000));
        entity.setIpAddress(entry.ipAddress() != null ? entry.ipAddress() : resolveRequestIp());
        entity.setCreatedAt(entry.createdAt() != null ? entry.createdAt() : LocalDateTime.now());
        repo.save(entity);
    }

    private String resolveRequestIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
            return req.getRemoteAddr();
        }
        return "SYSTEM";
    }

    private String truncate(String val, int max) {
        if (val == null) return null;
        return val.length() <= max ? val : val.substring(0, max);
    }
}