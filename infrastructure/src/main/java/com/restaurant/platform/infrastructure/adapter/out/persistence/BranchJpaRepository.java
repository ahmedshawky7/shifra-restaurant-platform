package com.restaurant.platform.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BranchJpaRepository extends JpaRepository<BranchEntity, Long> {
    List<BranchEntity> findByOrganizationId(Long organizationId);
    long countByOrganizationId(Long organizationId);
}