package com.restaurant.platform.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TableSessionJpaRepository extends JpaRepository<TableSessionEntity, Long> {
    Optional<TableSessionEntity> findFirstByTableIdAndOpenTrueOrderByOpenedAtDesc(Long tableId);
}