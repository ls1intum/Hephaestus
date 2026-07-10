package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.activity.ActivityEventRepository;
import de.tum.cit.aet.hephaestus.activity.ActivityEventType;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for workspace purge (deletion) covering data cleanup completeness,
 * idempotency, shared entity protection, credential clearing, and authorization.
 */
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

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    // Helpers

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
                de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
                "glpat-purge-test-token",
                null
            )
        );

        // GitLab webhook ids + Slack credentials live on Connection rows now (the
        // Connection registry); the legacy Workspace columns are scheduled for removal.
        // The PAT was already encrypted onto the GitLab Connection by the workspace
        // creation request above, which is what the credential-clearing assertion below
        // exercises.

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
            1L
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

    // Data cleanup completeness

    @Nested
    class DataCleanup {

        @Test
        void purgeDeletesAllWorkspaceScopedData() {
            User owner = persistUser("cleanup-owner");
            Workspace workspace = workspaceService.createWorkspace(
                new CreateWorkspaceRequestDTO(
                    "cleanup-ws",
                    "Cleanup Test",
                    "cleanup-group",
                    AccountType.ORG,
                    owner.getId(),
                    de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
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
                1L
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

    // Idempotency

    @Nested
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
                    de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
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

    // Shared entity protection

    @Nested
    class SharedEntityProtection {

        @Test
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

    // Credential and sensitive field clearing

    @Nested
    class SensitiveFieldClearing {

        @Autowired
        private de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository connectionRepository;

        /**
         * Per-workspace credentials now live on the {@code Connection} aggregate (PAT,
         * Slack token, GitLab webhook ids). On workspace purge,
         * {@code ConnectionPurgeContributor} transitions every still-ACTIVE Connection to
         * {@code UNINSTALLED}, which clears the encrypted credential blob inside the
         * same transaction. We assert the post-purge state of those rows directly here
         * — the legacy {@code Workspace.personal_access_token / slack_token / …} columns
         * no longer carry runtime state.
         */
        @Test
        void purgeClearsCredentialBlobsOnConnections() {
            Workspace workspace = createGitLabWorkspaceWithData("sensitive");
            Long workspaceId = workspace.getId();

            // Pre-purge: the GitLab Connection from createGitLabWorkspaceWithData carries an
            // encrypted PAT blob and is ACTIVE.
            var connections = connectionRepository.findByWorkspaceId(workspaceId);
            assertThat(connections).isNotEmpty();
            assertThat(connections).anyMatch(
                c ->
                    c.getKind() == de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB &&
                    c.getState() == de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState.ACTIVE &&
                    c.getCredentialsEncrypted() != null
            );

            workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());

            // Post-purge: every Connection is UNINSTALLED and its credential blob is null.
            var postPurge = connectionRepository.findByWorkspaceId(workspaceId);
            assertThat(postPurge).allMatch(
                c -> c.getState() == de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState.UNINSTALLED
            );
            assertThat(postPurge).allMatch(c -> c.getCredentialsEncrypted() == null);
            assertThat(postPurge).allMatch(c -> c.getCredentialsAlg() == null);
        }
    }

    @Nested
    class ChatThreadCleanup {

        @Test
        void purgeDeletesChatThreads() {
            User owner = persistUser("chat-cleanup-owner");
            Workspace workspace = workspaceService.createWorkspace(
                new CreateWorkspaceRequestDTO(
                    "chat-cleanup-ws",
                    "Chat Cleanup",
                    "chat-cleanup-group",
                    AccountType.ORG,
                    owner.getId(),
                    de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
                    "glpat-chat-cleanup-token",
                    null
                )
            );

            ChatThread t1 = new ChatThread();
            t1.setId(UUID.randomUUID());
            t1.setTitle("Thread A");
            t1.setWorkspace(workspace);
            t1.setUser(owner);
            ChatThread t2 = new ChatThread();
            t2.setId(UUID.randomUUID());
            t2.setTitle("Thread B");
            t2.setWorkspace(workspace);
            t2.setUser(owner);
            chatThreadRepository.save(t1);
            chatThreadRepository.save(t2);

            workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());

            assertThat(chatThreadRepository.findById(t1.getId())).isEmpty();
            assertThat(chatThreadRepository.findById(t2.getId())).isEmpty();
        }
    }

    // Authorization

    @Nested
    class Authorization {

        @Test
        @WithAdminUser
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
                    de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
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
