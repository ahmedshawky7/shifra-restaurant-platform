package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.application.port.out.OrderRepositoryPort;
import com.restaurant.platform.application.port.out.OrganizationRepositoryPort;
import com.restaurant.platform.application.util.PasswordGenerator;
import com.restaurant.platform.domain.model.Organization;
import com.restaurant.platform.domain.model.enums.SubscriptionPlan;
import com.restaurant.platform.domain.model.enums.SubscriptionStatus;
import com.restaurant.platform.domain.model.enums.UserRole;
import com.restaurant.platform.infrastructure.adapter.out.persistence.*;
import com.restaurant.platform.infrastructure.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/org")
public class OrgManagementController {

    private static final List<UserRole> STAFF_ROLES = List.of(
            UserRole.ORG_ADMIN, UserRole.CASHIER, UserRole.KITCHEN_STAFF, UserRole.WAITER);

    private final OrganizationJpaRepository orgJpa;
    private final OrganizationRepositoryPort orgPort;
    private final BranchJpaRepository branchRepo;
    private final UserJpaRepository userRepo;
    private final OrderRepositoryPort orderRepo;
    private final RestaurantTableJpaRepository tableRepo;
    private final MenuCategoryJpaRepository categoryRepo;
    private final MenuItemJpaRepository itemRepo;
    private final PasswordEncoder passwordEncoder;

