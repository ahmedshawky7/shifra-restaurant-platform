package com.restaurant.platform.application.service;

import com.restaurant.platform.application.command.PlaceOrderCommand;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceOrderServiceTest {

    @Mock private OrderRepositoryPort orderRepo;
    @Mock private MenuRepositoryPort menuRepo;
    @Mock private TableRepositoryPort tableRepo;
    @Mock private BranchStatusPort branchStatusPort;
    @Mock private OrganizationRepositoryPort organizationRepo;
    @Mock private OrderEventPublisherPort eventPublisher;

    @InjectMocks private PlaceOrderService service;

    @Test
    void should_place_qr_dine_in_order_and_broadcast_events() {
        var table = new RestaurantTable(5L, 10L, 20L, "T-05", "qr-abc", 4, TableStatus.AVAILABLE, true);
        var org = new Organization(10L, "مطعم السعادة", "saada", OrganizationType.SINGLE_BRANCH,
                SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, true, null,
                LocalDateTime.now().plusDays(15), LocalDateTime.now());

        var variant = new ItemVariant(2L, 100L, "كبير", BigDecimal.valueOf(150), false, true);
        var addon = new ItemAddon(3L, 100L, "صوص جبنة", BigDecimal.valueOf(25), true);
        var item = new MenuItem(100L, 10L, 1L, "بيتزا مارجريتا", "وصف",
                BigDecimal.valueOf(100), null, 15, true, true, List.of(variant), List.of(addon));

        when(tableRepo.findByQrToken("qr-abc")).thenReturn(Optional.of(table));
        when(branchStatusPort.isBranchOpen(20L)).thenReturn(true);
        when(branchStatusPort.isWithinWorkingHours(20L)).thenReturn(true);
        when(branchStatusPort.getOrganizationIdByBranchId(20L)).thenReturn(10L);
        when(branchStatusPort.getBranchZoneId(20L)).thenReturn(ZoneId.of("Africa/Cairo"));
        when(branchStatusPort.getTaxRate(20L)).thenReturn(BigDecimal.valueOf(14));
        when(branchStatusPort.getServiceChargeRate(20L)).thenReturn(BigDecimal.valueOf(12));
        when(organizationRepo.findById(10L)).thenReturn(Optional.of(org));
        when(orderRepo.countByOrganizationInMonth(eq(10L), anyInt(), anyInt())).thenReturn(10L);
        when(tableRepo.findOpenSessionByTableId(5L)).thenReturn(Optional.empty());
        when(tableRepo.saveSession(any())).thenAnswer(inv -> {
            TableSession s = inv.getArgument(0);
            return new TableSession(99L, s.getOrganizationId(), s.getBranchId(), s.getTableId(),
                    s.getSessionToken(), true, s.getOpenedAt(), null);
        });
        when(menuRepo.findMenuItemById(100L)).thenReturn(Optional.of(item));
        when(menuRepo.isItemAvailableInBranch(20L, 100L)).thenReturn(true);
        when(menuRepo.getBranchPriceOverride(20L, 100L)).thenReturn(Optional.empty());
        when(orderRepo.nextOrderNumber(eq(20L), any())).thenReturn(1);
        when(orderRepo.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return Order.reconstruct(500L, o.getOrganizationId(), o.getBranchId(), o.getTableId(),
                    o.getTableSessionId(), o.getOrderNumber(), o.getTrackingToken(), o.getOrderType(),
                    o.getOrderSource(), o.getCreatedByUserId(), o.getCustomerName(), o.getCustomerPhone(),
                    o.getDeliveryAddress(), o.getNotes(), o.getItems(), o.getSubtotal(), o.getTaxAmount(),
                    o.getServiceCharge(), o.getDiscountAmount(), o.getTotalAmount(), o.getStatus(),
                    o.getPrepStartedAt(), o.getReadyAt(), o.getCreatedAt());
        });

        var cmd = new PlaceOrderCommand(
                null, "qr-abc", null, OrderType.DINE_IN, OrderSource.QR_CUSTOMER,
                null, null, null, null, "عاجل",
                List.of(new PlaceOrderCommand.OrderItemRequest(100L, 2L, 2, "مقرمشة", List.of(3L)))
        );

        var result = service.placeOrder(cmd);

        // (150 + 25) * 2 = 350.00 subtotal
        assertThat(result.orderId()).isEqualTo(500L);
        assertThat(result.orderNumber()).isEqualTo(1);
        assertThat(result.tableNumber()).isEqualTo("T-05");
        assertThat(result.subtotal()).isEqualByComparingTo("350.00");
        assertThat(table.getStatus()).isEqualTo(TableStatus.OCCUPIED);

        verify(eventPublisher).publishToKds(any());
        verify(eventPublisher).publishToPos(any());
        verify(eventPublisher).publishToCustomerTracking(any());
    }

    @Test
    void should_reject_order_when_branch_is_closed() {
        when(branchStatusPort.isBranchOpen(20L)).thenReturn(false);
        when(branchStatusPort.getClosureReason(20L)).thenReturn("إجازة رسمية");

        var cmd = new PlaceOrderCommand(
                20L, null, null, OrderType.TAKEAWAY, OrderSource.CASHIER_POS,
                1L, "عميل", null, null, null,
                List.of(new PlaceOrderCommand.OrderItemRequest(100L, null, 1, null, List.of()))
        );

        assertThatThrownBy(() -> service.placeOrder(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("إجازة رسمية");

        verify(orderRepo, never()).save(any());
    }
}