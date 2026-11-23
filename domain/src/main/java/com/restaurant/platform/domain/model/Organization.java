package com.restaurant.platform.domain.model;

import com.restaurant.platform.domain.exception.SubscriptionException;
import com.restaurant.platform.domain.model.enums.OrganizationType;
import com.restaurant.platform.domain.model.enums.SubscriptionPlan;
import com.restaurant.platform.domain.model.enums.SubscriptionStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

public class Organization {
    private final Long id;
    private String name;
    private String slug;
    private OrganizationType type;
    private SubscriptionPlan plan;
    private SubscriptionStatus subscriptionStatus;
    private boolean active;
    private LocalDateTime trialEndsAt;
    private LocalDateTime subscriptionEndsAt;
    private final LocalDateTime createdAt;

    public Organization(Long id, String name, String slug, OrganizationType type,
                        SubscriptionPlan plan, SubscriptionStatus subscriptionStatus,
                        boolean active, LocalDateTime trialEndsAt, LocalDateTime subscriptionEndsAt,
                        LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.type = type;
        this.plan = plan;
        this.subscriptionStatus = subscriptionStatus;
        this.active = active;
        this.trialEndsAt = trialEndsAt;
        this.subscriptionEndsAt = subscriptionEndsAt;
        this.createdAt = createdAt;
    }

    public void ensureActiveSubscription() {
        ensureActiveSubscription(Clock.systemUTC());
    }

    public void ensureActiveSubscription(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        LocalDateTime now = LocalDateTime.now(clock);

        if (!active) {
            throw new SubscriptionException("تم إيقاف حساب المطعم من قِبل إدارة المنصة. يرجى التواصل مع الدعم.");
        }
        if (subscriptionStatus == SubscriptionStatus.SUSPENDED
                || subscriptionStatus == SubscriptionStatus.CANCELLED
                || subscriptionStatus == SubscriptionStatus.PAST_DUE) {
            throw new SubscriptionException("اشتراك المطعم غير نشط. يرجى تجديد الاشتراك للمتابعة.");
        }
        if (subscriptionStatus == SubscriptionStatus.TRIAL
                && trialEndsAt != null && trialEndsAt.isBefore(now)) {
            throw new SubscriptionException("انتهت الفترة التجريبية. يرجى اختيار باقة للمتابعة.");
        }
        if (subscriptionStatus == SubscriptionStatus.ACTIVE
                && subscriptionEndsAt != null && subscriptionEndsAt.isBefore(now)) {
            throw new SubscriptionException("انتهت صلاحية الاشتراك الشهري. يرجى التجديد.");
        }
    }

    public void ensureCanAddBranch(long currentBranchCount) {
        ensureCanAddBranch(currentBranchCount, Clock.systemUTC());
    }

    public void ensureCanAddBranch(long currentBranchCount, Clock clock) {
        ensureActiveSubscription(clock);
        if (type == OrganizationType.SINGLE_BRANCH && currentBranchCount >= 1) {
            throw new SubscriptionException("حساب الفرع الواحد يدعم فرعاً واحداً فقط. يمكنك الترقية إلى سلسلة مطاعم.");
        }
        if (currentBranchCount >= plan.getMaxBranches()) {
            throw new SubscriptionException("وصلت للحد الأقصى للفروع في باقتك الحالية (" + plan.getMaxBranches() + ").");
        }
    }

    public void ensureCanAddStaff(long currentStaffCount) {
        ensureCanAddStaff(currentStaffCount, Clock.systemUTC());
    }

    public void ensureCanAddStaff(long currentStaffCount, Clock clock) {
        ensureActiveSubscription(clock);
        if (currentStaffCount >= plan.getMaxStaff()) {
            throw new SubscriptionException("وصلت للحد الأقصى للموظفين في باقتك الحالية (" + plan.getMaxStaff() + ").");
        }
    }

    public void ensureCanCreateOrderThisMonth(long monthlyOrdersCount) {
        ensureCanCreateOrderThisMonth(monthlyOrdersCount, Clock.systemUTC());
    }

    public void ensureCanCreateOrderThisMonth(long monthlyOrdersCount, Clock clock) {
        ensureActiveSubscription(clock);
        if (monthlyOrdersCount >= plan.getMaxMonthlyOrders()) {
            throw new SubscriptionException("تم استهلاك الحد الأقصى للطلبات الشهرية في باقتك الحالية.");
        }
    }

    public void setActive(boolean active) { this.active = active; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public OrganizationType getType() { return type; }
    public SubscriptionPlan getPlan() { return plan; }
    public SubscriptionStatus getSubscriptionStatus() { return subscriptionStatus; }
    public boolean isActive() { return active; }
    public LocalDateTime getTrialEndsAt() { return trialEndsAt; }
    public LocalDateTime getSubscriptionEndsAt() { return subscriptionEndsAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}