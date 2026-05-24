package de.tum.cit.aet.hephaestus.integration.spi;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Per-kind lifecycle hooks bridging vendor events to Connection state transitions
 * and scope updates.
 *
 * <p>Collapses the 8-method {@code ProvisioningListener} (GitHub-shaped) to 4.
 * State transitions ({@code SUSPENDED}, {@code UNINSTALLED}) flow via the
 * {@code WorkspaceIntegrationService.transition()} call, not through additional
 * listener methods — keeps the abstraction symmetric across vendors.
 *
 * <p>Per-vendor mapping examples:
 * <ul>
 *   <li>GitHub {@code installation.created} → {@link #onInstanceInstalled}
 *   <li>GitHub {@code installation_repositories.added} → {@link #onScopeChanged}
 *   <li>GitHub {@code installation.deleted} → {@link #onInstanceUninstalled}
 *   <li>Slack OAuth callback → {@link #onInstanceInstalled} (initialResources empty)
 *   <li>Slack {@code member_joined_channel} → {@link #onScopeChanged}
 *   <li>Slack {@code app_uninstalled} or {@code tokens_revoked} → {@link #onInstanceUninstalled}
 *   <li>Outline {@code collections.create} → {@link #onScopeChanged}
 * </ul>
 */
public interface IntegrationLifecycleListener {

    IntegrationKind kind();

    void onInstanceInstalled(InstanceProvisioned event);

    void onInstanceUninstalled(IntegrationRef ref);

    void onScopeChanged(IntegrationRef ref, ScopeDelta delta);

    void onTenantRenamed(IntegrationRef ref, String oldName, String newName);

    record InstanceProvisioned(
        IntegrationRef ref,
        TenantAccount account,
        List<ScopedResource> initialResources
    ) {
    }

    record TenantAccount(
        String externalId,
        String displayName,
        AccountKind kind,
        @Nullable String avatarUrl
    ) {
    }

    enum AccountKind {
        ORGANIZATION,
        USER,
        TEAM_WORKSPACE
    }

    record ScopedResource(
        String externalId,
        String displayName,
        ResourceKind kind,
        boolean isPrivate
    ) {
    }

    enum ResourceKind {
        REPOSITORY,
        CHANNEL,
        COLLECTION,
        NAMESPACE
    }

    record ScopeDelta(List<ScopedResource> added, List<String> removedExternalIds) {
    }
}
