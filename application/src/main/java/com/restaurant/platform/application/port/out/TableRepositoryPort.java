package com.restaurant.platform.application.port.out;

import com.restaurant.platform.domain.model.RestaurantTable;
import com.restaurant.platform.domain.model.TableSession;

import java.util.List;
import java.util.Optional;

public interface TableRepositoryPort {
    Optional<RestaurantTable> findById(Long tableId);
    Optional<RestaurantTable> findByQrToken(String qrToken);
    List<RestaurantTable> findByBranchId(Long branchId);
    RestaurantTable save(RestaurantTable table);

    Optional<TableSession> findOpenSessionByTableId(Long tableId);
    Optional<TableSession> findSessionById(Long sessionId);
    TableSession saveSession(TableSession session);
}