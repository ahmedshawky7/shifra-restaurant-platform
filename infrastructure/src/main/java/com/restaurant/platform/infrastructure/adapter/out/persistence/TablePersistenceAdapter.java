package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.application.port.out.TableRepositoryPort;
import com.restaurant.platform.domain.model.RestaurantTable;
import com.restaurant.platform.domain.model.TableSession;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TablePersistenceAdapter implements TableRepositoryPort {

    private final RestaurantTableJpaRepository tableRepo;
    private final TableSessionJpaRepository sessionRepo;

    public TablePersistenceAdapter(RestaurantTableJpaRepository tableRepo, TableSessionJpaRepository sessionRepo) {
        this.tableRepo = tableRepo;
        this.sessionRepo = sessionRepo;
    }

    @Override
    public Optional<RestaurantTable> findById(Long tableId) {
        return tableRepo.findById(tableId).map(this::toDomain);
    }

    @Override
    public Optional<RestaurantTable> findByQrToken(String qrToken) {
        return tableRepo.findByQrToken(qrToken).map(this::toDomain);
    }

    @Override
    public List<RestaurantTable> findByBranchId(Long branchId) {
        return tableRepo.findByBranchIdOrderByIdAsc(branchId).stream().map(this::toDomain).toList();
    }

    @Override
    public RestaurantTable save(RestaurantTable t) {
        RestaurantTableEntity e = new RestaurantTableEntity();
        e.setId(t.getId());
        e.setOrganizationId(t.getOrganizationId());
        e.setBranchId(t.getBranchId());
        e.setTableNumber(t.getTableNumber());
        e.setQrToken(t.getQrToken());
        e.setCapacity(t.getCapacity());
        e.setStatus(t.getStatus());
        e.setActive(t.isActive());
        return toDomain(tableRepo.save(e));
    }

    @Override
    public Optional<TableSession> findOpenSessionByTableId(Long tableId) {
        return sessionRepo.findFirstByTableIdAndOpenTrueOrderByOpenedAtDesc(tableId).map(this::toSessionDomain);
    }

    @Override
    public Optional<TableSession> findSessionById(Long sessionId) {
        return sessionRepo.findById(sessionId).map(this::toSessionDomain);
    }

    @Override
    public TableSession saveSession(TableSession s) {
        TableSessionEntity e = new TableSessionEntity();
        e.setId(s.getId());
        e.setOrganizationId(s.getOrganizationId());
        e.setBranchId(s.getBranchId());
        e.setTableId(s.getTableId());
        e.setSessionToken(s.getSessionToken());
        e.setOpen(s.isOpen());
        e.setOpenedAt(s.getOpenedAt());
        e.setClosedAt(s.getClosedAt());
        return toSessionDomain(sessionRepo.save(e));
    }

    private RestaurantTable toDomain(RestaurantTableEntity e) {
        return new RestaurantTable(e.getId(), e.getOrganizationId(), e.getBranchId(),
                e.getTableNumber(), e.getQrToken(), e.getCapacity(), e.getStatus(), e.isActive());
    }

    private TableSession toSessionDomain(TableSessionEntity e) {
        return new TableSession(e.getId(), e.getOrganizationId(), e.getBranchId(), e.getTableId(),
                e.getSessionToken(), e.isOpen(), e.getOpenedAt(), e.getClosedAt());
    }
}