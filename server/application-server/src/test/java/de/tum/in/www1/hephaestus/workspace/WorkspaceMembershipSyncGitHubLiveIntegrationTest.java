package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.github.AbstractGitHubLiveSyncIntegrationTest;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.GitHubOrganizationSyncService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Live integration tests for organization membership synchronization.
 * <p>
 * Tests that organization members are correctly synced from GitHub.
 */
class WorkspaceMembershipSyncGitHubLiveIntegrationTest extends AbstractGitHubLiveSyncIntegrationTest {

    @Autowired
    private GitHubOrganizationSyncService orgSyncService;

    @Autowired
    private OrganizationMembershipRepository organizationMembershipRepository;

    @Test
    void syncsOrganizationMembersFromGitHub() {
        // Sync the HephaestusTest organization (our test GitHub App is installed there)
        Organization org = orgSyncService.syncOrganization(workspace.getId(), "HephaestusTest");
        assertThat(org).as("Organization should be synced from GitHub").isNotNull();
        assertThat(org.getLogin()).isEqualTo("HephaestusTest");

        // Verify org members were synced (repository stores only IDs, not full User objects)
        List<Long> orgMemberUserIds = organizationMembershipRepository.findUserIdsByOrganizationId(org.getId());
        assertThat(orgMemberUserIds).as("organization members should be synced from GitHub").isNotEmpty();

        // Verify at least one member exists (the person who installed the GitHub App)
        assertThat(orgMemberUserIds.size()).isGreaterThanOrEqualTo(1);
    }
}
