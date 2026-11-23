package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.domain.model.enums.OrganizationType;
import com.restaurant.platform.domain.model.enums.SubscriptionPlan;
import com.restaurant.platform.domain.model.enums.SubscriptionStatus;
import com.restaurant.platform.domain.model.enums.UserRole;
import com.restaurant.platform.infrastructure.adapter.out.persistence.*;
import com.restaurant.platform.infrastructure.security.CustomUserDetails;
import com.restaurant.platform.infrastructure.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserJpaRepository userRepo;
    private final OrganizationJpaRepository orgRepo;
    private final BranchJpaRepository branchRepo;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authManager,
            JwtService jwtService,
            UserJpaRepository userRepo,
            OrganizationJpaRepository orgRepo,
            BranchJpaRepository branchRepo,
            PasswordEncoder passwordEncoder) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userRepo = userRepo;
        this.orgRepo = orgRepo;
        this.branchRepo = branchRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public record LoginRequest(String username, String password) {
    }

    public record RegisterWorkspaceRequest(
            String username,
            String password,
            String fullName,
            String phoneNumber,
            String organizationName,
            OrganizationType organizationType,
            String firstBranchName,
            String city,
            String address,
            SubscriptionPlan plan) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        if (req.username() == null || req.username().isBlank() || req.password() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "اسم المستخدم أو كلمة المرور غير صحيحة"));
        }

        try {
            var auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username().trim(), req.password()));
            CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
            String token = jwtService.generateToken(user);

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "userId", user.getUserId(),
                    "username", user.getUsername(),
                    "role", user.getRole().name(),
                    "organizationId", user.getOrganizationId() != null ? user.getOrganizationId() : 0,
                    "branchId", user.getBranchId() != null ? user.getBranchId() : 0,
                    "isOwner", user.isOwner()));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "اسم المستخدم أو كلمة المرور غير صحيحة"));
        }
    }

    @PostMapping("/register-workspace")
    @Transactional
    public ResponseEntity<Map<String, Object>> registerWorkspace(@RequestBody RegisterWorkspaceRequest req) {
        if (req.username() == null || req.username().isBlank() || req.password() == null
                || req.password().length() < 6) {
            throw new IllegalArgumentException("اسم المستخدم وكلمة المرور (6 أحرف على الأقل) مطلوبان");
        }
        if (userRepo.findByUsername(req.username().trim()).isPresent()) {
            throw new IllegalArgumentException("اسم المستخدم مستخدم بالفعل");
        }
        if (req.organizationName() == null || req.organizationName().isBlank()) {
            throw new IllegalArgumentException("اسم المطعم مطلوب");
        }

        String slug = "rest-" + UUID.randomUUID().toString().substring(0, 8);
        SubscriptionPlan chosenPlan = req.plan() != null ? req.plan() : SubscriptionPlan.TRIAL;

        OrganizationEntity org = new OrganizationEntity();
        org.setName(req.organizationName().trim());
        org.setSlug(slug);
        org.setType(req.organizationType() != null ? req.organizationType() : OrganizationType.SINGLE_BRANCH);
        org.setPlan(chosenPlan);
        org.setSubscriptionStatus(
                chosenPlan == SubscriptionPlan.TRIAL ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE);
        org.setActive(true);
        org.setTrialEndsAt(LocalDateTime.now().plusDays(14));
        if (chosenPlan != SubscriptionPlan.TRIAL) {
            org.setSubscriptionEndsAt(LocalDateTime.now().plusMonths(1));
        }
        org = orgRepo.save(org);

        BranchEntity branch = new BranchEntity();
        branch.setOrganizationId(org.getId());
        branch.setName(req.firstBranchName() != null && !req.firstBranchName().isBlank()
                ? req.firstBranchName().trim()
                : "الفرع الرئيسي");
        branch.setCity(req.city());
        branch.setAddress(req.address());
        branch.setPhone(req.phoneNumber());
        branch.setTaxRate(BigDecimal.valueOf(14));
        branch.setServiceChargeRate(BigDecimal.ZERO);
        branch = branchRepo.save(branch);

        UserEntity owner = new UserEntity();
        owner.setUsername(req.username().trim());
        owner.setPassword(passwordEncoder.encode(req.password()));
        owner.setFullName(req.fullName() != null ? req.fullName().trim() : req.organizationName().trim());
        owner.setPhoneNumber(req.phoneNumber());
        owner.setRole(UserRole.ORG_ADMIN);
        owner.setOrganizationId(org.getId());
        owner.setBranchId(branch.getId());
        owner.setOwner(true);
        owner.setActive(true);
        userRepo.save(owner);

        return ResponseEntity.ok(Map.of(
                "message", "تم إنشاء مساحة عمل المطعم بنجاح",
                "organizationId", org.getId(),
                "branchId", branch.getId()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody ChangePasswordRequest req) {
        if (req.newPassword() == null || req.newPassword().length() < 6) {
            throw new IllegalArgumentException("كلمة المرور الجديدة يجب أن تكون 6 أحرف على الأقل");
        }
        UserEntity user = userRepo.findById(principal.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("المستخدم غير موجود"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("كلمة المرور الحالية غير صحيحة");
        }
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("message", "تم تغيير كلمة المرور بنجاح"));
    }
}
