package com.restaurant.platform.domain.model;

import com.restaurant.platform.domain.exception.InvalidOrderTransitionException;
import com.restaurant.platform.domain.model.enums.OrderSource;
import com.restaurant.platform.domain.model.enums.OrderStatus;
import com.restaurant.platform.domain.model.enums.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    private Order newDineInOrder() {
        var item = new OrderItem(
                null, 10L, "برجر لحم", 1L, "دبل", 2,
                BigDecimal.valueOf(120), "بدون بصل",
                List.of(new OrderItemAddon(null, 5L, "جبنة شيدر", BigDecimal.valueOf(20)))
        );
        return Order.place(
                1L, 2L, 3L, 4L, 101, "trk-123",
                OrderType.DINE_IN, OrderSource.QR_CUSTOMER, null,
                null, null, null, null,
                List.of(item), BigDecimal.valueOf(14), BigDecimal.valueOf(10),
                LocalDateTime.now()
        );
    }

    @Test
    void should_calculate_subtotal_tax_and_service_correctly() {
        var order = newDineInOrder();
        // (120 + 20) * 2 = 280 | tax 14% = 39.20 | service 10% = 28.00 | total = 347.20
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getSubtotal()).isEqualByComparingTo("280.00");
        assertThat(order.getTaxAmount()).isEqualByComparingTo("39.20");
        assertThat(order.getServiceCharge()).isEqualByComparingTo("28.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("347.20");
    }

    @Test
    void should_follow_valid_kitchen_and_waiter_lifecycle() {
        var order = newDineInOrder();
        var now = LocalDateTime.now();

        order.startPreparing(now);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);

        order.markReady(now.plusMinutes(10));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);

        order.markServed();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SERVED);

        order.complete();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void should_not_allow_customer_cancel_after_preparing() {
        var order = newDineInOrder();
        order.startPreparing(LocalDateTime.now());

        assertThatThrownBy(order::cancelByCustomer)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_not_mark_ready_directly_from_pending() {
        var order = newDineInOrder();

        assertThatThrownBy(() -> order.markReady(LocalDateTime.now()))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }
}