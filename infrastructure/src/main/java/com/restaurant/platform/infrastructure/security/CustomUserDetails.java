package com.restaurant.platform.infrastructure.security;

import com.restaurant.platform.domain.model.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {
    private final Long userId;
    private final Long organizationId;
    private final Long branchId;
    private final UserRole role;
    private final boolean owner;
    private final boolean active;
    private final boolean organizationActive;
    private final String username;
    private final String password;

    public CustomUserDetails(Long userId, Long organizationId, Long branchId, UserRole role,
                             boolean owner, boolean active, boolean organizationActive,
                             String username, String password) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.branchId = branchId;
        this.role = role;
        this.owner = owner;
        this.active = active;
        this.organizationActive = organizationActive;
        this.username = username;
        this.password = password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        if (owner && role != UserRole.ORG_ADMIN) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"));
        }
        return authorities;
    }

    public Long getUserId() { return userId; }
    public Long getOrganizationId() { return organizationId; }
    public Long getBranchId() { return branchId; }
    public UserRole getRole() { return role; }
    public boolean isOwner() { return owner; }
    public boolean isUserActive() { return active; }
    public boolean isOrganizationActive() { return organizationActive; }

    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return organizationActive; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return active && organizationActive; }
}