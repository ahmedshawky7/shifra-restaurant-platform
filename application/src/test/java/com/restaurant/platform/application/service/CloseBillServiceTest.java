package com.restaurant.platform.application.service;

import com.restaurant.platform.application.command.CloseBillCommand;
import com.restaurant.platform.application.port.out.*;
import com.restaurant.platform.domain.model.*;
import com.restaurant.platform.domain.model.enums.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloseBillServiceTest {

    @Mock private OrderRepositoryPort orderRepo;
    @Mock private TableRepositoryPort tableRepo;
    @Mock private InvoiceRepositoryPort invoiceRepo;
    @Mock private BranchStatusPort branchStatusPort;
    @Mock private OrderEventPublisherPort eventPublisher;

    @InjectMocks private CloseBillService service;

    @Test
    void should_close_table_session_issue_invoice_and_release_table() {
        var now = LocalDateTime.now();
        var table = new RestaurantTable(5L, 10L, 20L, "T-05", "qr-abc", 4, TableStatus.OCCUPIED, true);
        var session = new TableSession(99L, 10L, 20L, 5L, "sess-1", true, now.minusMinutes(40), null);

        var orderItem = new OrderItem(1L, 100L, "عصير", null, null, 2,
                BigDecimal.valueOf(50), null, List.of());
        var order = Order.reconstruct(500L, 10L, 20L, 5L, 99L, 1, "trk-1",
                OrderType.DINE_IN, OrderSource.QR_CUSTOMER, null, null, null, null, null,
                List.of(orderItem), BigDecimal.valueOf(100), BigDecimal.valueOf(14),
                BigDecimal.valueOf(10), BigDecimal.ZERO, BigDecimal.valueOf(124),
                OrderStatus.SERVED, now.minusMinutes(30), now.minusMinutes(20), now.minusMinutes(35));

        when(branchStatusPort.getBranchZoneId(20L)).thenReturn(ZoneId.of("Africa/Cairo"));
        when(branchStatusPort.getOrganizationIdByBranchId(20L)).thenReturn(10L);
        when(tableRepo.findById(5L)).thenReturn(Optional.of(table));
        when(tableRepo.findOpenSessionByTableId(5L)).thenReturn(Optional.of(session));
        when(orderRepo.findUnpaidOrdersBySessionId(99L)).thenReturn(List.of(order));
        when(invoiceRepo.nextInvoiceNumber(eq(20L), any())).thenReturn("INV-2026-0001");
        when(invoiceRepo.save(any())).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            return new Invoice(700L, i.getOrganizationId(), i.getBranchId(), i.getTableSessionId(),
                    i.getSingleOrderId(), i.getInvoiceNumber(), i.getSubtotal(), i.getTaxAmount(),
                    i.getServiceCharge(), i.getDiscountAmount(), i.getTotalAmount(),
                    i.getPaymentMethod(), i.getPaymentStatus(), i.getCashierUserId(), i.getPaidAt());
        });
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.closeBill(new CloseBillCommand(
                20L, 5L, null, BigDecimal.valueOf(4), PaymentMethod.CARD, 88L
        ));

        assertThat(result.invoiceId()).isEqualTo(700L);
        assertThat(result.totalAmount()).isEqualByComparingTo("120.00"); // 100 + 14 + 10 - 4
        assertThat(table.getStatus()).isEqualTo(TableStatus.AVAILABLE);
        assertThat(session.isOpen()).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        verify(eventPublisher).publishToPos(argThat(e -> e.eventType().equals("TABLE_UPDATED")));
    }
}