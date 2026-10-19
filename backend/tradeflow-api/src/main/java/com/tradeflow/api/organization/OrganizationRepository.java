package com.tradeflow.api.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository
        extends JpaRepository<Organization, Long> {

    boolean existsByBusinessEmail(String businessEmail);

    Optional<Organization> findByBusinessEmail(String businessEmail);
}