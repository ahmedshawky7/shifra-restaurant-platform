package com.restaurant.platform.infrastructure.adapter.in.web;

import com.restaurant.platform.application.util.PasswordGenerator;
import com.restaurant.platform.domain.model.enums.SubscriptionStatus;
import com.restaurant.platform.infrastructure.adapter.out.persistence.OrganizationEntity;
import com.restaurant.platform.infrastructure.adapter.out.persistence.OrganizationJpaRepository;
import com.restaurant.platform.infrastructure.adapter.out.persistence.UserEntity;
import com.restaurant.platform.infrastructure.adapter.out.persistence.UserJpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin")
public class SuperAdminController {

    private final OrganizationJpaRepository orgRepo;
    private final UserJpaRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminController(OrganizationJpaRepository orgRepo,
                                UserJpaRepository userRepo,
                                PasswordEncoder passwordEncoder) {
        this.orgRepo = orgRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/organizations")
    public List<OrganizationEntity> listAll() {
        return orgRepo.findAll();
    }

    @PutMapping("/organizations/{id}/subscription")
    public OrganizationEntity updateSubscription(@PathVariable Long id, @RequestBody Map<String, String> body) {
        OrganizationEntity org = orgRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("المطعم غير موجود"));
        org.setSubscriptionStatus(SubscriptionStatus.valueOf(body.get("status")));
        return orgRepo.save(org);
    }

    @PutMapping("/organizations/{id}/active")
    public OrganizationEntity toggleActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        OrganizationEntity org = orgRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("المطعم غير موجود"));
        org.setActive(Boolean.TRUE.equals(body.get("active")));
        return orgRepo.save(org);
    }

    @PostMapping("/organizations/{id}/reset-admin-password")
    public ResponseEntity<Map<String, String>> resetAdminPassword(@PathVariable Long id) {
        UserEntity owner = userRepo.findFirstByOrganizationIdAndOwnerTrue(id)
                .orElseThrow(() -> new IllegalArgumentException("لم يتم العثور على مالك لهذا المطعم"));
        String tempPass = PasswordGenerator.generate(8);
        owner.setPassword(passwordEncoder.encode(tempPass));
        userRepo.save(owner);
        return ResponseEntity.ok(Map.of(
                "username", owner.getUsername(),
                "temporaryPassword", tempPass
        ));
    }
}