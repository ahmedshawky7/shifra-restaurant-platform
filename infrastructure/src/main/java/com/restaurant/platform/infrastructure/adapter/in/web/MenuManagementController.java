package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.application.port.out.AuditLogPort;
import com.restaurant.platform.domain.model.AuditLog;
import com.restaurant.platform.infrastructure.adapter.out.persistence.*;
import com.restaurant.platform.infrastructure.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/org/menu")
public class MenuManagementController {

    private final MenuCategoryJpaRepository categoryRepo;
    private final MenuItemJpaRepository itemRepo;
    private final ItemVariantJpaRepository variantRepo;
    private final ItemAddonJpaRepository addonRepo;
    private final AuditLogPort auditLogPort;
    private final TenantGuard tenantGuard;

    public MenuManagementController(MenuCategoryJpaRepository categoryRepo,
                                    MenuItemJpaRepository itemRepo,
                                    ItemVariantJpaRepository variantRepo,
                                    ItemAddonJpaRepository addonRepo,
                                    AuditLogPort auditLogPort,
                                    TenantGuard tenantGuard) {
        this.categoryRepo = categoryRepo;
        this.itemRepo = itemRepo;
        this.variantRepo = variantRepo;
        this.addonRepo = addonRepo;
        this.auditLogPort = auditLogPort;
        this.tenantGuard = tenantGuard;
    }

    public record CategoryRequest(Long branchId, String name, String description, String imageUrl, int sortOrder, Boolean active) {}
    public record VariantRequest(String name, BigDecimal price, boolean isDefault) {}
    public record AddonRequest(String name, BigDecimal price, Boolean available) {}
    public record MenuItemRequest(Long categoryId, String name, String description, BigDecimal basePrice,
                                  String imageUrl, Integer prepTimeMinutes, Boolean available,
                                  List<VariantRequest> variants, List<AddonRequest> addons) {}

    @GetMapping
    public Map<String, Object> getFullMenu(@AuthenticationPrincipal CustomUserDetails principal) {
        Long orgId = principal.getOrganizationId();
        var categories = categoryRepo.findByOrganizationIdOrderBySortOrderAscIdAsc(orgId);
        var items = itemRepo.findByOrganizationIdOrderByIdAsc(orgId);
        var ids = items.stream().map(MenuItemEntity::getId).toList();
        var variantsMap = ids.isEmpty() ? Map.<Long, List<ItemVariantEntity>>of()
                : variantRepo.findByMenuItemIdInAndActiveTrue(ids).stream()
                        .collect(Collectors.groupingBy(ItemVariantEntity::getMenuItemId));
        var addonsMap = ids.isEmpty() ? Map.<Long, List<ItemAddonEntity>>of()
                : addonRepo.findByMenuItemIdIn(ids).stream()
                        .collect(Collectors.groupingBy(ItemAddonEntity::getMenuItemId));

        var itemDtos = items.stream().map(i -> Map.<String, Object>ofEntries(
                Map.entry("id", i.getId()),
                Map.entry("categoryId", i.getCategoryId()),
                Map.entry("name", i.getName()),
                Map.entry("description", i.getDescription() != null ? i.getDescription() : ""),
                Map.entry("basePrice", i.getBasePrice()),
                Map.entry("imageUrl", i.getImageUrl() != null ? i.getImageUrl() : ""),
                Map.entry("prepTimeMinutes", i.getPrepTimeMinutes()),
                Map.entry("available", i.isAvailable()),
                Map.entry("active", i.isActive()),
                Map.entry("variants", variantsMap.getOrDefault(i.getId(), List.of())),
                Map.entry("addons", addonsMap.getOrDefault(i.getId(), List.of()))
        )).toList();

        return Map.of("categories", categories, "items", itemDtos);
    }

