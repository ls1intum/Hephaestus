package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import de.tum.in.www1.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for workspace purge (deletion) covering data cleanup completeness,
 * idempotency, shared entity protection, credential clearing, and authorization.
 */
@AutoConfigureWebTestClient
@DisplayName("Workspace purge integration")
class WorkspacePurgeIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceLifecycleService workspaceLifecycleService;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private WorkspaceSlugHistoryRepository workspaceSlugHistoryRepository;

    @Autowired
    private ActivityEventRepository activityEventRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a GitLab workspace with typical associated data for purge testing.
     */
    private Workspace createGitLabWorkspaceWithData(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = workspaceService.createWorkspace(
            new CreateWorkspaceRequestDTO(
                slug,
                "Purge Test " + slug,
                slug + "-group",
                AccountType.ORG,
                owner.getId(),
                Workspace.GitProviderMode.GITLAB_PAT,
                "glpat-purge-test-token",
                null
            )
        );

        // Set GitLab webhook fields (simulates a registered webhook)
        workspace.setGitlabGroupId(42L);
        workspace.setGitlabWebhookId(99L);
        workspace.setSlackToken("xoxb-test-slack-token");
        workspace.setSlackSigningSecret("test-signing-secret");
        workspace = workspaceRepository.save(workspace);

        // Add a monitored repository (saved directly — purge reloads workspace with EAGER fetch)
        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setNameWithOwner(slug + "-group/my-repo");
        monitor.setWorkspace(workspace);
        repositoryToMonitorRepository.save(monitor);

        // Add slug history entry
        WorkspaceSlugHistory slugHistory = new WorkspaceSlugHistory();
        slugHistory.setWorkspace(workspace);
        slugHistory.setOldSlug("old-" + slug);
        slugHistory.setNewSlug(slug);
        slugHistory.setChangedAt(Instant.now());
        workspaceSlugHistoryRepository.save(slugHistory);

        // Add an activity event
        activityEventRepository.insertIfAbsent(
            UUID.randomUUID(),
            "test:1:" + System.currentTimeMillis(),
            ActivityEventType.REVIEW_COMMENTED.name(),
            Instant.now(),
            owner.getId(),
            workspace.getId(),
            null,
            "pull_request",
            1L,
            1.5
        );

        // Link organization
        GitProvider provider = ensureGitLabProvider();
        Organization org = new Organization();
        org.setNativeId(42L);
        org.setProvider(provider);
        org.setLogin(slug + "-group");
        org.setName("Test Org " + slug);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org = organizationRepository.save(org);
        workspace.setOrganization(org);
        workspace = workspaceRepository.save(workspace);

        // Ensure OWNER membership for the "admin" test user (for HTTP endpoint tests)
        ensureOwnerMembership(workspace);

        return workspace;
    }

    // -------------------------------------------------------------------------
    // Data cleanup completeness
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Data cleanup")
    class DataCleanup {

        @Test
        @DisplayName("purge deletes all workspace-scoped data and marks workspace as PURGED")
        void purgeDeletesAllWorkspaceScopedData() {
            User owner = persistUser("cleanup-owner");
            Workspace workspace = workspaceService.createWorkspace(
                new CreateWorkspaceRequestDTO(
                    "cleanup-ws",
                    "Cleanup Test",
                    "cleanup-group",
                    AccountType.ORG,
                    owner.getId(),
                    Workspace.GitProviderMode.GITLAB_PAT,
                    "glpat-cleanup-token",
                    null
                )
            );
            Long workspaceId = workspace.getId();

            // Add a monitored repository (saved directly — purge reloads workspace with EAGER fetch)
            RepositoryToMonitor monitor = new RepositoryToMonitor();
            monitor.setNameWithOwner("cleanup-group/repo");
            monitor.setWorkspace(workspace);
            repositoryToMonitorRepository.save(monitor);

            // Add slug history
            WorkspaceSlugHistory slugHistory = new WorkspaceSlugHistory();
            slugHistory.setWorkspace(workspace);
            slugHistory.setOldSlug("old-cleanup");
            slugHistory.setNewSlug("cleanup-ws");
            slugHistory.setChangedAt(Instant.now());
            workspaceSlugHistoryRepository.save(slugHistory);

            // Add activity event
            activityEventRepository.insertIfAbsent(
                UUID.randomUUID(),
                "test:cleanup:" + System.currentTimeMillis(),
                ActivityEventType.REVIEW_COMMENTED.name(),
                Instant.now(),
                owner.getId(),
                workspaceId,
                null,
                "pull_request",
                1L,
                1.0
            );

            // Purge
            workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());

            // Verify status
            Workspace purged = workspaceRepository.findById(workspaceId).orElseThrow();
            assertThat(purged.getStatus()).isEqualTo(Workspace.WorkspaceStatus.PURGED);

            // Verify all workspace-scoped data deleted
            assertThat(repositoryToMonitorRepository.findByWorkspaceId(workspaceId)).isEmpty();
            assertThat(workspaceSlugHistoryRepository.findByWorkspaceOrderByChangedAtDesc(purged)).isEmpty();
            assertThat(workspaceMembershipRepository.findByWorkspace_Id(workspaceId)).isEmpty();
            assertThat(activityEventRepository.countByWorkspaceId(workspaceId)).isZero();
        }
    }

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("purging an already-purged workspace is a no-op")
        void purgeIsIdempotent() {
            User owner = persistUser("idempotent-owner");
            Workspace workspace = workspaceService.createWorkspace(
                new CreateWorkspaceRequestDTO(
                    "idempotent-ws",
                    "Idempotent Test",
                    "idempotent-group",
                    AccountType.ORG,
                    owner.getId(),
                    Workspace.GitProviderMode.GITLAB_PAT,
                    "glpat-idempotent-token",
                    null
                )
            );

            // First purge
            Workspace first = workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());
            assertThat(first.getStatus()).isEqualTo(Workspace.WorkspaceStatus.PURGED);

            // Second purge — should be no-op, no exception
            Workspace second = workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());
            assertThat(second.getStatus()).isEqualTo(Workspace.WorkspaceStatus.PURGED);
            assertThat(second.getId()).isEqualTo(first.getId());
        }
    }

    // -------------------------------------------------------------------------
    // Shared entity protection
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Shared entity protection")
    class SharedEntityProtection {

        @Test
        @DisplayName("purge unlinks but preserves the Organization entity")
        void purgePreservesOrganization() {
            Workspace workspace = createGitLabWorkspaceWithData("org-protect");
            Long workspaceId = workspace.getId();
            Long orgId = workspace.getOrganization().getId();

            workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());

            // Workspace org link cleared
            Workspace purged = workspaceRepository.findById(workspaceId).orElseThrow();
            assertThat(purged.getOrganization()).isNull();

            // Organization entity still exists
            assertThat(organizationRepository.findById(orgId)).isPresent();
        }
    }

    // -------------------------------------------------------------------------
    // Credential and sensitive field clearing
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Sensitive field clearing")
    class SensitiveFieldClearing {

        @Test
        @DisplayName("purge clears PAT, Slack tokens, and GitLab webhook fields")
        void purgeClearsSensitiveFields() {
            Workspace workspace = createGitLabWorkspaceWithData("sensitive");
            Long workspaceId = workspace.getId();

            // Verify fields set before purge
            assertThat(workspace.getPersonalAccessToken()).isNotNull();
            assertThat(workspace.getSlackToken()).isNotNull();
            assertThat(workspace.getSlackSigningSecret()).isNotNull();
            assertThat(workspace.getGitlabGroupId()).isNotNull();
            assertThat(workspace.getGitlabWebhookId()).isNotNull();

            workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());

            Workspace purged = workspaceRepository.findById(workspaceId).orElseThrow();
            assertThat(purged.getPersonalAccessToken()).isNull();
            assertThat(purged.getSlackToken()).isNull();
            assertThat(purged.getSlackSigningSecret()).isNull();
            assertThat(purged.getGitlabGroupId()).isNull();
            assertThat(purged.getGitlabWebhookId()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Authorization
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        @WithAdminUser
        @DisplayName("OWNER can purge workspace via DELETE endpoint")
        void ownerCanPurge() {
            Workspace workspace = createGitLabWorkspaceWithData("owner-purge");
            // ensureOwnerMembership already called in createGitLabWorkspaceWithData

            webTestClient
                .delete()
                .uri("/workspaces/{workspaceSlug}", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNoContent();

            Workspace purged = workspaceRepository.findById(workspace.getId()).orElseThrow();
            assertThat(purged.getStatus()).isEqualTo(Workspace.WorkspaceStatus.PURGED);
        }

        @Test
        @WithMentorUser
        @DisplayName("non-owner is denied access to DELETE endpoint")
        void nonOwnerIsDeniedAccess() {
            // Create workspace with a different owner — mentor user is NOT the owner
            User owner = persistUser("non-owner-test-owner");
            Workspace workspace = workspaceService.createWorkspace(
                new CreateWorkspaceRequestDTO(
                    "non-owner-ws",
                    "Non-Owner Test",
                    "non-owner-group",
                    AccountType.ORG,
                    owner.getId(),
                    Workspace.GitProviderMode.GITLAB_PAT,
                    "glpat-non-owner-token",
                    null
                )
            );

            // Mentor user has MEMBER role (not OWNER)
            User mentorUser = persistUser("mentor");
            ensureWorkspaceMembership(workspace, mentorUser, WorkspaceMembership.WorkspaceRole.MEMBER);

            webTestClient
                .delete()
                .uri("/workspaces/{workspaceSlug}", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();

            // Workspace should still be ACTIVE (purge was denied)
            Workspace unchanged = workspaceRepository.findById(workspace.getId()).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE);
        }
    }
}
