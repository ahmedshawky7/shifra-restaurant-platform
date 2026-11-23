package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.domain.model.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    List<UserEntity> findByOrganizationIdAndRole(Long organizationId, UserRole role);
    List<UserEntity> findByOrganizationIdAndRoleInOrderByIdDesc(Long organizationId, List<UserRole> roles);
    long countByOrganizationIdAndRoleIn(Long organizationId, List<UserRole> roles);
    Optional<UserEntity> findFirstByOrganizationIdAndOwnerTrue(Long organizationId);
}