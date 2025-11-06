package de.tum.in.www1.hephaestus.gitprovider.installationtarget;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallationTargetRepository extends JpaRepository<InstallationTarget, Long> {
    Optional<InstallationTarget> findByLoginIgnoreCase(String login);
}
