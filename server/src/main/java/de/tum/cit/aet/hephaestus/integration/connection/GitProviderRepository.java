package de.tum.cit.aet.hephaestus.integration.connection;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link GitProvider} entities.
 *
 * <p>Workspace-agnostic by design: a {@code GitProvider} row models a vendor
 * <em>instance</em> (e.g. {@code github.com}, {@code gitlab.lrz.de}), not a tenant
 * resource. Many workspaces share the same provider instance — multi-tenancy is
 * enforced one layer up, on the {@link Connection} aggregate (which is workspace-scoped).
 *
 * <p>Git providers are auto-created when workspaces are activated. The
 * {@link #findByTypeAndServerUrl} lookup is the primary resolution path.
 */
@WorkspaceAgnostic("GitProvider models a vendor instance (github.com, gitlab.lrz.de) shared across all workspaces; tenant scoping is enforced on the Connection aggregate.")
public interface GitProviderRepository extends JpaRepository<GitProvider, Long> {
    Optional<GitProvider> findByTypeAndServerUrl(GitProviderType type, String serverUrl);
}
