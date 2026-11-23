package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.application.port.out.InvoiceRepositoryPort;
import com.restaurant.platform.domain.model.Invoice;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Component
public class InvoicePersistenceAdapter implements InvoiceRepositoryPort {

    private final InvoiceJpaRepository jpa;

    public InvoicePersistenceAdapter(InvoiceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Invoice save(Invoice i) {
        InvoiceEntity e = new InvoiceEntity();
        e.setId(i.getId());
        e.setOrganizationId(i.getOrganizationId());
        e.setBranchId(i.getBranchId());
        e.setTableSessionId(i.getTableSessionId());
        e.setSingleOrderId(i.getSingleOrderId());
        e.setInvoiceNumber(i.getInvoiceNumber());
        e.setSubtotal(i.getSubtotal());
        e.setTaxAmount(i.getTaxAmount());
        e.setServiceCharge(i.getServiceCharge());
        e.setDiscountAmount(i.getDiscountAmount());
        e.setTotalAmount(i.getTotalAmount());
        e.setPaymentMethod(i.getPaymentMethod());
        e.setPaymentStatus(i.getPaymentStatus());
        e.setCashierUserId(i.getCashierUserId());
        e.setPaidDate(i.getPaidAt().toLocalDate());
        e.setPaidAt(i.getPaidAt());
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<Invoice> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public String nextInvoiceNumber(Long branchId, LocalDate date) {
        long count = jpa.countByBranchIdAndPaidDate(branchId, date);
        return "INV-" + date.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + branchId + "-" + String.format("%04d", count + 1);
    }

    private Invoice toDomain(InvoiceEntity e) {
        return new Invoice(
                e.getId(), e.getOrganizationId(), e.getBranchId(), e.getTableSessionId(),
                e.getSingleOrderId(), e.getInvoiceNumber(), e.getSubtotal(), e.getTaxAmount(),
                e.getServiceCharge(), e.getDiscountAmount(), e.getTotalAmount(),
                e.getPaymentMethod(), e.getPaymentStatus(), e.getCashierUserId(), e.getPaidAt()
        );
    }
}