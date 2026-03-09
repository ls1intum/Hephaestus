package de.tum.in.www1.hephaestus.gitprovider.common;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link GitProvider} entities.
 * <p>
 * Git providers are auto-created when workspaces are activated. The
 * {@link #findByTypeAndServerUrl} lookup is the primary resolution path.
 */
public interface GitProviderRepository extends JpaRepository<GitProvider, Long> {
    Optional<GitProvider> findByTypeAndServerUrl(GitProviderType type, String serverUrl);
}
