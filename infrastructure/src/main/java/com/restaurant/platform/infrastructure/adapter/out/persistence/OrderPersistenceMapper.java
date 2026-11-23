package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.domain.model.Order;
import com.restaurant.platform.domain.model.OrderItem;
import com.restaurant.platform.domain.model.OrderItemAddon;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderPersistenceMapper {

    public OrderEntity toEntity(Order domain) {
        OrderEntity e = new OrderEntity();
        e.setId(domain.getId());
        e.setOrganizationId(domain.getOrganizationId());
        e.setBranchId(domain.getBranchId());
        e.setTableId(domain.getTableId());
        e.setTableSessionId(domain.getTableSessionId());
        e.setOrderNumber(domain.getOrderNumber());
        e.setOrderDate(domain.getCreatedAt().toLocalDate());
        e.setTrackingToken(domain.getTrackingToken());
        e.setOrderType(domain.getOrderType());
        e.setOrderSource(domain.getOrderSource());
        e.setCreatedByUserId(domain.getCreatedByUserId());
        e.setCustomerName(domain.getCustomerName());
        e.setCustomerPhone(domain.getCustomerPhone());
        e.setDeliveryAddress(domain.getDeliveryAddress());
        e.setNotes(domain.getNotes());
        e.setSubtotal(domain.getSubtotal());
        e.setTaxAmount(domain.getTaxAmount());
        e.setServiceCharge(domain.getServiceCharge());
        e.setDiscountAmount(domain.getDiscountAmount());
        e.setTotalAmount(domain.getTotalAmount());
        e.setStatus(domain.getStatus());
        e.setPrepStartedAt(domain.getPrepStartedAt());
        e.setReadyAt(domain.getReadyAt());
        e.setCreatedAt(domain.getCreatedAt());

        List<OrderItemEntity> itemEntities = domain.getItems().stream().map(item -> {
            OrderItemEntity ie = new OrderItemEntity();
            ie.setId(item.getId());
            ie.setOrder(e);
            ie.setMenuItemId(item.getMenuItemId());
            ie.setItemNameSnapshot(item.getItemNameSnapshot());
            ie.setVariantId(item.getVariantId());
            ie.setVariantNameSnapshot(item.getVariantNameSnapshot());
            ie.setQuantity(item.getQuantity());
            ie.setUnitPrice(item.getUnitPrice());
            ie.setNotes(item.getNotes());
            ie.setLineTotal(item.getLineTotal());

            List<OrderItemAddonEntity> addonEntities = new java.util.ArrayList<>(
                    item.getAddons().stream().map(addon -> {
                        OrderItemAddonEntity ae = new OrderItemAddonEntity();
                        ae.setId(addon.id());
                        ae.setOrderItem(ie);
                        ae.setAddonId(addon.addonId());
                        ae.setAddonNameSnapshot(addon.addonNameSnapshot());
                        ae.setAddonPriceSnapshot(addon.addonPriceSnapshot());
                        return ae;
                    }).toList()
            );
            ie.setAddons(addonEntities);
            return ie;
        }).toList();

        e.setItems(new java.util.ArrayList<>(itemEntities));
        return e;
    }

    public Order toDomain(OrderEntity e) {
        List<OrderItem> items = e.getItems().stream().map(ie -> {
            List<OrderItemAddon> addons = ie.getAddons().stream()
                    .map(ae -> new OrderItemAddon(ae.getId(), ae.getAddonId(),
                            ae.getAddonNameSnapshot(), ae.getAddonPriceSnapshot()))
                    .toList();
            return new OrderItem(ie.getId(), ie.getMenuItemId(), ie.getItemNameSnapshot(),
                    ie.getVariantId(), ie.getVariantNameSnapshot(), ie.getQuantity(),
                    ie.getUnitPrice(), ie.getNotes(), addons);
        }).toList();

        return Order.reconstruct(
                e.getId(), e.getOrganizationId(), e.getBranchId(), e.getTableId(),
                e.getTableSessionId(), e.getOrderNumber(), e.getTrackingToken(),
                e.getOrderType(), e.getOrderSource(), e.getCreatedByUserId(),
                e.getCustomerName(), e.getCustomerPhone(), e.getDeliveryAddress(),
                e.getNotes(), items, e.getSubtotal(), e.getTaxAmount(),
                e.getServiceCharge(), e.getDiscountAmount(), e.getTotalAmount(),
                e.getStatus(), e.getPrepStartedAt(), e.getReadyAt(), e.getCreatedAt()
        );
    }
}