    @PostMapping("/categories")
    public MenuCategoryEntity createCategory(@AuthenticationPrincipal CustomUserDetails principal,
                                             @RequestBody CategoryRequest req) {
        if (req.branchId() != null) {
            tenantGuard.requireBranchAccess(principal, req.branchId());
        }
        MenuCategoryEntity c = new MenuCategoryEntity();
        c.setOrganizationId(principal.getOrganizationId());
        c.setBranchId(req.branchId());
        c.setName(req.name());
        c.setDescription(req.description());
        c.setImageUrl(req.imageUrl());
        c.setSortOrder(req.sortOrder());
        c.setActive(req.active() == null || req.active());
        return categoryRepo.save(c);
    }

    @PutMapping("/categories/{id}")
    public MenuCategoryEntity updateCategory(@AuthenticationPrincipal CustomUserDetails principal,
                                             @PathVariable Long id,
                                             @RequestBody CategoryRequest req) {
        MenuCategoryEntity c = categoryRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("القسم غير موجود"));
        if (!principal.getOrganizationId().equals(c.getOrganizationId())) {
            throw new AccessDeniedException("غير مصرح");
        }
        c.setName(req.name());
        c.setDescription(req.description());
        c.setImageUrl(req.imageUrl());
        c.setSortOrder(req.sortOrder());
        if (req.active() != null) c.setActive(req.active());
        return categoryRepo.save(c);
    }

    @PostMapping("/items")
    @Transactional
    public Map<String, Object> createItem(@AuthenticationPrincipal CustomUserDetails principal,
                                          @RequestBody MenuItemRequest req) {
        Long orgId = principal.getOrganizationId();
        requireOwnedCategory(orgId, req.categoryId());

        MenuItemEntity item = new MenuItemEntity();
        item.setOrganizationId(orgId);
        item.setCategoryId(req.categoryId());
        item.setName(req.name());
        item.setDescription(req.description());
        item.setBasePrice(req.basePrice());
        item.setImageUrl(req.imageUrl());
        item.setPrepTimeMinutes(req.prepTimeMinutes() != null ? req.prepTimeMinutes() : 15);
        item.setAvailable(req.available() == null || req.available());
        item.setActive(true);
        item = itemRepo.save(item);

        saveVariantsAndAddons(item.getId(), req);

        auditLogPort.record(AuditLog.of(
                orgId,
                principal.getBranchId(),
                principal.getUserId(),
                principal.getUsername(),
                "CREATE_MENU_ITEM",
                "MENU_ITEM",
                item.getId(),
                null,
                "NAME=" + item.getName() + ", BASE_PRICE=" + item.getBasePrice(),
                LocalDateTime.now()
        ));

        return Map.of("id", item.getId(), "name", item.getName());
    }

    @PutMapping("/items/{id}")
    @Transactional
    public ResponseEntity<Void> updateItem(@AuthenticationPrincipal CustomUserDetails principal,
                                           @PathVariable Long id,
                                           @RequestBody MenuItemRequest req) {
        MenuItemEntity item = itemRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("الصنف غير موجود"));
        if (!principal.getOrganizationId().equals(item.getOrganizationId())) {
            throw new AccessDeniedException("غير مصرح");
        }
        if (req.categoryId() != null) {
            requireOwnedCategory(principal.getOrganizationId(), req.categoryId());
            item.setCategoryId(req.categoryId());
        }

        BigDecimal oldBasePrice = item.getBasePrice();
        String oldName = item.getName();
        String oldVariantsSummary = variantRepo.findByMenuItemIdAndActiveTrue(id).stream()
                .map(v -> v.getName() + ":" + v.getPrice())
                .collect(Collectors.joining(", "));

        item.setName(req.name());
        item.setDescription(req.description());
        item.setBasePrice(req.basePrice());
        item.setImageUrl(req.imageUrl());
        if (req.prepTimeMinutes() != null) item.setPrepTimeMinutes(req.prepTimeMinutes());
        if (req.available() != null) item.setAvailable(req.available());
        itemRepo.save(item);

        if (req.variants() != null) {
            variantRepo.findByMenuItemIdAndActiveTrue(id).forEach(v -> {
                v.setActive(false);
                variantRepo.save(v);
            });
            for (var vr : req.variants()) {
                ItemVariantEntity v = new ItemVariantEntity();
                v.setMenuItemId(id);
                v.setName(vr.name());
                v.setPrice(vr.price());
                v.setDefault(vr.isDefault());
                v.setActive(true);
                variantRepo.save(v);
            }
        }
        if (req.addons() != null) {
            addonRepo.deleteAll(addonRepo.findByMenuItemId(id));
            for (var ar : req.addons()) {
                ItemAddonEntity a = new ItemAddonEntity();
                a.setMenuItemId(id);
                a.setName(ar.name());
                a.setPrice(ar.price());
                a.setAvailable(ar.available() == null || ar.available());
                addonRepo.save(a);
            }
        }

        String newVariantsSummary = req.variants() == null ? oldVariantsSummary
                : req.variants().stream().map(v -> v.name() + ":" + v.price()).collect(Collectors.joining(", "));
        boolean priceChanged = oldBasePrice == null || req.basePrice() == null
                || oldBasePrice.compareTo(req.basePrice()) != 0
                || !oldVariantsSummary.equals(newVariantsSummary);

        auditLogPort.record(AuditLog.of(
                principal.getOrganizationId(),
                principal.getBranchId(),
                principal.getUserId(),
                principal.getUsername(),
                priceChanged ? "UPDATE_MENU_PRICE" : "UPDATE_MENU_ITEM",
                "MENU_ITEM",
                item.getId(),
                "NAME=" + oldName + ", BASE_PRICE=" + oldBasePrice + (oldVariantsSummary.isEmpty() ? "" : ", VARIANTS=[" + oldVariantsSummary + "]"),
                "NAME=" + item.getName() + ", BASE_PRICE=" + item.getBasePrice() + (newVariantsSummary.isEmpty() ? "" : ", VARIANTS=[" + newVariantsSummary + "]"),
                LocalDateTime.now()
        ));

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/items/{id}")
    @Transactional
    public ResponseEntity<Void> archiveItem(@AuthenticationPrincipal CustomUserDetails principal,
                                            @PathVariable Long id) {
        MenuItemEntity item = itemRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("الصنف غير موجود"));
        if (!principal.getOrganizationId().equals(item.getOrganizationId())) {
            throw new AccessDeniedException("غير مصرح");
        }
        item.setActive(false);
        itemRepo.save(item);

        auditLogPort.record(AuditLog.of(
                principal.getOrganizationId(),
                principal.getBranchId(),
                principal.getUserId(),
                principal.getUsername(),
                "ARCHIVE_MENU_ITEM",
                "MENU_ITEM",
                item.getId(),
                "ACTIVE=true, NAME=" + item.getName() + ", PRICE=" + item.getBasePrice(),
                "ACTIVE=false",
                LocalDateTime.now()
        ));

        return ResponseEntity.ok().build();
    }

    private void requireOwnedCategory(Long orgId, Long categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("يجب تحديد القسم");
        }
        MenuCategoryEntity category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("القسم غير موجود"));
        if (!orgId.equals(category.getOrganizationId())) {
            throw new AccessDeniedException("غير مصرح بإضافة أو نقل صنف إلى قسم لا يتبع منشأتك");
        }
    }

    private void saveVariantsAndAddons(Long itemId, MenuItemRequest req) {
        if (req.variants() != null) {
            for (var vr : req.variants()) {
                ItemVariantEntity v = new ItemVariantEntity();
                v.setMenuItemId(itemId);
                v.setName(vr.name());
                v.setPrice(vr.price());
                v.setDefault(vr.isDefault());
                v.setActive(true);
                variantRepo.save(v);
            }
        }
        if (req.addons() != null) {
            for (var ar : req.addons()) {
                ItemAddonEntity a = new ItemAddonEntity();
                a.setMenuItemId(itemId);
                a.setName(ar.name());
                a.setPrice(ar.price());
                a.setAvailable(ar.available() == null || ar.available());
                addonRepo.save(a);
            }
        }
    }
}