package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.domain.model.enums.OrderSource;
import com.restaurant.platform.domain.model.enums.OrderStatus;
import com.restaurant.platform.domain.model.enums.OrderType;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_branch_date_order_num",
               columnNames = {"branch_id", "order_date", "order_number"}),
       indexes = {
               @Index(name = "idx_branch_date_status", columnList = "branch_id, order_date, status"),
               @Index(name = "idx_orders_branch_status_created", columnList = "branch_id, status, created_at"),
               @Index(name = "idx_orders_branch_created", columnList = "branch_id, created_at"),
               @Index(name = "idx_org_order_date", columnList = "organization_id, order_date"),
               @Index(name = "idx_session_id", columnList = "table_session_id")
       })
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "table_id")
    private Long tableId;

    @Column(name = "table_session_id")
    private Long tableSessionId;

    @Column(name = "order_number", nullable = false)
    private Integer orderNumber;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "tracking_token", nullable = false, unique = true, length = 64)
    private String trackingToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_source", nullable = false, length = 20)
    private OrderSource orderSource;

    private Long createdByUserId;
    private String customerName;
    private String customerPhone;
    private String deliveryAddress;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceCharge;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    private LocalDateTime prepStartedAt;
    private LocalDateTime readyAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @OrderBy("id ASC")
    private List<OrderItemEntity> items = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long v) { this.organizationId = v; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long v) { this.branchId = v; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long v) { this.tableId = v; }
    public Long getTableSessionId() { return tableSessionId; }
    public void setTableSessionId(Long v) { this.tableSessionId = v; }
    public Integer getOrderNumber() { return orderNumber; }
    public void setOrderNumber(Integer v) { this.orderNumber = v; }
    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate v) { this.orderDate = v; }
    public String getTrackingToken() { return trackingToken; }
    public void setTrackingToken(String v) { this.trackingToken = v; }
    public OrderType getOrderType() { return orderType; }
    public void setOrderType(OrderType v) { this.orderType = v; }
    public OrderSource getOrderSource() { return orderSource; }
    public void setOrderSource(OrderSource v) { this.orderSource = v; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long v) { this.createdByUserId = v; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String v) { this.customerName = v; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String v) { this.customerPhone = v; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String v) { this.deliveryAddress = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
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
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus v) { this.status = v; }
    public LocalDateTime getPrepStartedAt() { return prepStartedAt; }
    public void setPrepStartedAt(LocalDateTime v) { this.prepStartedAt = v; }
    public LocalDateTime getReadyAt() { return readyAt; }
    public void setReadyAt(LocalDateTime v) { this.readyAt = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public List<OrderItemEntity> getItems() { return items; }
    public void setItems(List<OrderItemEntity> items) { this.items = items; }
}