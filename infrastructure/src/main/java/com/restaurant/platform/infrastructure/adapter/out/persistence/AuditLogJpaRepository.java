package com.restaurant.platform.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {
    Page<AuditLogEntity> findByBranchIdOrderByCreatedAtDesc(Long branchId, Pageable pageable);
}