package com.tradeflow.api.organization;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganizationRepository
        extends JpaRepository<Organization, Long> {

    boolean existsByBusinessEmail(String businessEmail);

    Optional<Organization> findByBusinessEmail(String businessEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Organization o WHERE o.id = :organizationId")
    Optional<Organization> findByIdForUpdate(
            @Param("organizationId") Long organizationId
    );
}
