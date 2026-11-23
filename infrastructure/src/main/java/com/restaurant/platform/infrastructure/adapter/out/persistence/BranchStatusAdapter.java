package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.application.port.out.BranchStatusPort;
import com.restaurant.platform.domain.model.Branch;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;

@Component
public class BranchStatusAdapter implements BranchStatusPort {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Cairo");

    private final BranchJpaRepository branchRepo;
    private final OrganizationJpaRepository orgRepo;

    public BranchStatusAdapter(BranchJpaRepository branchRepo, OrganizationJpaRepository orgRepo) {
        this.branchRepo = branchRepo;
        this.orgRepo = orgRepo;
    }

    @Override
    public boolean isBranchOpen(Long branchId) {
        return branchRepo.findById(branchId).map(branch -> {
            boolean orgActive = orgRepo.findById(branch.getOrganizationId())
                    .map(OrganizationEntity::isActive)
                    .orElse(false);
            if (!orgActive || !branch.isOpenToday()) return false;

            ZoneId zone = resolveZone(branch);
            String today = LocalDate.now(zone).getDayOfWeek().name();
            String workingDays = branch.getWorkingDays();
            return workingDays == null || workingDays.isBlank()
                    || Arrays.asList(workingDays.split(",")).contains(today);
        }).orElse(false);
    }

    @Override
    public String getClosureReason(Long branchId) {
        return branchRepo.findById(branchId).map(branch -> {
            boolean orgActive = orgRepo.findById(branch.getOrganizationId())
                    .map(OrganizationEntity::isActive)
                    .orElse(false);
            if (!orgActive) {
                return "تم إيقاف هذا المطعم مؤقتاً";
            }
            if (!branch.isOpenToday()) {
                return (branch.getClosureReason() != null && !branch.getClosureReason().isBlank())
                        ? branch.getClosureReason()
                        : "الفرع مغلق من قبل الإدارة";
            }
            ZoneId zone = resolveZone(branch);
            String today = LocalDate.now(zone).getDayOfWeek().name();
            if (branch.getWorkingDays() != null && !branch.getWorkingDays().isBlank()
                    && !Arrays.asList(branch.getWorkingDays().split(",")).contains(today)) {
                return "الفرع لا يعمل في هذا اليوم";
            }
            return "";
        }).orElse("الفرع غير موجود");
    }

    @Override
    public boolean isWithinWorkingHours(Long branchId) {
        return branchRepo.findById(branchId).map(branch -> {
            LocalTime open = parseTime(branch.getOpenTime(), LocalTime.MIN);
            LocalTime close = parseTime(branch.getCloseTime(), LocalTime.of(23, 59));
            LocalTime now = LocalTime.now(resolveZone(branch)).truncatedTo(ChronoUnit.MINUTES);

            if (!open.isAfter(close)) {
                return !now.isBefore(open) && !now.isAfter(close);
            } else {
                return !now.isBefore(open) || !now.isAfter(close);
            }
        }).orElse(false);
    }

    @Override
    public String getClosedHoursMessage(Long branchId) {
        return branchRepo.findById(branchId).map(branch -> {
            LocalTime open = parseTime(branch.getOpenTime(), LocalTime.MIN);
            LocalTime close = parseTime(branch.getCloseTime(), LocalTime.of(23, 59));
            return "المطعم مغلق حالياً. ساعات العمل من " + formatArabicTime(open) + " إلى " + formatArabicTime(close) + ".";
        }).orElse("الفرع غير موجود");
    }

    @Override
    public ZoneId getBranchZoneId(Long branchId) {
        return branchRepo.findById(branchId).map(this::resolveZone).orElse(DEFAULT_ZONE);
    }

    @Override
    public Long getOrganizationIdByBranchId(Long branchId) {
        return branchRepo.findById(branchId)
                .map(BranchEntity::getOrganizationId)
                .orElseThrow(() -> new IllegalArgumentException("الفرع غير موجود"));
    }

    @Override
    public BigDecimal getTaxRate(Long branchId) {
        return branchRepo.findById(branchId)
                .map(b -> b.getTaxRate() != null ? b.getTaxRate() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getServiceChargeRate(Long branchId) {
        return branchRepo.findById(branchId)
                .map(b -> b.getServiceChargeRate() != null ? b.getServiceChargeRate() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public Optional<Branch> findBranchById(Long branchId) {
        return branchRepo.findById(branchId).map(b -> new Branch(
                b.getId(), b.getOrganizationId(), b.getName(), b.getAddress(), b.getCity(),
                b.getPhone(), b.getWorkingDays(), b.getOpenTime(), b.getCloseTime(),
                b.getTimezone(), b.getTaxRate(), b.getServiceChargeRate(),
                b.isOpenToday(), b.getClosureReason()
        ));
    }

    private ZoneId resolveZone(BranchEntity branch) {
        try {
            if (branch.getTimezone() != null && !branch.getTimezone().isBlank()) {
                return ZoneId.of(branch.getTimezone());
            }
        } catch (Exception ignored) {}
        return DEFAULT_ZONE;
    }

    private LocalTime parseTime(String value, LocalTime fallback) {
        try {
            if (value != null && !value.isBlank()) {
                return LocalTime.parse(value.length() > 5 ? value.substring(0, 5) : value);
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private String formatArabicTime(LocalTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();
        String period = hour >= 12 ? "مساءً" : "صباحاً";
        int hour12 = hour % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format("%02d:%02d %s", hour12, minute, period);
    }
}