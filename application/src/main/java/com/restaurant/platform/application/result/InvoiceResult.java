package com.restaurant.platform.application.result;

import com.restaurant.platform.domain.model.Invoice;
import com.restaurant.platform.domain.model.enums.PaymentMethod;
import com.restaurant.platform.domain.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvoiceResult(
        Long invoiceId,
        String invoiceNumber,
        Long branchId,
        Long tableSessionId,
        Long singleOrderId,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal serviceCharge,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        LocalDateTime paidAt,
        String taxQrBase64
) {
    public static InvoiceResult from(Invoice invoice) {
        return from(invoice, "مطعم", "000000000000000");
    }

    public static InvoiceResult from(Invoice invoice, String sellerName, String vatNumber) {
        return new InvoiceResult(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getBranchId(),
                invoice.getTableSessionId(),
                invoice.getSingleOrderId(),
                invoice.getSubtotal(),
                invoice.getTaxAmount(),
                invoice.getServiceCharge(),
                invoice.getDiscountAmount(),
                invoice.getTotalAmount(),
                invoice.getPaymentMethod(),
                invoice.getPaymentStatus(),
                invoice.getPaidAt(),
                invoice.generateTaxQrBase64(sellerName, vatNumber)
        );
    }
}