package de.tum.cit.aet.hephaestus.integration.gitlab.lifecycle;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Skeleton lifecycle listener for GitLab Connections.
 *
 * <p>Unlike GitHub Apps (which fire {@code installation.created},
 * {@code installation_repositories.added}, {@code installation.deleted} webhooks)
 * GitLab has no install/uninstall webhooks. Lifecycle is admin-driven via the
 * Connection CRUD endpoints — the orchestrator calls
 * {@code ConnectionService.transition()} directly when an admin connects, suspends,
 * or revokes.
 *
 * <p>This bean exists to satisfy the per-kind listener contract (any vendor with a
 * webhook footprint should be able to receive scope-change events even if they
 * never fire). All methods log and return; the real listener wiring ships when the
 * lifecycle endpoints land in the follow-up.
 *
 * <p>TODO(#1198 follow-up): wire up the admin-triggered transition path
 * (Connection CRUD endpoints + matching {@code ConnectionService.transition()} calls).
 */
@Component
public class GitlabLifecycleListener implements IntegrationLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(GitlabLifecycleListener.class);

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public void onInstanceInstalled(InstanceProvisioned event) {
        log.info("GitLab onInstanceInstalled (admin-driven; no-op stub): ref={}, account={}",
            event == null ? null : event.ref(),
            event == null ? null : event.account());
    }

    @Override
    public void onInstanceUninstalled(IntegrationRef ref) {
        log.info("GitLab onInstanceUninstalled (admin-driven; no-op stub): ref={}", ref);
    }

    @Override
    public void onScopeChanged(IntegrationRef ref, ScopeDelta delta) {
        log.info("GitLab onScopeChanged (no webhook source; no-op stub): ref={}, delta={}", ref, delta);
    }

    @Override
    public void onTenantRenamed(IntegrationRef ref, String oldName, String newName) {
        log.info("GitLab onTenantRenamed (no-op stub): ref={}, {} → {}", ref, oldName, newName);
    }
}
