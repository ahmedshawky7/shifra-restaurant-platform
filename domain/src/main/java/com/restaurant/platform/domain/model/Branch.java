package com.restaurant.platform.domain.model;

import java.math.BigDecimal;

public class Branch {
    private final Long id;
    private final Long organizationId;
    private final String name;
    private final String address;
    private final String city;
    private final String phone;
    private final String workingDays;
    private final String openTime;
    private final String closeTime;
    private final String timezone;
    private final BigDecimal taxRate;
    private final BigDecimal serviceChargeRate;
    private boolean openToday;
    private String closureReason;

    public Branch(Long id, Long organizationId, String name, String address, String city,
                  String phone, String workingDays, String openTime, String closeTime,
                  String timezone, BigDecimal taxRate, BigDecimal serviceChargeRate,
                  boolean openToday, String closureReason) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.address = address;
        this.city = city;
        this.phone = phone;
        this.workingDays = workingDays;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.timezone = timezone;
        this.taxRate = taxRate != null ? taxRate : BigDecimal.ZERO;
        this.serviceChargeRate = serviceChargeRate != null ? serviceChargeRate : BigDecimal.ZERO;
        this.openToday = openToday;
        this.closureReason = openToday ? null : closureReason;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getPhone() { return phone; }
    public String getWorkingDays() { return workingDays; }
    public String getOpenTime() { return openTime; }
    public String getCloseTime() { return closeTime; }
    public String getTimezone() { return timezone; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getServiceChargeRate() { return serviceChargeRate; }
    public boolean isOpenToday() { return openToday; }
    public String getClosureReason() { return closureReason; }
}