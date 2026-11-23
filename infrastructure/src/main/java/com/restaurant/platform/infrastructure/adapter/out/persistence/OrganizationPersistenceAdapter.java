package com.restaurant.platform.infrastructure.adapter.out.persistence;

import com.restaurant.platform.application.port.out.OrganizationRepositoryPort;
import com.restaurant.platform.domain.model.Organization;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class OrganizationPersistenceAdapter implements OrganizationRepositoryPort {
    private final OrganizationJpaRepository jpa;

    public OrganizationPersistenceAdapter(OrganizationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Organization save(Organization o) {
        OrganizationEntity e = new OrganizationEntity();
        e.setId(o.getId());
        e.setName(o.getName());
        e.setSlug(o.getSlug());
        e.setType(o.getType());
        e.setPlan(o.getPlan());
        e.setSubscriptionStatus(o.getSubscriptionStatus());
        e.setActive(o.isActive());
        e.setTrialEndsAt(o.getTrialEndsAt());
        e.setSubscriptionEndsAt(o.getSubscriptionEndsAt());
        e.setCreatedAt(o.getCreatedAt());
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<Organization> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    private Organization toDomain(OrganizationEntity e) {
        return new Organization(e.getId(), e.getName(), e.getSlug(), e.getType(), e.getPlan(),
                e.getSubscriptionStatus(), e.isActive(), e.getTrialEndsAt(), e.getSubscriptionEndsAt(), e.getCreatedAt());
    }
}