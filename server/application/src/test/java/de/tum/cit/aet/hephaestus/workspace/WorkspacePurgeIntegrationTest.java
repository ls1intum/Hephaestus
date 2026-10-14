package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.tum.cit.aet.hephaestus.activity.ActivityEventRepository;
import de.tum.cit.aet.hephaestus.activity.ActivityEventType;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJob;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobStatus;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobTrigger;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
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
import de.tum.cit.aet.hephaestus.integration.slack.retention.SlackWorkspacePurgeAdapter;
import de.tum.cit.aet.hephaestus.mentor.ChatMessage;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackBasisRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopy;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.testconfig.SharedTestDoubles.LateFailingWorkspacePurgeContributor;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeBlockedException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for workspace purge (deletion) covering data cleanup completeness, idempotency,
 * shared entity protection, credential clearing, and authorization.
 */
class WorkspacePurgeIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    private PracticeModelTestFixtures modelFixtures;

    @Autowired
    private LateFailingWorkspacePurgeContributor lateFailingContributor;

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
    private ChatMessageRepository chatMessageRepository;

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
    private SlackWorkspacePurgeAdapter slackWorkspacePurgeAdapter;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private ResultRepository practiceResultRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackBasisRepository feedbackEvidenceRepository;

    @Autowired
    private FeedbackCopyRepository feedbackPlacementRepository;

    @Autowired
    private ReviewJobRepository agentJobRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private SyncJobRepository syncJobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final ObjectMapper OM = new ObjectMapper();

    /** Creates a GitLab workspace with typical associated data for purge testing. */
    private Workspace createGitLabWorkspaceWithData(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = workspaceService.createWorkspace(new CreateWorkspaceRequestDTO(
                slug,
                "Purge Test " + slug,
                slug + "-group",
                AccountType.ORG,
                owner.getId(),
                IntegrationKind.GITLAB,
                "glpat-purge-test-token",
                null));

        // The workspace creation request above encrypted the PAT onto the GitLab Connection, which
        // is what
        // the credential-clearing assertion below exercises.

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
                1L);

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
        workspace.setOrganizationId(org.getId());
        workspace = workspaceRepository.save(workspace);

        // Ensure OWNER membership for the "admin" test user (for HTTP endpoint tests)
        ensureOwnerMembership(workspace);

        return workspace;
    }

    @Test
    void laterContributorFailureRollsBackLocalDataAndCredentialErasure() {
        Workspace workspace = createGitLabWorkspaceWithData("rollback-ws");
        Long workspaceId = workspace.getId();
        assertThat(activityEventRepository.countByWorkspaceId(workspaceId)).isPositive();

        lateFailingContributor.setFail(true);
        try {
            assertThatThrownBy(() -> workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("late purge failure");
        } finally {
            lateFailingContributor.setFail(false);
        }

        Workspace reloaded = workspaceRepository.findById(workspaceId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE);
        assertThat(activityEventRepository.countByWorkspaceId(workspaceId)).isPositive();
        assertThat(connectionRepository.findByWorkspaceId(workspaceId))
                .singleElement()
                .satisfies(connection -> {
                    assertThat(connection.getState()).isEqualTo(IntegrationState.ACTIVE);
                    assertThat(connection.getCredentialsEncrypted()).isNotNull();
                });
    }

    @Nested
    class DataCleanup {

        @Test
        void purgeRemovesTheProductRowsTheWorkspaceOwnsAndKeepsInstanceWideSurveys() {
            Workspace workspace = createGitLabWorkspaceWithData("product-rows");
            Long workspaceId = workspace.getId();
            long accountId = Objects.requireNonNull(
                    accountRepository.save(new Account("Product purge")).getId());
            UUID targeted = seedSurvey(workspaceId);
            UUID instanceWide = seedSurvey(null);
            UUID targetedParticipation = seedParticipation(targeted, accountId, workspaceId);
            UUID feedback = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO product_feedback (id, account_id, workspace_id, kind, message,"
                            + " app_version, created_at, submission_minute) VALUES (?, ?, ?,"
                            + " 'FEEDBACK', 'From the purged workspace', 'test', now(), now())",
                    feedback,
                    accountId,
                    workspaceId);

            workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());

            assertThat(jdbcTemplate.queryForList(
                            "SELECT id FROM product_survey WHERE id IN (?, ?)", UUID.class, targeted, instanceWide))
                    .containsExactly(instanceWide);
            assertThat(jdbcTemplate.queryForList(
                            "SELECT id FROM product_survey_participation WHERE id = ?",
                            UUID.class,
                            targetedParticipation))
                    .isEmpty();
            assertThat(jdbcTemplate.queryForList("SELECT id FROM product_feedback WHERE id = ?", UUID.class, feedback))
                    .isEmpty();
        }

        private UUID seedSurvey(@Nullable Long workspaceId) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO product_survey (id, title, description, questions_json,"
                            + " workspace_id, starts_at, active, created_at) VALUES (?, 'Survey',"
                            + " 'Description', CAST('[]' AS jsonb), ?, now(), true, now())",
                    id,
                    workspaceId);
            return id;
        }

        private UUID seedParticipation(UUID surveyId, long accountId, Long workspaceId) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO product_survey_participation (id, survey_id, account_id,"
                            + " workspace_id, status, invited_at) VALUES (?, ?, ?, ?, 'INVITED',"
                            + " now())",
                    id,
                    surveyId,
                    accountId,
                    workspaceId);
            return id;
        }

        @Test
        void purgeDeletesAllWorkspaceScopedData() {
            User owner = persistUser("cleanup-owner");
            Workspace workspace = workspaceService.createWorkspace(new CreateWorkspaceRequestDTO(
                    "cleanup-ws",
                    "Cleanup Test",
                    "cleanup-group",
                    AccountType.ORG,
                    owner.getId(),
                    IntegrationKind.GITLAB,
                    "glpat-cleanup-token",
                    null));
            Long workspaceId = workspace.getId();

            // Add a monitored repository (saved directly — purge reloads workspace with EAGER
            // fetch)
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
                    1L);

            // Purge
            workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());

            Workspace purged = workspaceRepository.findById(workspaceId).orElseThrow();
            assertThat(purged.getStatus()).isEqualTo(Workspace.WorkspaceStatus.PURGED);

            assertThat(repositoryToMonitorRepository.findByWorkspaceId(workspaceId))
                    .isEmpty();
            assertThat(workspaceSlugHistoryRepository.findByWorkspaceOrderByChangedAtDesc(purged))
                    .isEmpty();
            assertThat(workspaceMembershipRepository.findByWorkspace_Id(workspaceId))
                    .isEmpty();
            assertThat(activityEventRepository.countByWorkspaceId(workspaceId)).isZero();
        }
    }

    @Nested
    class Idempotency {

        @Test
        @DisplayName("purging an already-purged workspace is a no-op")
        void purgeIsIdempotent() {
            User owner = persistUser("idempotent-owner");
            Workspace workspace = workspaceService.createWorkspace(new CreateWorkspaceRequestDTO(
                    "idempotent-ws",
                    "Idempotent Test",
                    "idempotent-group",
                    AccountType.ORG,
                    owner.getId(),
                    IntegrationKind.GITLAB,
                    "glpat-idempotent-token",
                    null));

            Workspace first = workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());
            assertThat(first.getStatus()).isEqualTo(Workspace.WorkspaceStatus.PURGED);

            // Second purge — should be no-op, no exception
            Workspace second = workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());
            assertThat(second.getStatus()).isEqualTo(Workspace.WorkspaceStatus.PURGED);
            assertThat(second.getId()).isEqualTo(first.getId());
        }
    }

    @Nested
    class Quiescence {

        @Test
        void purgeRefusesActiveIntegrationSync() {
            Workspace workspace = createGitLabWorkspaceWithData("active-sync");
            var connection =
                    connectionRepository.findByWorkspaceId(workspace.getId()).getFirst();
            SyncJob job = new SyncJob(
                    workspace,
                    connection,
                    connection.getKind(),
                    SyncJobType.RECONCILIATION,
                    SyncJobTrigger.MANUAL,
                    null);
            job.setStatus(SyncJobStatus.RUNNING);
            syncJobRepository.saveAndFlush(job);

            assertThatThrownBy(() -> workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug()))
                    .isInstanceOf(WorkspacePurgeBlockedException.class)
                    .hasMessageContaining("active integration sync");

            assertThat(workspaceRepository
                            .findById(workspace.getId())
                            .orElseThrow()
                            .getStatus())
                    .isEqualTo(Workspace.WorkspaceStatus.ACTIVE);
        }

        @Test
        void purgeRefusesQueuedAgentWork() {
            Workspace workspace = createGitLabWorkspaceWithData("queued-agent");
            var work = modelFixtures.work(workspace, "queued-work-" + workspace.getId(), "scm.pull_request", 1L);
            var job = work.job();
            job.setStatus(ReviewJobStatus.QUEUED);
            agentJobRepository.saveAndFlush(job);

            assertThatThrownBy(() -> workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug()))
                    .isInstanceOf(WorkspacePurgeBlockedException.class)
                    .hasMessageContaining("AI runs are queued");

            assertThat(workspaceRepository
                            .findById(workspace.getId())
                            .orElseThrow()
                            .getStatus())
                    .isEqualTo(Workspace.WorkspaceStatus.ACTIVE);
        }
    }

    @Nested
    class SharedEntityProtection {

        @Test
        void purgePreservesOrganization() {
            Workspace workspace = createGitLabWorkspaceWithData("org-protect");
            Long workspaceId = workspace.getId();
            Long orgId = workspace.getOrganizationId();
            assertNotNull(orgId);

            workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());

            // Workspace org link cleared
            Workspace purged = workspaceRepository.findById(workspaceId).orElseThrow();
            assertThat(purged.getOrganizationId()).isNull();

            // Organization entity still exists
            assertThat(organizationRepository.findById(orgId)).isPresent();
        }
    }

    @Nested
    class SensitiveFieldClearing {

        /**
         * Per-workspace credentials live on the {@code Connection} aggregate. On workspace purge,
         * {@code ConnectionPurgeContributor} transitions every still-ACTIVE Connection to {@code
         * UNINSTALLED}, which clears the encrypted credential blob inside the same transaction.
         */
        @Test
        void purgeClearsCredentialBlobsOnConnections() {
            Workspace workspace = createGitLabWorkspaceWithData("sensitive");
            Long workspaceId = workspace.getId();

            // Pre-purge: the GitLab Connection from createGitLabWorkspaceWithData carries an
            // encrypted PAT blob and is ACTIVE.
            var connections = connectionRepository.findByWorkspaceId(workspaceId);
            assertThat(connections).isNotEmpty();
            assertThat(connections)
                    .anyMatch(c -> c.getKind() == IntegrationKind.GITLAB
                            && c.getState() == IntegrationState.ACTIVE
                            && c.getCredentialsEncrypted() != null);

            workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());

            // Post-purge: every Connection is UNINSTALLED and its credential blob is null.
            var postPurge = connectionRepository.findByWorkspaceId(workspaceId);
            assertThat(postPurge).allMatch(c -> c.getState() == IntegrationState.UNINSTALLED);
            assertThat(postPurge).allMatch(c -> c.getCredentialsEncrypted() == null);
            assertThat(postPurge).allMatch(c -> c.getCredentialsAlg() == null);
        }
    }

    @Nested
    class ChatThreadCleanup {

        @Test
        void purgeDeletesChatThreads() {
            User owner = persistUser("chat-cleanup-owner");
            Workspace workspace = workspaceService.createWorkspace(new CreateWorkspaceRequestDTO(
                    "chat-cleanup-ws",
                    "Chat Cleanup",
                    "chat-cleanup-group",
                    AccountType.ORG,
                    owner.getId(),
                    IntegrationKind.GITLAB,
                    "glpat-chat-cleanup-token",
                    null));

            ChatThread t1 = new ChatThread();
            t1.setId(UUID.randomUUID());
            t1.setTitle("Thread A");
            t1.setWorkspace(workspace);
            t1.setAccountId(accountId(owner));
            ChatThread t2 = new ChatThread();
            t2.setId(UUID.randomUUID());
            t2.setTitle("Thread B");
            t2.setWorkspace(workspace);
            t2.setAccountId(accountId(owner));
            chatThreadRepository.save(t1);
            chatThreadRepository.save(t2);

            workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());

            assertThat(chatThreadRepository.findById(t1.getId())).isEmpty();
            assertThat(chatThreadRepository.findById(t2.getId())).isEmpty();
        }
    }

    @Test
    @WithAdminUser
    void shouldAllowOwnerToPurgeWorkspace() {
        Workspace workspace = createGitLabWorkspaceWithData("owner-purge");

        webTestClient
                .delete()
                .uri("/workspaces/{workspaceSlug}", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNoContent()
                .expectBody(Void.class);

        Workspace purged = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(purged.getStatus()).isEqualTo(Workspace.WorkspaceStatus.PURGED);
    }

    @Test
    @WithMentorUser
    void shouldDenyPurgeWhenUserIsNotOwner() {
        User owner = persistUser("non-owner-test-owner");
        Workspace workspace = workspaceService.createWorkspace(new CreateWorkspaceRequestDTO(
                "non-owner-ws",
                "Non-Owner Test",
                "non-owner-group",
                AccountType.ORG,
                owner.getId(),
                IntegrationKind.GITLAB,
                "glpat-non-owner-token",
                null));

        User mentorUser = persistUser("mentor");
        ensureMembership(workspace, mentorUser, Membership.WorkspaceRole.MEMBER);

        webTestClient
                .delete()
                .uri("/workspaces/{workspaceSlug}", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);

        Workspace unchanged = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE);
    }

    @Nested
    class SlackCleanup {

        private Workspace createBareWorkspace(String slug) {
            User owner = persistUser(slug + "-owner");
            return workspaceService.createWorkspace(new CreateWorkspaceRequestDTO(
                    slug,
                    "Slack Test " + slug,
                    slug + "-group",
                    AccountType.ORG,
                    owner.getId(),
                    IntegrationKind.GITLAB,
                    "glpat-slack-test-token",
                    null));
        }

        private static String slackTs(Instant instant) {
            return String.format("%010d.000000", instant.getEpochSecond());
        }

        private SlackThread insertThread(Long workspaceId, String slackChannelId, String slackThreadTs, String lastTs) {
            SlackThread thread = new SlackThread();
            thread.setWorkspaceId(workspaceId);
            thread.setSlackChannelId(slackChannelId);
            thread.setSlackThreadTs(slackThreadTs);
            thread.setFirstTs(lastTs);
            thread.setLastTs(lastTs);
            thread.setMessageCount(1);
            return slackThreadRepository.save(thread);
        }

        /**
         * Insert one ingested Slack message with a controlled {@code ingested_at} (native —
         * bypasses @CreationTimestamp).
         */
        private void insertMessage(Long workspaceId, String slackTs, String slackThreadTs, Instant ingestedAt) {
            jdbcTemplate.update(
                    "INSERT INTO slack_message (workspace_id, slack_team_id, slack_channel_id,"
                            + " slack_ts, slack_thread_ts, ingested_at) VALUES (?, ?, ?, ?, ?, ?)",
                    workspaceId,
                    "T1",
                    "C1",
                    slackTs,
                    slackThreadTs,
                    Timestamp.from(ingestedAt));
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

            // mentor_slack_thread.chat_thread_id is a NOT NULL FK → chat_thread(id); seed a real
            // thread first.
            User owner = persistUser(channelId + "-mentor-owner");
            ChatThread chatThread = new ChatThread();
            chatThread.setId(UUID.randomUUID());
            chatThread.setTitle("Slack DM " + channelId);
            chatThread.setWorkspace(workspace);
            chatThread.setAccountId(accountId(owner));
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

            slackParticipantConsentRepository.optOutOfIngestion(workspaceId, "U1", "SLACK_APP_HOME");
        }

        @Test
        @DisplayName("retention sweep processes each workspace independently: B's recent message"
                + " survives A's expired prune")
        void retentionSweepIsWorkspaceIsolated() {
            // Age-based pruning itself (which ts survives, message-grain vs. thread-grain) is
            // proven by
            // SlackRetentionErasureIntegrationTest; this is the cross-workspace delta it doesn't
            // cover — that the
            // sweep loop enumerates workspaces independently, so A's expired data cannot affect B's
            // recent data.
            // Neither workspace has a Slack Connection, so both resolve to DEFAULT_RETENTION_DAYS
            // (30d).
            Instant now = Instant.now();
            Workspace a = createBareWorkspace("slack-retain-a");
            Workspace b = createBareWorkspace("slack-retain-b");
            String aExpired = slackTs(now.minus(Duration.ofDays(40)));
            String bRecent = slackTs(now);
            insertThread(a.getId(), "C1", aExpired, aExpired);
            insertThread(b.getId(), "C1", bRecent, bRecent);
            insertMessage(a.getId(), aExpired, aExpired, now.minus(Duration.ofDays(40)));
            insertMessage(b.getId(), bRecent, bRecent, now);

            slackRetentionSweeper.sweepNow();

            assertThat(slackMessageRepository.countByWorkspaceId(a.getId())).isZero();
            assertThat(slackMessageRepository.countByWorkspaceId(b.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("purge empties every Slack table for the workspace while other workspaces stay" + " intact")
        void purgeEmptiesAllSlackTables() {
            // Seed all four Slack tables for both A and B.
            Instant now = Instant.now();
            Workspace a = createBareWorkspace("slack-purge-a");
            Workspace b = createBareWorkspace("slack-purge-b");
            insertMessage(a.getId(), "300.1", "300.1", now);
            insertMessage(b.getId(), "400.1", "400.1", now);
            seedAggregates(a, "CA", "300.1");
            seedAggregates(b, "CB", "400.1");

            workspaceLifecycleService.purgeWorkspace(a.getWorkspaceSlug());

            // A's Slack rows are empty; B's rows are untouched.
            assertThat(slackMessageRepository.countByWorkspaceId(a.getId())).isZero();
            assertThat(slackThreadRepository.countByWorkspaceId(a.getId())).isZero();
            assertThat(slackMonitoredChannelRepository.countByWorkspaceId(a.getId()))
                    .isZero();
            assertThat(mentorSlackThreadRepository.countByWorkspaceId(a.getId()))
                    .isZero();
            assertThat(slackParticipantConsentRepository.countByWorkspaceIdAndIngestionOptedOutTrue(a.getId()))
                    .isZero();

            assertThat(slackMessageRepository.countByWorkspaceId(b.getId())).isEqualTo(1);
            assertThat(slackThreadRepository.countByWorkspaceId(b.getId())).isEqualTo(1);
            assertThat(slackMonitoredChannelRepository.countByWorkspaceId(b.getId()))
                    .isEqualTo(1);
            assertThat(mentorSlackThreadRepository.countByWorkspaceId(b.getId()))
                    .isEqualTo(1);
            assertThat(slackParticipantConsentRepository.countByWorkspaceIdAndIngestionOptedOutTrue(b.getId()))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("SlackWorkspacePurgeAdapter erases the workspace's derived CONVERSATION rows;"
                + " another workspace's survive")
        void purgeAdapterErasesDerivedConversationRows() {
            Workspace a = createBareWorkspace("slack-conv-a");
            Workspace b = createBareWorkspace("slack-conv-b");

            SlackThread threadA = insertThread(a.getId(), "CA", "500.1", "500.1");
            DerivedConversation derivedA = seedDerivedConversation(a, threadA.getId());

            SlackThread threadB = insertThread(b.getId(), "CB", "600.1", "600.1");
            DerivedConversation derivedB = seedDerivedConversation(b, threadB.getId());

            // Run this contributor alone to prove it erases derived rows without the practices
            // contributor.
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> slackWorkspacePurgeAdapter.deleteWorkspaceData(a.getId()));

            assertDerivedConversationErased(derivedA);
            assertDerivedConversationExists(derivedB);
            tx.executeWithoutResult(status -> slackWorkspacePurgeAdapter.deleteWorkspaceData(a.getId()));
            assertDerivedConversationExists(derivedB);
        }

        @Test
        void shouldEraseDeliveredAndPreparedJudgmentsWhenWorkspaceIsPurged() {
            Workspace erasedWorkspace = createBareWorkspace("delivered-purge-a");
            Workspace retainedWorkspace = createBareWorkspace("delivered-purge-b");
            SlackThread erasedThread = insertThread(erasedWorkspace.getId(), "CA", "700.1", "700.1");
            SlackThread retainedThread = insertThread(retainedWorkspace.getId(), "CB", "800.1", "800.1");
            DerivedConversation erased = seedDerivedConversation(erasedWorkspace, erasedThread.getId());
            DerivedConversation retained = seedDerivedConversation(retainedWorkspace, retainedThread.getId());
            assertDerivedConversationExists(erased);

            workspaceLifecycleService.purgeWorkspace(erasedWorkspace.getWorkspaceSlug());

            assertDerivedConversationErased(erased);
            assertDerivedConversationExists(retained);
            assertThat(chatMessageRepository.findById(erased.messageId())).isEmpty();
            assertThat(chatMessageRepository.findById(retained.messageId())).isPresent();
        }

        private record DerivedConversation(
                List<UUID> practiceResultIds, UUID preparedId, UUID deliveredId, UUID messageId) {}

        private void assertDerivedConversationExists(DerivedConversation derived) {
            assertThat(practiceResultRepository.findAllById(derived.practiceResultIds()))
                    .hasSize(2);
            assertThat(feedbackRepository.findById(derived.preparedId()))
                    .get()
                    .extracting(Feedback::getDeliveryState)
                    .isEqualTo(FeedbackState.READY);
            assertThat(feedbackRepository.findById(derived.deliveredId()))
                    .get()
                    .extracting(Feedback::getDeliveryState)
                    .isEqualTo(FeedbackState.DELIVERED);
            assertThat(boundFeedbackIds(derived))
                    .containsExactlyInAnyOrder(derived.preparedId(), derived.deliveredId());
            assertThat(feedbackPlacementRepository.findByFeedbackId(derived.deliveredId()))
                    .singleElement()
                    .extracting(FeedbackCopy::getChatMessageId)
                    .isEqualTo(derived.messageId());
        }

        private void assertDerivedConversationErased(DerivedConversation derived) {
            assertThat(practiceResultRepository.findAllById(derived.practiceResultIds()))
                    .isEmpty();
            assertThat(feedbackRepository.findById(derived.preparedId())).isEmpty();
            assertThat(feedbackRepository.findById(derived.deliveredId())).isEmpty();
            assertThat(boundFeedbackIds(derived)).isEmpty();
            assertThat(feedbackPlacementRepository.findByFeedbackId(derived.deliveredId()))
                    .isEmpty();
        }

        private List<@Nullable UUID> boundFeedbackIds(DerivedConversation derived) {
            return jdbcTemplate.queryForList(
                    "SELECT feedback_id FROM feedback_evidence WHERE feedback_id IN (?, ?)",
                    UUID.class,
                    derived.preparedId(),
                    derived.deliveredId());
        }

        /** Seed both a never-delivered and a delivered judgment for one conversation. */
        private DerivedConversation seedDerivedConversation(Workspace workspace, long threadId) {
            User owner = persistUser("conv-" + workspace.getId() + "-subject");
            ChatThread chatThread = new ChatThread();
            chatThread.setId(UUID.randomUUID());
            chatThread.setWorkspace(workspace);
            chatThread.setAccountId(accountId(owner));
            chatThreadRepository.save(chatThread);
            ChatMessage message = new ChatMessage();
            message.setId(UUID.randomUUID());
            message.setThread(chatThread);
            message.setRole(ChatMessage.Role.ASSISTANT);
            message.setStatus(ChatMessage.Status.completed);
            message.setParts(OM.valueToTree(List.of(Map.of("type", "text", "text", "Delivered guidance"))));
            message.setMetadata(OM.createObjectNode());
            chatMessageRepository.save(message);

            var work = modelFixtures.work(workspace, "conv-practice-" + workspace.getId(), "chat.thread", threadId);
            var first = modelFixtures.result(work, accountId(owner), "Result title");
            var second = modelFixtures.result(work, accountId(owner), "Result title");
            List<UUID> practiceResultIds = List.of(first.getId(), second.getId());
            var prepared = modelFixtures.feedback(
                    first, FeedbackDestination.CONVERSATION, FeedbackState.READY, "Prepared guidance");
            var delivered = modelFixtures.feedback(
                    second, FeedbackDestination.CONVERSATION, FeedbackState.DELIVERED, "Delivered guidance");
            UUID copyId = UUID.randomUUID();
            feedbackPlacementRepository.save(FeedbackCopy.builder()
                    .id(copyId)
                    .writeKey(copyId.toString())
                    .writeMarker("hephaestus:" + copyId)
                    .workspaceId(workspace.getId())
                    .feedbackId(delivered.getId())
                    .kind(FeedbackCopyKind.CHAT_TURN)
                    .state(FeedbackCopyState.POSTED)
                    .chatMessageId(message.getId())
                    .postedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build());
            return new DerivedConversation(practiceResultIds, prepared.getId(), delivered.getId(), message.getId());
        }
    }
}
