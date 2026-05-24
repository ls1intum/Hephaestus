package de.tum.cit.aet.hephaestus.integration.github.lifecycle;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * GitHub adapter for {@link IntegrationLifecycleListener}. Receives the framework's
 * {@code onInstanceInstalled} / {@code onInstanceUninstalled} / {@code onScopeChanged}
 * / {@code onTenantRenamed} signals derived from {@code installation.*} and
 * {@code installation_repositories.*} webhooks and must (eventually) persist or
 * reconcile workspace-side state for them.
 *
 * <p>For #1198 this is a structural skeleton — bodies log only — because the existing
 * {@code WorkspaceInstallationService.createOrUpdateFromInstallation()} path in the
 * legacy {@code workspace} package still owns those mutations. Wiring this listener as
 * the canonical write path is part of the C13 migration; until then this bean exists
 * solely so {@code IntegrationFrameworkBootstrap} (and any future capability check
 * that requires a lifecycle listener per kind) sees a registered impl for
 * {@link IntegrationKind#GITHUB}.
 */
@Component
public class GithubLifecycleListener implements IntegrationLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(GithubLifecycleListener.class);

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public void onInstanceInstalled(InstanceProvisioned event) {
        // TODO(#1198 follow-up): replace WorkspaceInstallationService.createOrUpdateFromInstallation()
        // — persist Connection + scope rows from the provided InstanceProvisioned snapshot.
        log.info(
            "GitHub onInstanceInstalled (skeleton): ref={} account={} resources={}",
            event == null ? null : event.ref(),
            event == null || event.account() == null ? null : event.account().externalId(),
            event == null || event.initialResources() == null ? 0 : event.initialResources().size()
        );
    }

    @Override
    public void onInstanceUninstalled(IntegrationRef ref) {
        // TODO(#1198 follow-up): transition the Connection row to UNINSTALLED via ConnectionService.
        log.info("GitHub onInstanceUninstalled (skeleton): ref={}", ref);
    }

    @Override
    public void onScopeChanged(IntegrationRef ref, ScopeDelta delta) {
        // TODO(#1198 follow-up): reconcile workspace-side scope rows from delta.added + delta.removedExternalIds.
        log.info(
            "GitHub onScopeChanged (skeleton): ref={} added={} removed={}",
            ref,
            delta == null || delta.added() == null ? 0 : delta.added().size(),
            delta == null || delta.removedExternalIds() == null ? 0 : delta.removedExternalIds().size()
        );
    }

    @Override
    public void onTenantRenamed(IntegrationRef ref, String oldName, String newName) {
        // TODO(#1198 follow-up): update Connection.displayName + any workspace-side denormalized name.
        log.info("GitHub onTenantRenamed (skeleton): ref={} {} -> {}", ref, oldName, newName);
    }
}
