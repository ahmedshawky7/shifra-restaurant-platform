package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.application.port.out.MenuRepositoryPort;
import com.restaurant.platform.domain.model.ItemAddon;
import com.restaurant.platform.domain.model.ItemVariant;
import com.restaurant.platform.domain.model.MenuCategory;
import com.restaurant.platform.domain.model.MenuItem;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class MenuPersistenceAdapter implements MenuRepositoryPort {

    private final MenuCategoryJpaRepository categoryRepo;
    private final MenuItemJpaRepository itemRepo;
    private final ItemVariantJpaRepository variantRepo;
    private final ItemAddonJpaRepository addonRepo;
    private final BranchMenuItemJpaRepository branchItemRepo;

    public MenuPersistenceAdapter(MenuCategoryJpaRepository categoryRepo,
                                  MenuItemJpaRepository itemRepo,
                                  ItemVariantJpaRepository variantRepo,
                                  ItemAddonJpaRepository addonRepo,
                                  BranchMenuItemJpaRepository branchItemRepo) {
        this.categoryRepo = categoryRepo;
        this.itemRepo = itemRepo;
        this.variantRepo = variantRepo;
        this.addonRepo = addonRepo;
        this.branchItemRepo = branchItemRepo;
    }

    @Override
    public List<MenuCategory> findActiveCategoriesByOrgAndBranch(Long organizationId, Long branchId) {
        return categoryRepo.findActiveForBranch(organizationId, branchId).stream()
                .map(c -> new MenuCategory(c.getId(), c.getOrganizationId(), c.getBranchId(),
                        c.getName(), c.getDescription(), c.getImageUrl(), c.getSortOrder(), c.isActive()))
                .toList();
    }

    @Override
    public List<MenuItem> findActiveItemsByOrg(Long organizationId) {
        List<MenuItemEntity> items = itemRepo.findByOrganizationIdAndActiveTrueOrderByIdAsc(organizationId);
        if (items.isEmpty()) return List.of();

        List<Long> ids = items.stream().map(MenuItemEntity::getId).toList();
        Map<Long, List<ItemVariant>> variantsMap = variantRepo.findByMenuItemIdInAndActiveTrue(ids).stream()
                .collect(Collectors.groupingBy(
                        ItemVariantEntity::getMenuItemId,
                        Collectors.mapping(v -> new ItemVariant(v.getId(), v.getMenuItemId(),
                                v.getName(), v.getPrice(), v.isDefault(), v.isActive()), Collectors.toList())
                ));
        Map<Long, List<ItemAddon>> addonsMap = addonRepo.findByMenuItemIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        ItemAddonEntity::getMenuItemId,
                        Collectors.mapping(a -> new ItemAddon(a.getId(), a.getMenuItemId(),
                                a.getName(), a.getPrice(), a.isAvailable()), Collectors.toList())
                ));

        return items.stream()
                .map(e -> new MenuItem(
                        e.getId(), e.getOrganizationId(), e.getCategoryId(), e.getName(),
                        e.getDescription(), e.getBasePrice(), e.getImageUrl(),
                        e.getPrepTimeMinutes(), e.isAvailable(), e.isActive(),
                        variantsMap.getOrDefault(e.getId(), List.of()),
                        addonsMap.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    @Override
    public Optional<MenuItem> findMenuItemById(Long menuItemId) {
        return itemRepo.findById(menuItemId).map(e -> {
            var variants = variantRepo.findByMenuItemIdAndActiveTrue(e.getId()).stream()
                    .map(v -> new ItemVariant(v.getId(), v.getMenuItemId(), v.getName(),
                            v.getPrice(), v.isDefault(), v.isActive()))
                    .toList();
            var addons = addonRepo.findByMenuItemId(e.getId()).stream()
                    .map(a -> new ItemAddon(a.getId(), a.getMenuItemId(), a.getName(),
                            a.getPrice(), a.isAvailable()))
                    .toList();
            return new MenuItem(
                    e.getId(), e.getOrganizationId(), e.getCategoryId(), e.getName(),
                    e.getDescription(), e.getBasePrice(), e.getImageUrl(),
                    e.getPrepTimeMinutes(), e.isAvailable(), e.isActive(),
                    variants, addons);
        });
    }

    @Override
    public boolean isItemAvailableInBranch(Long branchId, Long menuItemId) {
        return branchItemRepo.findByBranchIdAndMenuItemId(branchId, menuItemId)
                .map(BranchMenuItemEntity::isAvailable)
                .orElse(true);
    }

    @Override
    public Optional<BigDecimal> getBranchPriceOverride(Long branchId, Long menuItemId) {
        return branchItemRepo.findByBranchIdAndMenuItemId(branchId, menuItemId)
                .map(BranchMenuItemEntity::getPriceOverride);
    }

    @Override
    @Transactional
    public void setItemAvailability(Long organizationId, Long branchId, Long menuItemId, boolean available) {
        MenuItemEntity item = itemRepo.findById(menuItemId)
                .orElseThrow(() -> new IllegalArgumentException("الصنف غير موجود"));
        if (!item.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("الصنف لا ينتمي لهذا المطعم");
        }
        if (branchId != null) {
            BranchMenuItemEntity override = branchItemRepo.findByBranchIdAndMenuItemId(branchId, menuItemId)
                    .orElseGet(() -> {
                        BranchMenuItemEntity created = new BranchMenuItemEntity();
                        created.setOrganizationId(organizationId);
                        created.setBranchId(branchId);
                        created.setMenuItemId(menuItemId);
                        return created;
                    });
            override.setAvailable(available);
            branchItemRepo.save(override);
        } else {
            item.setAvailable(available);
            itemRepo.save(item);
        }
    }
}