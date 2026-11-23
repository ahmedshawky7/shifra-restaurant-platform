package com.restaurant.platform.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "branches")
public class BranchEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long organizationId;

    @Column(nullable = false)
    private String name;

    private String address;
    private String city;
    private String phone;
    private String workingDays = "SATURDAY,SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";
    private String openTime = "09:00";
    private String closeTime = "23:59";
    private String timezone = "Africa/Cairo";

    @Column(precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.valueOf(14.00);

    @Column(precision = 5, scale = 2)
    private BigDecimal serviceChargeRate = BigDecimal.ZERO;

    private boolean openToday = true;
    private String closureReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWorkingDays() { return workingDays; }
    public void setWorkingDays(String workingDays) { this.workingDays = workingDays; }
    public String getOpenTime() { return openTime; }
    public void setOpenTime(String openTime) { this.openTime = openTime; }
    public String getCloseTime() { return closeTime; }
    public void setCloseTime(String closeTime) { this.closeTime = closeTime; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public BigDecimal getServiceChargeRate() { return serviceChargeRate; }
    public void setServiceChargeRate(BigDecimal serviceChargeRate) { this.serviceChargeRate = serviceChargeRate; }
    public boolean isOpenToday() { return openToday; }
    public void setOpenToday(boolean openToday) { this.openToday = openToday; }
    public String getClosureReason() { return closureReason; }
    public void setClosureReason(String closureReason) { this.closureReason = closureReason; }
}