package com.restaurant.platform.application.port.out;

import com.restaurant.platform.domain.model.Organization;
import java.util.Optional;

public interface OrganizationRepositoryPort {
    Organization save(Organization organization);
    Optional<Organization> findById(Long id);
}