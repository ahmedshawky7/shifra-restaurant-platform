package com.restaurant.platform.infrastructure.security;

import com.restaurant.platform.domain.model.enums.UserRole;
import com.restaurant.platform.infrastructure.adapter.out.persistence.OrganizationEntity;
import com.restaurant.platform.infrastructure.adapter.out.persistence.OrganizationJpaRepository;
import com.restaurant.platform.infrastructure.adapter.out.persistence.UserEntity;
import com.restaurant.platform.infrastructure.adapter.out.persistence.UserJpaRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserJpaRepository userRepository;
    private final OrganizationJpaRepository orgRepository;

    public CustomUserDetailsService(UserJpaRepository userRepository,
                                    OrganizationJpaRepository orgRepository) {
        this.userRepository = userRepository;
        this.orgRepository = orgRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity u = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        boolean orgActive = true;
        if (u.getOrganizationId() != null && u.getRole() != UserRole.CUSTOMER && u.getRole() != UserRole.SUPER_ADMIN) {
            orgActive = orgRepository.findById(u.getOrganizationId())
                    .map(OrganizationEntity::isActive)
                    .orElse(false);
        }

        return new CustomUserDetails(
                u.getId(),
                u.getOrganizationId(),
                u.getBranchId(),
                u.getRole(),
                u.isOwner(),
                u.isActive(),
                orgActive,
                u.getUsername(),
                u.getPassword());
    }
}