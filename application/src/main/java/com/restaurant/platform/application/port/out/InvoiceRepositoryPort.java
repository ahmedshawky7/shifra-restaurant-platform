package com.restaurant.platform.application.port.out;

import com.restaurant.platform.domain.model.Invoice;

import java.time.LocalDate;
import java.util.Optional;

public interface InvoiceRepositoryPort {
    Invoice save(Invoice invoice);
    Optional<Invoice> findById(Long id);
    String nextInvoiceNumber(Long branchId, LocalDate date);
}