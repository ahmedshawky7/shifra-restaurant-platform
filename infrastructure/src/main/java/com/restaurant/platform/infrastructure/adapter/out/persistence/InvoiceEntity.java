package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.domain.model.enums.PaymentMethod;
import com.restaurant.platform.domain.model.enums.PaymentStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices",
       indexes = {
               @Index(name = "idx_invoice_branch_date", columnList = "branch_id, paid_date"),
               @Index(name = "idx_invoice_branch_paid_at", columnList = "branch_id, paid_at")
       })
public class InvoiceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    private Long tableSessionId;
    private Long singleOrderId;

    @Column(nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceCharge;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(nullable = false)
    private Long cashierUserId;

    @Column(name = "paid_date", nullable = false)
    private LocalDate paidDate;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long v) { this.organizationId = v; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long v) { this.branchId = v; }
    public Long getTableSessionId() { return tableSessionId; }
    public void setTableSessionId(Long v) { this.tableSessionId = v; }
    public Long getSingleOrderId() { return singleOrderId; }
    public void setSingleOrderId(Long v) { this.singleOrderId = v; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String v) { this.invoiceNumber = v; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal v) { this.subtotal = v; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal v) { this.taxAmount = v; }
    public BigDecimal getServiceCharge() { return serviceCharge; }
    public void setServiceCharge(BigDecimal v) { this.serviceCharge = v; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal v) { this.discountAmount = v; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod v) { this.paymentMethod = v; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus v) { this.paymentStatus = v; }
    public Long getCashierUserId() { return cashierUserId; }
    public void setCashierUserId(Long v) { this.cashierUserId = v; }
    public LocalDate getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDate v) { this.paidDate = v; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime v) { this.paidAt = v; }
}