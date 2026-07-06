package com.documind.org.application;

import com.documind.common.error.EntityNotFoundException;
import com.documind.org.domain.Organization;
import com.documind.org.domain.PlanTier;
import com.documind.org.infrastructure.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrganizationService {

    private static final long DEFAULT_FREE_TIER_STORAGE_QUOTA_BYTES = 5L * 1024 * 1024 * 1024; // 5 GiB

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    /** Creates a brand-new tenant on the FREE plan. Called once during registration, alongside creating that tenant's first admin user. */
    public Organization createOrganizationOnFreeTier(String organizationName) {
        Organization organization = Organization.createNew(organizationName, PlanTier.FREE, DEFAULT_FREE_TIER_STORAGE_QUOTA_BYTES);
        return organizationRepository.save(organization);
    }

    public Organization findByIdOrThrow(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> EntityNotFoundException.forEntity("Organization", organizationId));
    }
}
