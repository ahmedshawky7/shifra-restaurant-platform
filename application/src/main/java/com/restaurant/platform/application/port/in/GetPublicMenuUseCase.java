package com.restaurant.platform.application.port.in;

import com.restaurant.platform.application.result.PublicMenuResult;

public interface GetPublicMenuUseCase {
    PublicMenuResult getMenuByQrToken(String qrToken);
    PublicMenuResult getMenuByBranchId(Long branchId);
}