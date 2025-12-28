package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import java.util.List;

/**
 * Handles workspace provisioning in response to GitHub App installation events.
 */
public interface WorkspaceProvisioningListener {
    void onInstallationCreated(InstallationData installation);

    void onInstallationDeleted(Long installationId);

    void onRepositoriesAdded(Long installationId, List<String> repositoryNames);

    void onRepositoriesRemoved(Long installationId, List<String> repositoryNames);

    void onAccountRenamed(Long installationId, String oldLogin, String newLogin);

    void onInstallationSuspended(Long installationId);

    void onInstallationActivated(Long installationId);

    void onRepositorySelectionChanged(Long installationId, String selection);

    record InstallationData(
        Long installationId,
        String accountLogin,
        AccountType accountType,
        String avatarUrl,
        List<String> repositoryNames
    ) {}

    enum AccountType {
        ORGANIZATION,
        USER;

        public static AccountType fromGitHubType(String type) {
            return "Organization".equalsIgnoreCase(type) ? ORGANIZATION : USER;
        }
    }
}
