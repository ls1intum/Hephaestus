package de.tum.cit.aet.hephaestus.integration.github.installation;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link GithubInstallationUnbound}.
 *
 * <p>Workspace-agnostic — the rows in this table exist precisely BEFORE any workspace
 * has claimed them. Per-workspace queries are not applicable.
 */
@Repository
@WorkspaceAgnostic("Pre-workspace bootstrap — no workspace has claimed the installation yet")
public interface GithubInstallationUnboundRepository extends JpaRepository<GithubInstallationUnbound, Long> {

    /** Used by the daily cleanup job to find expired rows. */
    List<GithubInstallationUnbound> findByExpiresAtBefore(Instant now);

    /** Convenience lookup for the bootstrap UI (admin types an org login to claim). */
    List<GithubInstallationUnbound> findByAccountLoginIgnoreCase(String login);
}
