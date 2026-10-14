package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationMembershipRepository;
import de.tum.cit.aet.hephaestus.integration.scm.github.AbstractGitHubLiveSyncIntegrationTest;
import de.tum.cit.aet.hephaestus.integration.scm.github.organization.GitHubOrganizationSyncService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MembershipSyncGitHubLiveIntegrationTest extends AbstractGitHubLiveSyncIntegrationTest {

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

        List<Long> orgMemberUserIds = organizationMembershipRepository.findUserIdsByOrganizationId(org.getId());
        assertThat(orgMemberUserIds)
                .as("organization members should be synced from GitHub")
                .isNotEmpty();

        assertThat(orgMemberUserIds.size()).isGreaterThanOrEqualTo(1);
    }
}
