package de.tum.in.www1.hephaestus.gitprovider.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueTypeRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.github.GitHubIssueTypeSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.GitHubDataSyncService;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHIssueState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Live GitHub integration tests for issue type syncing.
 *
 * These tests verify that issue types are correctly synced from GitHub
 * via both REST API and webhook events.
 *
 * Required: The test organization must have issue types enabled.
 */
@TestPropertySource(
    properties = {
        "monitoring.timeframe=7", "monitoring.sync-cooldown-in-minutes=0", "monitoring.backfill.enabled=false", // Disable backfill for focused tests
    }
)
class GitHubLiveIssueTypeSyncIntegrationTest extends AbstractGitHubLiveSyncIntegrationTest {

    @Autowired
    private GitHubDataSyncService dataSyncService;

    @Autowired
    private GitHubIssueTypeSyncService issueTypeSyncService;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private IssueTypeRepository issueTypeRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    /**
     * Helper to create and persist an organization linked to the test workspace.
     * The workspace owns the relationship, so we save the org first, then link via
     * workspace.
     */
    private Organization createAndLinkOrganization() throws Exception {
        var ghOrg = fetchOrganization();

        // Create and save the organization first
        Organization org = new Organization();
        org.setId(ghOrg.getId());
        org.setGithubId(ghOrg.getId());
        org.setLogin(ghOrg.getLogin());
        org.setAvatarUrl(ghOrg.getAvatarUrl());
        org = organizationRepository.save(org);

        // Now link the workspace to the organization (workspace owns the FK)
        workspace.setOrganization(org);
        workspaceRepository.save(workspace);

        return org;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ISSUE TYPE SYNC TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Issue Type Workspace Sync")
    class IssueTypeWorkspaceSync {

        @Test
        @DisplayName("Sync workspace issue types via GraphQL API")
        void syncWorkspaceIssueTypesViaGraphQL() throws Exception {
            // Arrange - Create and link organization to workspace
            Organization org = createAndLinkOrganization();

            // Act - Sync issue types via the workspace sync method
            int synced = issueTypeSyncService.syncIssueTypesForWorkspace(workspace.getId());

            // Assert - Issue types should be synced
            var issueTypes = issueTypeRepository.findAllByOrganizationId(org.getId());

            // HephaestusTest org should have default issue types (Task, Bug, Feature)
            assertThat(issueTypes).as("Organization should have issue types after sync").isNotEmpty();

            assertThat(synced).as("Should return positive count for synced types").isGreaterThan(0);

            // Check for expected default types
            var typeNames = issueTypes.stream().map(it -> it.getName()).toList();

            assertThat(typeNames)
                .as("Should contain default GitHub issue types")
                .containsAnyOf("Task", "Bug", "Feature");

            // Verify types have proper fields
            for (var issueType : issueTypes) {
                assertThat(issueType.getId()).as("Issue type should have id (node_id)").isNotBlank();
                assertThat(issueType.getName()).as("Issue type should have name").isNotBlank();
                assertThat(issueType.getOrganization()).as("Issue type should be linked to organization").isNotNull();
            }
        }

        @Test
        @DisplayName("Issue types are idempotent on re-sync")
        void issueTypesIdempotentOnReSync() throws Exception {
            // Arrange - Create and link organization to workspace
            Organization org = createAndLinkOrganization();

            // Act - Sync twice
            issueTypeSyncService.syncIssueTypesForWorkspace(workspace.getId());
            int countAfterFirst = issueTypeRepository.findAllByOrganizationId(org.getId()).size();

            // Wait a bit before second sync
            Thread.sleep(100);

            issueTypeSyncService.syncIssueTypesForWorkspace(workspace.getId());
            int countAfterSecond = issueTypeRepository.findAllByOrganizationId(org.getId()).size();

            // Assert - Count should be the same
            assertThat(countAfterSecond)
                .as("Re-sync should not create duplicate issue types")
                .isEqualTo(countAfterFirst);
        }
    }

    @Nested
    @DisplayName("Issue Type with Issue Sync")
    class IssueTypeWithIssueSync {

        @Test
        @DisplayName("Issues synced via REST complete without error")
        void issuesSyncedViaRestCompleteWithoutError() throws Exception {
            // Arrange - Create a repository with an issue
            var ghRepository = createEphemeralRepository("issue-type");
            var createdIssue = createIssueWithComment(ghRepository);
            var monitor = registerRepositoryToMonitor(ghRepository);

            // Wait for issue visibility
            awaitCondition("issue visible in API", () -> {
                var issues = ghRepository.queryIssues().state(GHIssueState.ALL).list().toList();
                return issues.size() >= 1;
            });

            // Act - Sync the repository
            dataSyncService.syncRepositoryToMonitor(monitor);

            // Assert - Issue should be synced
            var repoId = repositoryRepository.findByNameWithOwner(ghRepository.getFullName()).orElseThrow().getId();

            awaitCondition("issue synced", () -> {
                Set<Integer> synced = issueRepository.findAllSyncedIssueNumbers(repoId);
                return synced.contains(createdIssue.issueNumber());
            });

            // Verify the issue was synced
            var syncedNumbers = issueRepository.findAllSyncedIssueNumbers(repoId);
            assertThat(syncedNumbers).as("Issue should be synced successfully").contains(createdIssue.issueNumber());
        }
    }
}
