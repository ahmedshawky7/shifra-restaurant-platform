package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.application.command.PlaceOrderCommand;
import com.restaurant.platform.application.command.TableAlertCommand;
import com.restaurant.platform.application.port.in.*;
import com.restaurant.platform.application.port.out.BranchStatusPort;
import com.restaurant.platform.application.port.out.OrderRepositoryPort;
import com.restaurant.platform.application.port.out.OrganizationRepositoryPort;
import com.restaurant.platform.application.result.OrderResult;
import com.restaurant.platform.application.result.PublicMenuResult;
import com.restaurant.platform.domain.model.enums.OrderSource;
import com.restaurant.platform.domain.model.enums.OrderStatus;
import com.restaurant.platform.domain.model.enums.OrderType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicQrController {

    private final GetPublicMenuUseCase getPublicMenuUseCase;
    private final PlaceOrderUseCase placeOrderUseCase;
    private final TrackOrderUseCase trackOrderUseCase;
    private final CancelCustomerOrderUseCase cancelCustomerOrderUseCase;
    private final SendTableAlertUseCase sendTableAlertUseCase;
    private final OrderRepositoryPort orderRepo;
    private final BranchStatusPort branchStatusPort;
    private final OrganizationRepositoryPort orgRepo;

    public PublicQrController(GetPublicMenuUseCase getPublicMenuUseCase,
                              PlaceOrderUseCase placeOrderUseCase,
                              TrackOrderUseCase trackOrderUseCase,
                              CancelCustomerOrderUseCase cancelCustomerOrderUseCase,
                              SendTableAlertUseCase sendTableAlertUseCase,
                              OrderRepositoryPort orderRepo,
                              BranchStatusPort branchStatusPort,
                              OrganizationRepositoryPort orgRepo) {
        this.getPublicMenuUseCase = getPublicMenuUseCase;
        this.placeOrderUseCase = placeOrderUseCase;
        this.trackOrderUseCase = trackOrderUseCase;
        this.cancelCustomerOrderUseCase = cancelCustomerOrderUseCase;
        this.sendTableAlertUseCase = sendTableAlertUseCase;
        this.orderRepo = orderRepo;
        this.branchStatusPort = branchStatusPort;
        this.orgRepo = orgRepo;
    }

    public record CustomerOrderRequest(
            String tableQrToken,
            Long branchId,
            OrderType orderType,
            String customerName,
            String customerPhone,
            String deliveryAddress,
            String notes,
            List<PlaceOrderCommand.OrderItemRequest> items
    ) {}

    @GetMapping("/menu/table/{qrToken}")
    public PublicMenuResult getMenuByTable(@PathVariable String qrToken) {
        return getPublicMenuUseCase.getMenuByQrToken(qrToken);
    }

    @GetMapping("/menu/branch/{branchId}")
    public PublicMenuResult getMenuByBranch(@PathVariable Long branchId) {
        return getPublicMenuUseCase.getMenuByBranchId(branchId);
    }

    @PostMapping("/orders")
    public OrderResult placeCustomerOrder(@RequestBody CustomerOrderRequest req) {
        OrderType type = req.orderType() != null ? req.orderType() : OrderType.DINE_IN;
        return placeOrderUseCase.placeOrder(new PlaceOrderCommand(
                req.branchId(),
                req.tableQrToken(),
                null,
                type,
                OrderSource.QR_CUSTOMER,
                null,
                req.customerName(),
                req.customerPhone(),
                req.deliveryAddress(),
                req.notes(),
                req.items()
        ));
    }

    @GetMapping("/orders/{trackingToken}")
    public OrderResult trackOrder(@PathVariable String trackingToken) {
        return trackOrderUseCase.trackByToken(trackingToken);
    }

    @PostMapping("/orders/{trackingToken}/cancel")
    public OrderResult cancelOrder(@PathVariable String trackingToken) {
        return cancelCustomerOrderUseCase.cancelByTrackingToken(trackingToken);
    }

    @PostMapping("/tables/alert")
    public ResponseEntity<Map<String, String>> sendTableAlert(@RequestBody TableAlertCommand cmd) {
        sendTableAlertUseCase.sendAlert(cmd);
        return ResponseEntity.ok(Map.of("message", "تم إرسال التنبيه للطاقم بنجاح"));
    }

    @GetMapping("/branches/{branchId}/pickup")
    public Map<String, Object> pickupScreen(@PathVariable Long branchId) {
        var branch = branchStatusPort.findBranchById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("الفرع غير موجود"));
        var orgName = orgRepo.findById(branch.getOrganizationId()).map(o -> o.getName()).orElse("");
        ZoneId zone = branchStatusPort.getBranchZoneId(branchId);
        var active = orderRepo.findByBranchAndStatuses(
                branchId, LocalDate.now(zone), List.of(OrderStatus.PREPARING, OrderStatus.READY));

        var preparing = active.stream()
                .filter(o -> o.getStatus() == OrderStatus.PREPARING)
                .map(o -> Map.of("orderNumber", o.getOrderNumber(), "orderType", o.getOrderType().name()))
                .toList();
        var ready = active.stream()
                .filter(o -> o.getStatus() == OrderStatus.READY)
                .map(o -> Map.of("orderNumber", o.getOrderNumber(), "orderType", o.getOrderType().name()))
                .toList();

        return Map.of(
                "organizationName", orgName,
                "branchName", branch.getName(),
                "preparing", preparing,
                "ready", ready
        );
    }
}