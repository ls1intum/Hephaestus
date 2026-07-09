package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.activity.ActivityEventRepository;
import de.tum.cit.aet.hephaestus.activity.ActivityEventType;
import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorSlackThread;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorSlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackParticipantConsentRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThread;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.retention.SlackRetentionSweeper;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private SlackMessageRepository slackMessageRepository;

    @Autowired
    private SlackThreadRepository slackThreadRepository;

    @Autowired
    private SlackMonitoredChannelRepository slackMonitoredChannelRepository;

    @Autowired
    private MentorSlackThreadRepository mentorSlackThreadRepository;

    @Autowired
    private SlackParticipantConsentRepository slackParticipantConsentRepository;

    @Autowired
    private SlackRetentionSweeper slackRetentionSweeper;

    @Autowired
    private de.tum.cit.aet.hephaestus.integration.slack.retention.SlackWorkspacePurgeAdapter slackWorkspacePurgeAdapter;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackObservationRepository feedbackObservationRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private static final tools.jackson.databind.ObjectMapper OM = new tools.jackson.databind.ObjectMapper();

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
            1L,
            1.5
        );

        // Link organization
        IdentityProvider provider = ensureGitLabProvider();
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

    // Slack purge + retention

    @Nested
    class SlackCleanup {

        private Workspace createBareWorkspace(String slug) {
            User owner = persistUser(slug + "-owner");
            return workspaceService.createWorkspace(
                new CreateWorkspaceRequestDTO(
                    slug,
                    "Slack Test " + slug,
                    slug + "-group",
                    AccountType.ORG,
                    owner.getId(),
                    de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
                    "glpat-slack-test-token",
                    null
                )
            );
        }

        private static String slackTs(Instant instant) {
            return String.format("%010d.000000", instant.getEpochSecond());
        }

        private void insertThread(Long workspaceId, String slackChannelId, String slackThreadTs, String lastTs) {
            SlackThread thread = new SlackThread();
            thread.setWorkspaceId(workspaceId);
            thread.setSlackChannelId(slackChannelId);
            thread.setSlackThreadTs(slackThreadTs);
            thread.setFirstTs(lastTs);
            thread.setLastTs(lastTs);
            thread.setMessageCount(1);
            slackThreadRepository.save(thread);
        }

        /** Insert one ingested Slack message with a controlled {@code ingested_at} (native — bypasses @CreationTimestamp). */
        private void insertMessage(Long workspaceId, String slackTs, String slackThreadTs, Instant ingestedAt) {
            jdbcTemplate.update(
                "INSERT INTO slack_message (workspace_id, slack_team_id, slack_channel_id, slack_ts, slack_thread_ts, ingested_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                workspaceId,
                "T1",
                "C1",
                slackTs,
                slackThreadTs,
                Timestamp.from(ingestedAt)
            );
        }

        /** Seed one row into each of the three non-message Slack tables for the workspace. */
        private void seedAggregates(Workspace workspace, String channelId, String threadTs) {
            Long workspaceId = workspace.getId();
            SlackThread thread = new SlackThread();
            thread.setWorkspaceId(workspaceId);
            thread.setSlackChannelId(channelId);
            thread.setSlackThreadTs(threadTs);
            slackThreadRepository.save(thread);

            SlackMonitoredChannel channel = new SlackMonitoredChannel();
            channel.setWorkspaceId(workspaceId);
            channel.setSlackTeamId("T1");
            channel.setSlackChannelId(channelId);
            channel.setConsentState(SlackMonitoredChannel.ConsentState.PENDING);
            slackMonitoredChannelRepository.save(channel);

            // mentor_slack_thread.chat_thread_id is a NOT NULL FK → chat_thread(id); seed a real thread first.
            User owner = persistUser(channelId + "-mentor-owner");
            ChatThread chatThread = new ChatThread();
            chatThread.setId(UUID.randomUUID());
            chatThread.setTitle("Slack DM " + channelId);
            chatThread.setWorkspace(workspace);
            chatThread.setUser(owner);
            chatThreadRepository.save(chatThread);

            MentorSlackThread mentorThread = new MentorSlackThread();
            mentorThread.setId(UUID.randomUUID());
            mentorThread.setWorkspaceId(workspaceId);
            mentorThread.setChatThreadId(chatThread.getId());
            mentorThread.setSlackTeamId("T1");
            mentorThread.setSlackChannelId(channelId);
            mentorThread.setSlackThreadTs(threadTs);
            mentorThread.setSlackUserId("U1");
            mentorSlackThreadRepository.save(mentorThread);

            slackParticipantConsentRepository.upsert(workspaceId, "U1", true, true, "SLACK_APP_HOME");
        }

        @Test
        @DisplayName("retention sweep deletes only messages older than the default window, per workspace")
        void retentionSweepDeletesOnlyExpiredMessages() {
            // Arrange — workspace A has recent + expired threads; workspace B has only a recent thread.
            // Neither workspace has a Slack Connection, so both resolve to DEFAULT_RETENTION_DAYS (30d).
            Instant now = Instant.now();
            Workspace a = createBareWorkspace("slack-retain-a");
            Workspace b = createBareWorkspace("slack-retain-b");
            String aRecent = slackTs(now);
            String aExpired = slackTs(now.minus(Duration.ofDays(40)));
            String aVeryExpired = slackTs(now.minus(Duration.ofDays(200)));
            String bRecent = slackTs(now);
            insertThread(a.getId(), "C1", aRecent, aRecent);
            insertThread(a.getId(), "C1", aExpired, aExpired);
            insertThread(a.getId(), "C1", aVeryExpired, aVeryExpired);
            insertThread(b.getId(), "C1", bRecent, bRecent);
            insertMessage(a.getId(), aRecent, aRecent, now);
            insertMessage(a.getId(), aExpired, aExpired, now.minus(Duration.ofDays(40)));
            insertMessage(a.getId(), aVeryExpired, aVeryExpired, now.minus(Duration.ofDays(200)));
            insertMessage(b.getId(), bRecent, bRecent, now);

            // Act
            slackRetentionSweeper.sweepNow();

            // Assert — only the two expired threads in A are gone; A's recent row and all of B survive.
            assertThat(slackMessageRepository.countByWorkspaceId(a.getId())).isEqualTo(1);
            assertThat(slackMessageRepository.countByWorkspaceId(b.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("purge empties every Slack table for the workspace while other workspaces stay intact")
        void purgeEmptiesAllSlackTables() {
            // Arrange — seed all four Slack tables for both A and B.
            Instant now = Instant.now();
            Workspace a = createBareWorkspace("slack-purge-a");
            Workspace b = createBareWorkspace("slack-purge-b");
            insertMessage(a.getId(), "300.1", "300.1", now);
            insertMessage(b.getId(), "400.1", "400.1", now);
            seedAggregates(a, "CA", "300.1");
            seedAggregates(b, "CB", "400.1");

            // Act
            workspaceLifecycleService.purgeWorkspace(a.getWorkspaceSlug());

            // Assert — A's Slack rows are empty; B's rows are untouched.
            assertThat(slackMessageRepository.countByWorkspaceId(a.getId())).isZero();
            assertThat(slackThreadRepository.countByWorkspaceId(a.getId())).isZero();
            assertThat(slackMonitoredChannelRepository.countByWorkspaceId(a.getId())).isZero();
            assertThat(mentorSlackThreadRepository.countByWorkspaceId(a.getId())).isZero();
            assertThat(
                slackParticipantConsentRepository.countByWorkspaceIdAndIngestionOptedOutTrue(a.getId())
            ).isZero();

            assertThat(slackMessageRepository.countByWorkspaceId(b.getId())).isEqualTo(1);
            assertThat(slackThreadRepository.countByWorkspaceId(b.getId())).isEqualTo(1);
            assertThat(slackMonitoredChannelRepository.countByWorkspaceId(b.getId())).isEqualTo(1);
            assertThat(mentorSlackThreadRepository.countByWorkspaceId(b.getId())).isEqualTo(1);
            assertThat(
                slackParticipantConsentRepository.countByWorkspaceIdAndIngestionOptedOutTrue(b.getId())
            ).isEqualTo(1);
        }

        @Test
        @DisplayName(
            "SlackWorkspacePurgeAdapter erases the workspace's derived CONVERSATION rows; another workspace's survive"
        )
        void purgeAdapterErasesDerivedConversationRows() {
            Workspace a = createBareWorkspace("slack-conv-a");
            Workspace b = createBareWorkspace("slack-conv-b");

            // Seed a slack_thread + its derived CONVERSATION_THREAD observation/feedback for each workspace.
            SlackThread threadA = new SlackThread();
            threadA.setWorkspaceId(a.getId());
            threadA.setSlackChannelId("CA");
            threadA.setSlackThreadTs("500.1");
            threadA = slackThreadRepository.save(threadA);
            java.util.UUID convObsA = seedDerivedConversation(a, threadA.getId());

            SlackThread threadB = new SlackThread();
            threadB.setWorkspaceId(b.getId());
            threadB.setSlackChannelId("CB");
            threadB.setSlackThreadTs("600.1");
            threadB = slackThreadRepository.save(threadB);
            java.util.UUID convObsB = seedDerivedConversation(b, threadB.getId());

            // Act — drive the Slack purge contributor for A in isolation (the real chain wraps the contributors in
            // one transaction, so mirror that with a TransactionTemplate). It is the explicit
            // eraseAllConversationForWorkspace call (not the practices contributor) that must erase the derived rows
            // here, so this fails if that port call is removed from the adapter.
            org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> slackWorkspacePurgeAdapter.deleteWorkspaceData(a.getId()));

            // A's derived CONVERSATION rows are erased; B's remain intact (tenant scoping).
            assertThat(observationRepository.findById(convObsA)).isEmpty();
            assertThat(observationRepository.findById(convObsB)).isPresent();
            // Idempotent: a second contributor pass (double-delete) is a no-op.
            tx.executeWithoutResult(status -> slackWorkspacePurgeAdapter.deleteWorkspaceData(a.getId()));
            assertThat(observationRepository.findById(convObsB)).isPresent();
        }

        /** Seed a CONVERSATION_THREAD observation + feedback + join anchored to {@code threadId} for {@code workspace}. */
        private java.util.UUID seedDerivedConversation(Workspace workspace, long threadId) {
            User owner = persistUser("conv-" + workspace.getId() + "-subject");
            Practice practice = new Practice();
            practice.setWorkspace(workspace);
            practice.setSlug("conv-practice-" + workspace.getId());
            practice.setName("Conversation Practice");
            practice.setCriteria("Test description");
            practice.setTriggerEvents(OM.valueToTree(java.util.List.of("PullRequestCreated")));
            practice = practiceRepository.save(practice);

            AgentJob job = new AgentJob();
            job.setWorkspace(workspace);
            job.setJobType(AgentJobType.CONVERSATION_REVIEW);
            job.setConfigSnapshot(OM.valueToTree(java.util.Map.of("model", "test")));
            job = agentJobRepository.save(job);

            java.util.UUID observationId = java.util.UUID.randomUUID();
            observationRepository.insertIfAbsent(
                observationId,
                "occ-" + observationId,
                job.getId(),
                practice.getId(),
                null,
                WorkArtifact.CONVERSATION_THREAD.name(),
                threadId,
                owner.getId(),
                "Observation title",
                "ABSENT",
                "BAD",
                "MAJOR",
                0.8f,
                null,
                null,
                null,
                Instant.now()
            );
            Feedback feedback = feedbackRepository.save(
                Feedback.builder()
                    .agentJobId(job.getId())
                    .workspaceId(workspace.getId())
                    .artifactType(WorkArtifact.CONVERSATION_THREAD)
                    .artifactId(threadId)
                    .recipientUserId(owner.getId())
                    .aboutUserId(owner.getId())
                    .channel(FeedbackChannel.CONVERSATION)
                    .position(0)
                    .deliveryState(FeedbackDeliveryState.PREPARED)
                    .source(FeedbackSource.AGENT)
                    .createdAt(Instant.now())
                    .build()
            );
            feedbackObservationRepository.insertIfAbsent(
                feedback.getId(),
                observationId,
                EvidenceRole.PRIMARY.name(),
                0
            );
            return observationId;
        }
    }
}
