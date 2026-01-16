package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import java.util.List;

/**
 * Handles provisioning in response to GitHub App installation events.
 */
public interface ProvisioningListener {
    void onInstallationCreated(InstallationData installation);

    void onInstallationDeleted(Long installationId);

    void onRepositoriesAdded(Long installationId, List<RepositorySnapshot> repositories);

    void onRepositoriesRemoved(Long installationId, List<String> repositoryNames);

    void onAccountRenamed(Long installationId, String oldLogin, String newLogin);

    void onInstallationSuspended(Long installationId);

    void onInstallationActivated(Long installationId);

    void onRepositorySelectionChanged(Long installationId, String selection);

    /**
     * Provider-agnostic snapshot of repository metadata from webhook payloads.
     * Contains the minimal information needed to create a Repository entity.
     */
    record RepositorySnapshot(long id, String nameWithOwner, String name, boolean isPrivate) {}

    record InstallationData(
        Long installationId,
        Long accountId,
        String accountLogin,
        AccountType accountType,
        String avatarUrl,
        List<RepositorySnapshot> repositories
    ) {}

    enum AccountType {
        ORGANIZATION,
        USER,
    }
}