    public OrgManagementController(OrganizationJpaRepository orgJpa,
                                   OrganizationRepositoryPort orgPort,
                                   BranchJpaRepository branchRepo,
                                   UserJpaRepository userRepo,
                                   OrderRepositoryPort orderRepo,
                                   RestaurantTableJpaRepository tableRepo,
                                   MenuCategoryJpaRepository categoryRepo,
                                   MenuItemJpaRepository itemRepo,
                                   PasswordEncoder passwordEncoder) {
        this.orgJpa = orgJpa;
        this.orgPort = orgPort;
        this.branchRepo = branchRepo;
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
        this.tableRepo = tableRepo;
        this.categoryRepo = categoryRepo;
        this.itemRepo = itemRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public record CreateBranchRequest(String name, String city, String address, String phone,
                                      BigDecimal taxRate, BigDecimal serviceChargeRate) {}

    public record CreateStaffRequest(String fullName, String username, String password,
                                     String phoneNumber, UserRole role, Long branchId) {}

    @GetMapping("/overview")
    public Map<String, Object> overview(@AuthenticationPrincipal CustomUserDetails principal) {
        Long orgId = principal.getOrganizationId();
        OrganizationEntity org = orgJpa.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("المطعم غير موجود"));
        List<BranchEntity> branches = branchRepo.findByOrganizationId(orgId);
        List<UserEntity> staff = userRepo.findByOrganizationIdAndRoleInOrderByIdDesc(orgId, STAFF_ROLES);
        LocalDate now = LocalDate.now();
        long monthlyOrders = orderRepo.countByOrganizationInMonth(orgId, now.getYear(), now.getMonthValue());

        return Map.of(
                "organization", org,
                "branches", branches,
                "staff", staff.stream().map(u -> Map.of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "fullName", u.getFullName() != null ? u.getFullName() : "",
                        "phoneNumber", u.getPhoneNumber() != null ? u.getPhoneNumber() : "",
                        "role", u.getRole().name(),
                        "branchId", u.getBranchId() != null ? u.getBranchId() : 0,
                        "owner", u.isOwner(),
                        "active", u.isActive()
                )).toList(),
                "branchesCount", branches.size(),
                "staffCount", staff.size(),
                "categoriesCount", categoryRepo.findByOrganizationIdOrderBySortOrderAscIdAsc(orgId).size(),
                "itemsCount", itemRepo.findByOrganizationIdAndActiveTrueOrderByIdAsc(orgId).size(),
                "monthlyOrders", monthlyOrders
        );
    }

    @PutMapping("/subscription")
    public OrganizationEntity updateSubscription(@AuthenticationPrincipal CustomUserDetails principal,
                                                 @RequestBody Map<String, String> body) {
        OrganizationEntity org = orgJpa.findById(principal.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("المطعم غير موجود"));
        org.setPlan(SubscriptionPlan.valueOf(body.get("plan")));
        org.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        org.setSubscriptionEndsAt(LocalDateTime.now().plusMonths(1));
        return orgJpa.save(org);
    }

    @PostMapping("/branches")
    public BranchEntity addBranch(@AuthenticationPrincipal CustomUserDetails principal,
                                  @RequestBody CreateBranchRequest req) {
        Long orgId = principal.getOrganizationId();
        Organization org = orgPort.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("المطعم غير موجود"));
        long count = branchRepo.countByOrganizationId(orgId);
        org.ensureCanAddBranch(count);

        BranchEntity b = new BranchEntity();
        b.setOrganizationId(orgId);
        b.setName(req.name());
        b.setCity(req.city());
        b.setAddress(req.address());
        b.setPhone(req.phone());
        b.setTaxRate(req.taxRate() != null ? req.taxRate() : BigDecimal.valueOf(14));
        b.setServiceChargeRate(req.serviceChargeRate() != null ? req.serviceChargeRate() : BigDecimal.ZERO);
        return branchRepo.save(b);
    }

    @GetMapping("/staff")
    public List<Map<String, Object>> listStaff(@AuthenticationPrincipal CustomUserDetails principal) {
        return userRepo.findByOrganizationIdAndRoleInOrderByIdDesc(principal.getOrganizationId(), STAFF_ROLES)
                .stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "fullName", u.getFullName() != null ? u.getFullName() : "",
                        "phoneNumber", u.getPhoneNumber() != null ? u.getPhoneNumber() : "",
                        "role", u.getRole().name(),
                        "branchId", u.getBranchId() != null ? u.getBranchId() : 0,
                        "owner", u.isOwner(),
                        "active", u.isActive()
                )).toList();
    }

    @PostMapping("/staff")
    public ResponseEntity<Map<String, Object>> addStaff(@AuthenticationPrincipal CustomUserDetails principal,
                                                        @RequestBody CreateStaffRequest req) {
        Long orgId = principal.getOrganizationId();
        Organization org = orgPort.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("المطعم غير موجود"));
        long count = userRepo.countByOrganizationIdAndRoleIn(orgId, STAFF_ROLES);
        org.ensureCanAddStaff(count);

        if (userRepo.findByUsername(req.username().trim()).isPresent()) {
            throw new IllegalArgumentException("اسم المستخدم مستخدم بالفعل");
        }
        if (req.role() == UserRole.SUPER_ADMIN || req.role() == UserRole.CUSTOMER) {
            throw new IllegalArgumentException("دور وظيفي غير مسموح");
        }

        UserEntity u = new UserEntity();
        u.setOrganizationId(orgId);
        u.setBranchId(req.branchId());
        u.setUsername(req.username().trim());
        u.setPassword(passwordEncoder.encode(req.password()));
        u.setFullName(req.fullName());
        u.setPhoneNumber(req.phoneNumber());
        u.setRole(req.role());
        u.setOwner(false);
        u.setActive(true);
        u = userRepo.save(u);

        return ResponseEntity.ok(Map.of("id", u.getId(), "username", u.getUsername()));
    }

    @PutMapping("/staff/{id}/status")
    public ResponseEntity<Void> toggleStaffStatus(@AuthenticationPrincipal CustomUserDetails principal,
                                                  @PathVariable Long id,
                                                  @RequestBody Map<String, Boolean> body) {
        UserEntity staff = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("الموظف غير موجود"));
        if (!principal.getOrganizationId().equals(staff.getOrganizationId())) {
            throw new SecurityException("غير مصرح");
        }
        if (staff.isOwner()) {
            throw new IllegalStateException("لا يمكن إيقاف حساب المالك");
        }
        staff.setActive(Boolean.TRUE.equals(body.get("active")));
        userRepo.save(staff);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/staff/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetStaffPassword(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {
        UserEntity staff = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("الموظف غير موجود"));
        if (!principal.getOrganizationId().equals(staff.getOrganizationId())) {
            throw new SecurityException("غير مصرح");
        }
        String temp = PasswordGenerator.generate(8);
        staff.setPassword(passwordEncoder.encode(temp));
        userRepo.save(staff);
        return ResponseEntity.ok(Map.of("username", staff.getUsername(), "temporaryPassword", temp));
    }
}