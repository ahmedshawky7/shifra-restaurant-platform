package com.restaurant.platform.domain.model;

import com.restaurant.platform.domain.model.enums.PaymentMethod;
import com.restaurant.platform.domain.model.enums.PaymentStatus;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Objects;

public class Invoice {
    private final Long id;
    private final Long organizationId;
    private final Long branchId;
    private final Long tableSessionId;
    private final Long singleOrderId;
    private final String invoiceNumber;
    private final BigDecimal subtotal;
    private final BigDecimal taxAmount;
    private final BigDecimal serviceCharge;
    private final BigDecimal discountAmount;
    private final BigDecimal totalAmount;
    private final PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private final Long cashierUserId;
    private final LocalDateTime paidAt;

    public Invoice(Long id, Long organizationId, Long branchId, Long tableSessionId, Long singleOrderId,
                   String invoiceNumber, BigDecimal subtotal, BigDecimal taxAmount, BigDecimal serviceCharge,
                   BigDecimal discountAmount, BigDecimal totalAmount, PaymentMethod paymentMethod,
                   PaymentStatus paymentStatus, Long cashierUserId, LocalDateTime paidAt) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.branchId = Objects.requireNonNull(branchId, "branchId");
        this.tableSessionId = tableSessionId;
        this.singleOrderId = singleOrderId;
        this.invoiceNumber = Objects.requireNonNull(invoiceNumber, "invoiceNumber");
        this.subtotal = scale2(subtotal);
        this.taxAmount = scale2(taxAmount);
        this.serviceCharge = scale2(serviceCharge);
        this.discountAmount = scale2(discountAmount);
        this.totalAmount = scale2(totalAmount);
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod");
        this.paymentStatus = Objects.requireNonNull(paymentStatus, "paymentStatus");
        this.cashierUserId = Objects.requireNonNull(cashierUserId, "cashierUserId");
        this.paidAt = Objects.requireNonNull(paidAt, "paidAt");
    }

    public static Invoice issue(Long organizationId, Long branchId, Long tableSessionId, Long singleOrderId,
                                String invoiceNumber, BigDecimal subtotal, BigDecimal taxAmount,
                                BigDecimal serviceCharge, BigDecimal discountAmount,
                                PaymentMethod paymentMethod, Long cashierUserId, LocalDateTime now) {
        BigDecimal sub = scale2(subtotal);
        BigDecimal tax = scale2(taxAmount);
        BigDecimal svc = scale2(serviceCharge);
        BigDecimal disc = scale2(discountAmount);

        if (disc.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("قيمة الخصم غير صالحة");
        }
        BigDecimal gross = sub.add(tax).add(svc).setScale(2, RoundingMode.HALF_UP);
        if (disc.compareTo(gross) > 0) {
            throw new IllegalArgumentException("الخصم أكبر من قيمة الفاتورة");
        }
        BigDecimal total = gross.subtract(disc).setScale(2, RoundingMode.HALF_UP);
        return new Invoice(null, organizationId, branchId, tableSessionId, singleOrderId,
                invoiceNumber, sub, tax, svc, disc, total,
                paymentMethod, PaymentStatus.PAID, cashierUserId, now);
    }

    /**
     * توليد رمز QR الضريبي بصيغة Base64 TLV (متوافق مع متطلبات الفوترة الإلكترونية ZATCA Phase 1).
     */
    public String generateTaxQrBase64(String sellerName, String vatRegistrationNumber) {
        String safeSeller = (sellerName != null && !sellerName.isBlank()) ? sellerName : "Restaurant";
        String safeVat = (vatRegistrationNumber != null && !vatRegistrationNumber.isBlank())
                ? vatRegistrationNumber : "000000000000000";
        String timestamp = paidAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTlv(out, 1, safeSeller);
        writeTlv(out, 2, safeVat);
        writeTlv(out, 3, timestamp);
        writeTlv(out, 4, totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        writeTlv(out, 5, taxAmount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static void writeTlv(ByteArrayOutputStream out, int tag, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(bytes.length, 255);
        out.write(tag & 0xFF);
        out.write(len & 0xFF);
        out.write(bytes, 0, len);
    }

    private static BigDecimal scale2(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public void refund() {
        if (paymentStatus != PaymentStatus.PAID) {
            throw new IllegalStateException("لا يمكن استرداد فاتورة غير مدفوعة");
        }
        this.paymentStatus = PaymentStatus.REFUNDED;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getBranchId() { return branchId; }
    public Long getTableSessionId() { return tableSessionId; }
    public Long getSingleOrderId() { return singleOrderId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getServiceCharge() { return serviceCharge; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public Long getCashierUserId() { return cashierUserId; }
    public LocalDateTime getPaidAt() { return paidAt; }
}