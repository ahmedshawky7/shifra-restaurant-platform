package com.restaurant.platform.application.port.out;

import com.restaurant.platform.domain.model.Branch;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Optional;

public interface BranchStatusPort {
    boolean isBranchOpen(Long branchId);
    String getClosureReason(Long branchId);
    boolean isWithinWorkingHours(Long branchId);
    String getClosedHoursMessage(Long branchId);
    ZoneId getBranchZoneId(Long branchId);
    Long getOrganizationIdByBranchId(Long branchId);
    BigDecimal getTaxRate(Long branchId);
    BigDecimal getServiceChargeRate(Long branchId);
    Optional<Branch> findBranchById(Long branchId);
}