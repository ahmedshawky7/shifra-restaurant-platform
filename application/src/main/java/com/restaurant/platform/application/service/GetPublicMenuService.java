package com.restaurant.platform.application.service;

import com.restaurant.platform.application.port.in.GetPublicMenuUseCase;
import com.restaurant.platform.application.port.out.BranchStatusPort;
import com.restaurant.platform.application.port.out.MenuRepositoryPort;
import com.restaurant.platform.application.port.out.OrganizationRepositoryPort;
import com.restaurant.platform.application.port.out.TableRepositoryPort;
import com.restaurant.platform.application.result.PublicMenuResult;
import com.restaurant.platform.domain.model.Branch;
import com.restaurant.platform.domain.model.MenuCategory;
import com.restaurant.platform.domain.model.MenuItem;
import com.restaurant.platform.domain.model.Organization;
import com.restaurant.platform.domain.model.RestaurantTable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GetPublicMenuService implements GetPublicMenuUseCase {

    private final TableRepositoryPort tableRepo;
    private final BranchStatusPort branchStatusPort;
    private final OrganizationRepositoryPort organizationRepo;
    private final MenuRepositoryPort menuRepo;

    public GetPublicMenuService(TableRepositoryPort tableRepo,
                                BranchStatusPort branchStatusPort,
                                OrganizationRepositoryPort organizationRepo,
                                MenuRepositoryPort menuRepo) {
        this.tableRepo = tableRepo;
        this.branchStatusPort = branchStatusPort;
        this.organizationRepo = organizationRepo;
        this.menuRepo = menuRepo;
    }

    @Override
    public PublicMenuResult getMenuByQrToken(String qrToken) {
        RestaurantTable table = tableRepo.findByQrToken(qrToken)
                .orElseThrow(() -> new IllegalArgumentException("رمز الطاولة غير صالح أو غير موجود"));
        if (!table.isActive()) {
            throw new IllegalStateException("هذه الطاولة غير متاحة حالياً");
        }
        return buildMenu(table.getBranchId(), table.getId(), table.getTableNumber());
    }

    @Override
    public PublicMenuResult getMenuByBranchId(Long branchId) {
        return buildMenu(branchId, null, null);
    }

    private PublicMenuResult buildMenu(Long branchId, Long tableId, String tableNumber) {
        Branch branch = branchStatusPort.findBranchById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("الفرع غير موجود"));

        Organization org = organizationRepo.findById(branch.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("المطعم غير موجود"));

        ZoneId zone = branchStatusPort.getBranchZoneId(branchId);
        org.ensureActiveSubscription(Clock.system(zone));

        boolean open = branchStatusPort.isBranchOpen(branchId) && branchStatusPort.isWithinWorkingHours(branchId);
        String closureMsg = !branchStatusPort.isBranchOpen(branchId)
                ? branchStatusPort.getClosureReason(branchId)
                : (!branchStatusPort.isWithinWorkingHours(branchId) ? branchStatusPort.getClosedHoursMessage(branchId) : null);

        List<MenuCategory> categories = menuRepo.findActiveCategoriesByOrgAndBranch(org.getId(), branchId);
        List<MenuItem> allItems = menuRepo.findActiveItemsByOrg(org.getId());

        Map<Long, List<PublicMenuResult.MenuItemDto>> itemsByCat = allItems.stream()
                .collect(Collectors.groupingBy(
                        MenuItem::categoryId,
                        Collectors.mapping(item -> {
                            boolean availableInBranch = item.available()
                                    && menuRepo.isItemAvailableInBranch(branchId, item.id());
                            BigDecimal effectivePrice = menuRepo.getBranchPriceOverride(branchId, item.id())
                                    .orElse(item.basePrice());
                            return new PublicMenuResult.MenuItemDto(
                                    item.id(),
                                    item.name(),
                                    item.description(),
                                    effectivePrice,
                                    item.imageUrl(),
                                    item.prepTimeMinutes(),
                                    availableInBranch,
                                    item.variants(),
                                    item.addons()
                            );
                        }, Collectors.toList())
                ));

        var categoryDtos = categories.stream()
                .map(cat -> new PublicMenuResult.CategoryMenuDto(
                        cat.id(),
                        cat.name(),
                        cat.description(),
                        cat.imageUrl(),
                        itemsByCat.getOrDefault(cat.id(), List.of())
                ))
                .toList();

        return new PublicMenuResult(
                org.getId(),
                org.getName(),
                branch.getId(),
                branch.getName(),
                open,
                closureMsg,
                tableId,
                tableNumber,
                branch.getTaxRate(),
                branch.getServiceChargeRate(),
                categoryDtos
        );
    }
}