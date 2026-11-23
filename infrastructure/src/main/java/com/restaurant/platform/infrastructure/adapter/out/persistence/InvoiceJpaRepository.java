package com.restaurant.platform.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceJpaRepository extends JpaRepository<InvoiceEntity, Long> {
    long countByBranchIdAndPaidDate(Long branchId, LocalDate paidDate);
    List<InvoiceEntity> findByBranchIdAndPaidDateOrderByPaidAtDesc(Long branchId, LocalDate paidDate);
    Page<InvoiceEntity> findByBranchIdOrderByPaidAtDesc(Long branchId, Pageable pageable);
}