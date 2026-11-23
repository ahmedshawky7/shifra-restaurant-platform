package com.restaurant.platform.domain.model.enums;

public enum SubscriptionPlan {
    TRIAL(1, 5, 300),
    BASIC(2, 15, 2000),
    PRO(5, 50, 15000),
    ENTERPRISE(50, 500, 200000);

    private final int maxBranches;
    private final int maxStaff;
    private final int maxMonthlyOrders;

    SubscriptionPlan(int maxBranches, int maxStaff, int maxMonthlyOrders) {
        this.maxBranches = maxBranches;
        this.maxStaff = maxStaff;
        this.maxMonthlyOrders = maxMonthlyOrders;
    }

    public int getMaxBranches() { return maxBranches; }
    public int getMaxStaff() { return maxStaff; }
    public int getMaxMonthlyOrders() { return maxMonthlyOrders; }
}