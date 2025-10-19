package de.tum.in.www1.hephaestus.organization;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByInstallationId(Long installationId);
    Optional<Organization> findByGithubId(Long installationId);
    Optional<Organization> findByLoginIgnoreCase(String login);
}
