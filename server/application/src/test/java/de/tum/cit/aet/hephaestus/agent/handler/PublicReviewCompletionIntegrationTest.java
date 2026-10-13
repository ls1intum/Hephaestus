package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.adapter.EvidenceFolderPersonDataCatalog;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.context.PreparedJobInputsFixtures;
import de.tum.cit.aet.hephaestus.agent.context.ReviewedWork;
import de.tum.cit.aet.hephaestus.agent.context.providers.GeneralReviewCommentContentSource;
import de.tum.cit.aet.hephaestus.agent.context.providers.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewHistoryContentSource;
import de.tum.cit.aet.hephaestus.agent.handler.ObservationAdmissionService.AdmissionIdentity;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedReview;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.job.ReviewCompositionBasis;
import de.tum.cit.aet.hephaestus.agent.job.ReviewCompositionBasisRepository;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCopyRecorder;
import de.tum.cit.aet.hephaestus.core.settings.InstanceSettingsService;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig.GitHubAppConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryChannel.SummaryHandle;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.AuthorAssociation;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.github.issuecomment.GitHubIssueCommentProcessor;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatch;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalDecision;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.dto.DecideFeedbackProposalRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.AdmittedReviewJobFixtures;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The whole review on the work accepted while its review still runs, against PostgreSQL: the completion row and the
 * public outcome it commits with, what a repeat or a lost owner may change, and what the job's terminal output may
 * still do afterwards. Provider writes go to the shared poster doubles; nothing leaves the test.
 */
class PublicReviewCompletionIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE_SHA = "a".repeat(40);
    private static final String HEAD_SHA = "b".repeat(40);
    private static final String WORKER = "test-worker";
    private static final String ADMISSION_DIGEST = "c".repeat(64);
    private static final String SUMMARY =
            "The description says what changed. The new method reads its input before it checks it for null.";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private InstanceSettingsService instanceSettings;

    @Autowired
    private PublicReviewCompletionService completionService;

    @Autowired
    private ObservationAdmissionService observationAdmission;

    @Autowired
    private PublicReviewCompletionRepository completions;

    @Autowired
    private JobTypeHandlerRegistry handlerRegistry;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository practiceRevisionRepository;

    @Autowired
    private JobEvidenceFiles evidenceFiles;

    @Autowired
    private FabricLayout evidenceLayout;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private LlmConnectionRepository llmConnectionRepository;

    @Autowired
    private LlmModelRepository llmModelRepository;

    @Autowired
    private WorkspaceAgentBindingRepository workspaceAgentBindingRepository;

    @Autowired
    private LlmModelResolver llmModelResolver;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private ConnectionRepository connections;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackDispatchRepository dispatchRepository;

    @Autowired
    private FeedbackDeliveryService feedbackDeliveryService;

    @Autowired
    private ReviewCompositionBasisRepository bases;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PullRequestCommentPoster commentPoster;

    @Autowired
    private DiffNotePoster diffNotePoster;

    @Autowired
    private AccountPreferencesQuery accountPreferencesQuery;

    @Autowired
    private GeneralReviewCommentContentSource generalComments;

    @Autowired
    private ReviewHistoryContentSource reviewHistory;

    @Autowired
    private IssueCommentRepository issueComments;

    @Autowired
    private GitHubIssueCommentProcessor commentProcessor;

    @Autowired
    private PersonDataCopyRecorder personCopies;

    private final Map<String, byte[]> capturedFiles = new LinkedHashMap<>();
    private final List<PreparedJobInputs> preparedEvidence = new ArrayList<>();

    private Workspace workspace;
    private Long prId;
    private Practice description;
    private Practice errors;
    private AgentJob job;

    @BeforeEach
    void setUp() {
        reset(commentPoster, diffNotePoster, accountPreferencesQuery);
        instanceSettings.updateSilentMode(
                false,
                null,
                null,
                EntityTagPrecondition.parse("\"" + instanceSettings.get().getVersion() + "\""));
        AgentHandlerTestDoubles.resolveSummaryWrites(commentPoster);
        when(commentPoster.findExisting(any())).thenReturn(ExistingDeliveryLookup.absent());
        when(commentPoster.post(any())).thenReturn(new SummaryHandle("early-comment"));
        when(accountPreferencesQuery.practiceFeedbackDeliveryEnabled(anyLong())).thenReturn(true);

        workspace = WorkspaceTestFixtures.activeWorkspace("public-completion");
        workspace.getFeatures().setPracticesEnabled(true);
        workspace = workspaceRepository.save(workspace);
        var scm = new Connection(
                workspace,
                IntegrationKind.GITHUB,
                "1733",
                new GitHubAppConfig(1733L, workspace.getAccountLogin(), null, Set.of()));
        ReflectionTestUtils.setField(scm, "state", IntegrationState.ACTIVE);
        connections.save(scm);

        description = createPractice("pr-description-quality", "PR Description Quality");
        errors = createPractice("error-handling", "Error Handling");

        IdentityProvider provider = gitProviderRepository
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseGet(() -> gitProviderRepository.save(
                        new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com")));
        User developer = userRepository.save(TestUserFactory.createUser(510L, "completion-author", provider));
        workspaceMembershipService.createMembership(
                workspace, developer.getId(), WorkspaceMembership.WorkspaceRole.MEMBER);

        Repository repo = new Repository();
        repo.setNativeId(4011L);
        repo.setProvider(provider);
        repo.setName("completion-repo");
        repo.setNameWithOwner("org/completion-repo");
        repo.setHtmlUrl("https://github.com/org/completion-repo");
        repo.setDefaultBranch("main");
        repo = repositoryRepository.save(repo);
        repositoryToMonitorRepository.save(WorkspaceTestFixtures.repositoryMonitor(workspace, repo.getNameWithOwner()));

        Instant now = Instant.now();
        Long providerId = Objects.requireNonNull(provider.getId());
        pullRequestRepository.upsertCore(
                8011L,
                providerId,
                51,
                "Pipeline Test PR",
                "Test body",
                "OPEN",
                null,
                "https://github.com/org/completion-repo/pull/51",
                false,
                null,
                0,
                now,
                now,
                now,
                developer.getId(),
                repo.getId(),
                null,
                null,
                false,
                false,
                1,
                10,
                5,
                3,
                null,
                null,
                null,
                "feature/completion",
                "main",
                HEAD_SHA,
                BASE_SHA,
                null,
                null);
        prId = pullRequestRepository
                .findByRepositoryIdAndNumber(repo.getId(), 51)
                .orElseThrow()
                .getId();

        AgentJob created = new AgentJob();
        created.setWorkspace(workspace);
        created.setWorkerId(WORKER);
        created.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        created.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        created.setArtifactKind(ArtifactKinds.PULL_REQUEST);
        created.setStatus(AgentJobStatus.RUNNING);
        created.setConfigSnapshot(AdmittedReviewJobFixtures.snapshot(
                workspace,
                llmConnectionRepository,
                llmModelRepository,
                workspaceAgentBindingRepository,
                llmModelResolver,
                MAPPER));
        ObjectNode metadata = MAPPER.createObjectNode();
        metadata.put("pull_request_id", prId);
        metadata.put("repository_id", repo.getId());
        metadata.put("repository_full_name", "org/completion-repo");
        metadata.put("pr_number", 51);
        metadata.put("pr_url", "https://github.com/org/completion-repo/pull/51");
        metadata.put("commit_sha", HEAD_SHA);
        metadata.put("source_branch", "feature/completion");
        metadata.put("target_branch", "main");
        metadata.put("title", "Pipeline Test PR");
        metadata.put("body", "Test body");
        created.setMetadata(metadata);
        created.setEvidenceSnapshot(evidenceSnapshot(description, errors));
        job = agentJobRepository.save(created);
        preparedEvidence.add(PreparedJobInputsFixtures.prepare(
                evidenceFiles, job, PreparedJobInputsFixtures.filesOnly(capturedFiles)));
    }

    @AfterEach
    void tearDown() throws Exception {
        reset(commentPoster, diffNotePoster, accountPreferencesQuery);
        preparedEvidence.forEach(PreparedJobInputs::close);
        preparedEvidence.clear();
        FileUtils.deleteDirectory(evidenceLayout
                .jobsRoot()
                .resolve(workspace.getId().toString())
                .resolve(job.getId().toString())
                .toFile());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-before", "missing-after", "unknown-after", "complete-after"})
    void shouldWithholdUnvouchedHistoricalWordsAfterTheirDiscussionAuthorDisappears(String custody) {
        String quotedWords = "split persistence out so each unit is testable";
        var pr = pullRequestRepository.findByIdWithAuthorAndRepository(prId).orElseThrow();
        var repo = Objects.requireNonNull(pr.getRepository());
        var provider = repo.getProvider();
        Long authorId = Objects.requireNonNull(pr.getAuthor()).getId();
        User reviewer = userRepository.save(TestUserFactory.createUser(511L, "earlier-reviewer", provider));
        IssueComment comment = new IssueComment();
        comment.setNativeId(9011L);
        comment.setProvider(provider);
        comment.setIssue(pr);
        comment.setAuthor(reviewer);
        comment.setBody(quotedWords);
        comment.setHtmlUrl(pr.getHtmlUrl() + "#issuecomment-9011");
        comment.setAuthorAssociation(AuthorAssociation.NONE);
        comment.setCreatedAt(Instant.now());
        comment.setUpdatedAt(Instant.now());
        issueComments.saveAndFlush(comment);

        AgentJob current = job;
        AgentJob earlier = new AgentJob();
        earlier.setWorkspace(workspace);
        earlier.setWorkerId(WORKER);
        earlier.setPurpose(job.getPurpose());
        earlier.setJobType(job.getJobType());
        earlier.setArtifactKind(job.getArtifactKind());
        earlier.setStatus(AgentJobStatus.RUNNING);
        earlier.setConfigSnapshot(job.getConfigSnapshot());
        earlier.setMetadata(Objects.requireNonNull(job.getMetadata()).deepCopy());
        earlier.setEvidenceSnapshot(job.getEvidenceSnapshot());
        job = agentJobRepository.saveAndFlush(earlier);
        evidenceFiles.beginPersonCapture(job);
        var previousFiles = new LinkedHashMap<>(capturedFiles);
        previousFiles.putAll(generalComments
                .capture(new ContextRequest.PracticeReviewRequest(job), generalComments.sourceKinds())
                .files());
        assertThat(new String(previousFiles.get("context/general_comments.json"), StandardCharsets.UTF_8))
                .contains(quotedWords);
        // These are the actual participants of the captured discussion, recorded before freezing its bytes.
        personCopies.recordUser(reviewer.getId());
        personCopies.recordUser(authorId);
        preparedEvidence.add(evidenceFiles.prepare(job, new PreparedEvidence(previousFiles, null), null));
        String earlierDigest = admitLive();
        String priorBody =
                "The description makes the new boundary clear. The earlier reviewer asked to '" + quotedWords + "'.";
        assertThat(completionService
                        .complete(identity(), earlierDigest, summaryOn(priorBody, all()))
                        .accepted())
                .isTrue();
        UUID earlierJob = job.getId();
        assertThat(reviewHistory
                        .publicSameWorkFeedback(workspace.getId(), authorId, ArtifactKinds.PULL_REQUEST, prId)
                        .toString())
                .contains(priorBody);

        commentProcessor.delete(
                comment.getNativeId(), ProcessingContext.forWebhook(workspace.getId(), repo, "deleted"));
        assertThat(issueComments.findByNativeIdAndProviderId(9011L, Objects.requireNonNull(provider.getId())))
                .isEmpty();
        // A's retained guidance still rests on the valid core source after B's discussion is deleted.
        assertThat(reviewHistory
                        .publicSameWorkFeedback(workspace.getId(), authorId, ArtifactKinds.PULL_REQUEST, prId)
                        .toString())
                .contains(priorBody);
        if (custody.startsWith("missing")) jdbc.update("DELETE FROM person_evidence_copy WHERE job_id=?", earlierJob);
        if (custody.startsWith("unknown"))
            jdbc.update(
                    "UPDATE person_evidence_copy SET payload=jsonb_set(payload,'{dependencies}','\"UNKNOWN\"'::jsonb) WHERE job_id=?",
                    earlierJob);
        job = current;
        String digest = admitLive();
        UUID suppression = UUID.randomUUID();
        try {
            if (custody.endsWith("before")) suppressReviewer(suppression, reviewer);
            ObjectNode read = observationAdmission.publicFeedbackHistory(identity());
            assertThat(read.path("history").path("feedback")).isNotEmpty();
            if (custody.startsWith("complete")) assertThat(read.toString()).contains(quotedWords);
            else {
                assertThat(read.toString()).doesNotContain(quotedWords).contains("COPY_AUTHORITY_UNAVAILABLE");
                assertThat(read.path("history").path("wordsCoverage").asString())
                        .isEqualTo("PARTIAL");
            }
            if (custody.endsWith("after")) suppressReviewer(suppression, reviewer);
            var completed = completionService.complete(identity(), digest, summaryOn(SUMMARY, all()));
            assertThat(completed.accepted()).isEqualTo(!custody.startsWith("complete"));
            if (custody.startsWith("complete")) {
                assertThat(completions.findByWorkspaceIdAndJobId(workspace.getId(), job.getId()))
                        .isEmpty();
            }
        } finally {
            jdbc.update("DELETE FROM person_suppression WHERE id=?", suppression);
        }
    }

    private void suppressReviewer(UUID request, User reviewer) {
        jdbc.update(
                "INSERT INTO person_suppression(id,provider_id,subject,team_key) SELECT ?,provider_id,native_id::text,'' FROM \"user\" WHERE id=?",
                request,
                reviewer.getId());
    }

    @Test
    void shouldRefusePublicHistoryExplicitlyWhenTheCurrentCopyIsRevoked() {
        admitLive();
        jdbc.update("UPDATE person_evidence_copy SET state='ERASE_REQUESTED' WHERE job_id=?", job.getId());
        assertThatThrownBy(() -> observationAdmission.publicFeedbackHistory(identity()))
                .isInstanceOf(ObservationAdmissionService.PublicHistoryRefusedException.class)
                .hasMessage("Review history copy authority is unavailable")
                .hasCauseInstanceOf(EvidenceFolderPersonDataCatalog.CopyUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldAcceptTheLiveAdmittingWriterWithoutRetainedRecoveryInputs(boolean unknownLineage) {
        if (unknownLineage) leaveCaptureLineageUnknown();
        String digest = admitLive();
        assertThat(bases.findOriginalCopy(workspace.getId(), job.getId())).isEmpty();

        var outcome = completionService.complete(identity(), digest, summaryOn(SUMMARY, all()));

        assertThat(outcome.accepted()).isTrue();
        assertThat(completion().getAdmissionAttempt()).isZero();
        assertThat(automaticPackage().getState()).isEqualTo(FeedbackDispatchState.SENT);
        assertThat(reload().getStatus()).isEqualTo(AgentJobStatus.RUNNING);
        verify(commentPoster, times(1)).post(any());
        if (unknownLineage)
            assertThat(evidenceFiles.completeReceipt(identity())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "absent",
                "revoked",
                "foreign-store",
                "wrong-attempt",
                "wrong-workspace",
                "ambiguous",
                "malformed-identities"
            })
    void shouldRefuseTheLiveWriterWithoutItsExactReadyCapture(String invalidation) {
        leaveCaptureLineageUnknown();
        String digest = admitLive();
        switch (invalidation) {
            case "absent" -> jdbc.update("DELETE FROM person_evidence_copy WHERE job_id=?", job.getId());
            case "revoked" ->
                jdbc.update("UPDATE person_evidence_copy SET state='ERASE_REQUESTED' WHERE job_id=?", job.getId());
            case "foreign-store" ->
                jdbc.update(
                        "UPDATE person_evidence_copy SET store_id=? WHERE job_id=?", UUID.randomUUID(), job.getId());
            case "wrong-attempt" ->
                jdbc.update(
                        "UPDATE person_evidence_copy SET payload=jsonb_set(payload,'{attempt}','1'::jsonb) WHERE job_id=?",
                        job.getId());
            case "wrong-workspace" ->
                jdbc.update(
                        "UPDATE person_evidence_copy SET workspace_id=? WHERE job_id=?",
                        workspace.getId() + 1,
                        job.getId());
            case "ambiguous" ->
                jdbc.update(
                        "INSERT INTO person_evidence_copy(id,workspace_id,job_id,store_id,state,payload) SELECT ?,workspace_id,job_id,store_id,state,payload FROM person_evidence_copy WHERE job_id=?",
                        UUID.randomUUID(),
                        job.getId());
            case "malformed-identities" ->
                jdbc.update(
                        "UPDATE person_evidence_copy SET payload=jsonb_set(payload,'{identities}','[\"invalid\"]'::jsonb) WHERE job_id=?",
                        job.getId());
            default -> throw new IllegalArgumentException("Unknown capture invalidation");
        }

        var outcome = completionService.complete(identity(), digest, summaryOn(SUMMARY, all()));

        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.reasons()).containsExactly(PublicReviewCompletionService.COPY_GONE);
        assertThat(PublicReviewCompletionService.wasRefused(reload())).isTrue();
        assertThat(completions.existsByWorkspaceIdAndJobId(workspace.getId(), job.getId()))
                .isFalse();
        assertThat(jobFeedback()).isEmpty();
        verify(commentPoster, never()).post(any());
    }

    @Test
    void shouldRefuseTheLiveWriterWhenACopiedPersonIsSuppressedAfterAdmission() {
        leaveCaptureLineageUnknown();
        String digest = admitLive();
        Observation about = observation(Outcome.MET);
        evidenceFiles.indexLateRead(
                identity(), () -> Set.of(about.getId()), ids -> new JobEvidenceFiles.LateCopies(ids, Set.of()));
        UUID suppression = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO person_suppression(id,provider_id,subject,team_key) SELECT ?,provider_id,native_id::text,'' FROM \"user\" WHERE id=?",
                suppression,
                about.getAboutUserId());
        try {
            var outcome = completionService.complete(identity(), digest, summaryOn(SUMMARY, all()));
            assertThat(outcome.accepted()).isFalse();
            assertThat(outcome.reasons()).containsExactly(PublicReviewCompletionService.COPY_GONE);
            assertThat(completions.existsByWorkspaceIdAndJobId(workspace.getId(), job.getId()))
                    .isFalse();
            assertThat(jobFeedback()).isEmpty();
            verify(commentPoster, never()).post(any());
        } finally {
            jdbc.update("DELETE FROM person_suppression WHERE id=?", suppression);
        }
    }

    @Test
    void shouldRefuseTheLiveWriterWhileItsJobIsSelectedByAnUnfinishedErasure() {
        leaveCaptureLineageUnknown();
        String digest = admitLive();
        UUID request = UUID.randomUUID();
        ObjectNode selections = MAPPER.createObjectNode();
        selections
                .putObject("agent_job")
                .putArray("rows")
                .addObject()
                .putObject("columns")
                .put("id", job.getId().toString());
        jdbc.update(
                "INSERT INTO person_data_request(id,state,selections_json,counts_json,completed_json,created_at,expires_at,version) VALUES (?,'FAILED',?,'{}','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 day',0)",
                request,
                MAPPER.writeValueAsString(selections));
        try {
            var outcome = completionService.complete(identity(), digest, summaryOn(SUMMARY, all()));
            assertThat(outcome.accepted()).isFalse();
            assertThat(outcome.reasons()).containsExactly(PublicReviewCompletionService.COPY_GONE);
            assertThat(completions.existsByWorkspaceIdAndJobId(workspace.getId(), job.getId()))
                    .isFalse();
            assertThat(jobFeedback()).isEmpty();
            verify(commentPoster, never()).post(any());
        } finally {
            jdbc.update("DELETE FROM person_data_request WHERE id=?", request);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown-lineage", "expired", "absent"})
    void shouldRefuseAContinuedWriterWithoutVouchingUnexpiredOriginalInputs(String invalidation) {
        admit(BOTH_OBSERVATIONS);
        switch (invalidation) {
            case "unknown-lineage" -> leaveCaptureLineageUnknown();
            case "expired" ->
                jdbc.update(
                        "UPDATE review_composition_basis SET expires_at=? WHERE job_id=?",
                        Timestamp.from(Instant.EPOCH),
                        job.getId());
            case "absent" -> jdbc.update("DELETE FROM review_composition_basis WHERE job_id=?", job.getId());
            default -> throw new IllegalArgumentException("Unknown recovery invalidation");
        }
        jdbc.update("UPDATE agent_job SET retry_count=1 WHERE id=?", job.getId());

        var outcome = completionService.complete(
                new AdmissionIdentity(job.getId(), workspace.getId(), 1, WORKER),
                ADMISSION_DIGEST,
                summaryOn(SUMMARY, all()));

        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.reasons()).containsExactly(PublicReviewCompletionService.COPY_GONE);
        assertThat(completions.existsByWorkspaceIdAndJobId(workspace.getId(), job.getId()))
                .isFalse();
        assertThat(jobFeedback()).isEmpty();
        verify(commentPoster, never()).post(any());
    }

    @Test
    void shouldCommitTheAutomaticPackageWithItsCompletionAndPostItOnceWhileTheJobRuns() {
        admit(BOTH_OBSERVATIONS);

        var outcome = completionService.complete(identity(), ADMISSION_DIGEST, summaryOn(SUMMARY, all()));

        assertThat(outcome.accepted()).isTrue();
        PublicReviewCompletion completion = completion();
        assertThat(completion.getReviewDigest()).isEqualTo(outcome.reviewDigest());
        assertThat(completion.getAdmissionDigest()).isEqualTo(ADMISSION_DIGEST);
        assertThat(completion.getAdmissionAttempt()).isZero();
        assertThat(completion.getAcceptedAttempt()).isZero();
        assertThat(completion.getAcceptedWorkerId()).isEqualTo(WORKER);
        assertThat(completion.getContractVersion()).isEqualTo(ComposedReview.CONTRACT_VERSION);
        assertThat(Objects.requireNonNull(completion.getReview())
                        .path("summary")
                        .path("body")
                        .asString())
                .isEqualTo(SUMMARY);
        FeedbackDispatch dispatch = automaticPackage();
        assertThat(dispatch.getState()).isEqualTo(FeedbackDispatchState.SENT);
        assertThat(dispatch.getDeliveredExternalRef()).isEqualTo("early-comment");
        verify(commentPoster, times(1)).post(any());
        assertThat(rows(FeedbackDeliveryState.DELIVERED)).hasSize(1);
        // The run, its accounting and its private lanes are untouched: they settle at the terminal boundary.
        AgentJob running = reload();
        assertThat(running.getStatus()).isEqualTo(AgentJobStatus.RUNNING);
        assertThat(running.getDeliveryStatus()).isNull();
        assertNoPrivatePreparation();
    }

    @Test
    void shouldAcknowledgeAnIdenticalRepeatAndRefuseAChangedReviewWithoutASecondPackage() {
        admit(BOTH_OBSERVATIONS);
        JsonNode review = summaryOn(SUMMARY, all());

        var first = completionService.complete(identity(), ADMISSION_DIGEST, review);
        var repeat = completionService.complete(identity(), ADMISSION_DIGEST, review.deepCopy());
        var changed = completionService.complete(
                identity(), ADMISSION_DIGEST, summaryOn("A different summary of the same problem.", all()));

        assertThat(first.accepted()).isTrue();
        assertThat(repeat.accepted()).isTrue();
        assertThat(repeat.reviewDigest()).isEqualTo(first.reviewDigest());
        assertThat(changed.accepted()).isFalse();
        assertThat(changed.reasons()).containsExactly(PublicReviewCompletionService.CHANGED);
        assertThat(completion().getReviewDigest()).isEqualTo(first.reviewDigest());
        assertThat(Objects.requireNonNull(completion().getReview())
                        .path("summary")
                        .path("body")
                        .asString())
                .isEqualTo(SUMMARY);
        // A refused change is not a refusal of the accepted review: nothing is recorded against the job.
        assertThat(PublicReviewCompletionService.wasRefused(reload())).isFalse();
        assertThat(dispatchRepository.findReviewPackages(workspace.getId(), job.getId()))
                .hasSize(1);
        verify(commentPoster, times(1)).post(any());
    }

    @Test
    void shouldRecordAWhollyQuietReviewWithoutAnyProviderWrite() {
        admit(ONE_MET_OBSERVATION);

        var outcome = completionService.complete(identity(), ADMISSION_DIGEST, MAPPER.createObjectNode());

        assertThat(outcome.accepted()).isTrue();
        assertThat(Objects.requireNonNull(completion().getReview())
                        .path("summary")
                        .isNull())
                .isTrue();
        assertThat(dispatchRepository.findReviewPackages(workspace.getId(), job.getId()))
                .isEmpty();
        assertThat(jobFeedback()).isEmpty();
        verify(commentPoster, never()).post(any());
        assertNoPrivatePreparation();
    }

    @Test
    void shouldRecordAnExplicitlyWithheldProblemAsItsOwnSuppressedUnitWithTheCompletion() {
        admit(BOTH_OBSERVATIONS);
        Observation problem = observation(Outcome.NOT_MET);
        ObjectNode review = MAPPER.createObjectNode();
        ObjectNode decision = review.putArray("withheld").addObject();
        decision.putArray("basedOn").add(problem.getId().toString());
        decision.put("reason", "BELOW_BAR");

        var outcome = completionService.complete(identity(), ADMISSION_DIGEST, review);

        assertThat(outcome.accepted()).isTrue();
        assertThat(dispatchRepository.findReviewPackages(workspace.getId(), job.getId()))
                .isEmpty();
        assertThat(rows(FeedbackDeliveryState.SUPPRESSED))
                .singleElement()
                .satisfies(row -> assertThat(row.getPosition()).isEqualTo(2000));
        assertThat(jdbc.queryForList("""
                        SELECT fo.observation_id FROM feedback_observation fo
                        JOIN feedback f ON f.id = fo.feedback_id
                        WHERE f.agent_job_id = ? AND f.delivery_state = 'SUPPRESSED'
                        """, UUID.class, job.getId())).containsExactly(problem.getId());
        verify(commentPoster, never()).post(any());
    }

    @Test
    void shouldLeaveAReviewThatRestsOnAnApprovalPracticeAwaitingApprovalWithoutPostingIt() {
        errors.setAutonomy(PracticeAutonomy.HUMAN_APPROVAL);
        errors = practiceRepository.saveAndFlush(errors);
        admit(BOTH_OBSERVATIONS);

        var outcome = completionService.complete(identity(), ADMISSION_DIGEST, summaryOn(SUMMARY, all()));

        assertThat(outcome.accepted()).isTrue();
        assertThat(completions.existsByWorkspaceIdAndJobId(workspace.getId(), job.getId()))
                .isTrue();
        assertThat(rows(FeedbackDeliveryState.AWAITING_APPROVAL)).hasSize(1);
        assertThat(dispatchRepository.findReviewPackages(workspace.getId(), job.getId()))
                .isEmpty();
        verify(commentPoster, never()).post(any());
        assertNoPrivatePreparation();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @WithAdminUser
    void shouldRespectCancellationAtAnEarlyApprovedPackageReservation(boolean cancelled) {
        ensureAdminMembership(workspace);
        errors.setAutonomy(PracticeAutonomy.HUMAN_APPROVAL);
        errors = practiceRepository.saveAndFlush(errors);
        admit(BOTH_OBSERVATIONS);
        var outcome = completionService.complete(identity(), ADMISSION_DIGEST, summaryOn(SUMMARY, all()));
        assertThat(outcome.accepted()).isTrue();
        Feedback proposal = rows(FeedbackDeliveryState.AWAITING_APPROVAL).getFirst();
        if (cancelled) jdbc.update("UPDATE agent_job SET status='CANCELLED' WHERE id=?", job.getId());
        webTestClient
                .put()
                .uri(
                        "/workspaces/{slug}/practices/reviews/feedback/{feedbackId}/approval",
                        workspace.getWorkspaceSlug(),
                        proposal.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .bodyValue(new DecideFeedbackProposalRequestDTO(FeedbackApprovalDecision.APPROVED, null, null))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            FeedbackDispatch dispatch = dispatchRepository
                    .findByDestinationKeyAndWorkspaceId("approved:" + proposal.getId(), workspace.getId())
                    .orElseThrow();
            assertThat(dispatch.getState())
                    .isEqualTo(cancelled ? FeedbackDispatchState.SUPPRESSED : FeedbackDispatchState.SENT);
            if (cancelled) {
                assertThat(dispatch.getSuppressionReason())
                        .isEqualTo(FeedbackSuppressionReason.REVIEW_CANCELLED.name());
                assertThat(dispatch.getWriteStarted()).isFalse();
            }
        });
        assertThat(completion().getReviewDigest()).isEqualTo(outcome.reviewDigest());
        verify(commentPoster, times(cancelled ? 0 : 1)).post(any());
    }

    @Test
    void shouldKeepARefusedReviewRefusedSoNeitherALaterSubmissionNorTheTerminalOutputCarriesIt() {
        admit(BOTH_OBSERVATIONS);

        var wrongAdmission = completionService.complete(identity(), "d".repeat(64), summaryOn(SUMMARY, all()));
        var afterwards = completionService.complete(identity(), ADMISSION_DIGEST, summaryOn(SUMMARY, all()));

        assertThat(wrongAdmission.accepted()).isFalse();
        assertThat(wrongAdmission.reasons()).containsExactly(PublicReviewCompletionService.NOT_ADMITTED);
        assertThat(PublicReviewCompletionService.wasRefused(reload())).isTrue();
        assertThat(afterwards.accepted()).isFalse();
        assertThat(completions.existsByWorkspaceIdAndJobId(workspace.getId(), job.getId()))
                .isFalse();
        AgentJob ended = endWithOutput(summaryOn(SUMMARY, all()));
        assertThatThrownBy(() -> handler().deliver(ended)).isInstanceOf(JobDeliveryException.class);
        assertThat(dispatchRepository.findReviewPackages(workspace.getId(), job.getId()))
                .isEmpty();
        verify(commentPoster, never()).post(any());
    }

    @Test
    void shouldRefuseAReviewThatLeavesAPublicProblemUndecidedAndRecordTheRefusal() {
        admit(BOTH_OBSERVATIONS);

        var outcome =
                completionService.complete(identity(), ADMISSION_DIGEST, summaryOn(SUMMARY, observation(Outcome.MET)));

        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.reasons()).containsExactly(PublicReviewCompletionService.UNDECIDED);
        assertThat(PublicReviewCompletionService.wasRefused(reload())).isTrue();
        assertThat(jobFeedback()).isEmpty();
        assertThat(completions.existsByWorkspaceIdAndJobId(workspace.getId(), job.getId()))
                .isFalse();
    }

    @Test
    void shouldWriteNothingForAnAttemptThatNoLongerOwnsTheJobOrNamesAnotherWorkspace() {
        admit(BOTH_OBSERVATIONS);
        JsonNode review = summaryOn(SUMMARY, all());

        assertThatThrownBy(() -> completionService.complete(
                        new AdmissionIdentity(job.getId(), workspace.getId(), 0, "lost-worker"),
                        ADMISSION_DIGEST,
                        review))
                .isInstanceOf(ObservationAdmissionService.StaleAttemptException.class);
        assertThatThrownBy(() -> completionService.complete(
                        new AdmissionIdentity(job.getId(), workspace.getId(), 1, WORKER), ADMISSION_DIGEST, review))
                .isInstanceOf(ObservationAdmissionService.StaleAttemptException.class);
        assertThatThrownBy(() -> completionService.complete(
                        new AdmissionIdentity(job.getId(), workspace.getId() + 1, 0, WORKER), ADMISSION_DIGEST, review))
                .isInstanceOf(ObservationAdmissionService.StaleAttemptException.class);

        assertThat(completions.existsByWorkspaceIdAndJobId(workspace.getId(), job.getId()))
                .isFalse();
        assertThat(PublicReviewCompletionService.wasRefused(reload())).isFalse();
        assertThat(jobFeedback()).isEmpty();
        verify(commentPoster, never()).post(any());
    }

    @Test
    void shouldAcceptNothingForAReviewCancelledBeforeItsSubmissionArrived() {
        admit(BOTH_OBSERVATIONS);
        jdbc.update("UPDATE agent_job SET status = 'CANCELLED' WHERE id = ?", job.getId());

        assertThatThrownBy(() -> completionService.complete(identity(), ADMISSION_DIGEST, summaryOn(SUMMARY, all())))
                .isInstanceOf(ObservationAdmissionService.StaleAttemptException.class);

        assertThat(completions.existsByWorkspaceIdAndJobId(workspace.getId(), job.getId()))
                .isFalse();
        assertThat(jobFeedback()).isEmpty();
        verify(commentPoster, never()).post(any());
    }

    @Test
    void shouldWithholdAnAcceptedPackageWhoseReviewWasCancelledBeforeTheDispatcherReservedItsWrite() {
        admit(BOTH_OBSERVATIONS);
        // The provider cannot answer the first lookup, so the package waits for a retry instead of being written.
        when(commentPoster.findExisting(any()))
                .thenReturn(ExistingDeliveryLookup.unknown())
                .thenReturn(ExistingDeliveryLookup.absent());

        var outcome = completionService.complete(identity(), ADMISSION_DIGEST, summaryOn(SUMMARY, all()));
        assertThat(outcome.accepted()).isTrue();
        assertThat(automaticPackage().getState()).isNotIn(FeedbackDispatchState.SENT, FeedbackDispatchState.SUPPRESSED);

        jdbc.update("UPDATE agent_job SET status = 'CANCELLED' WHERE id = ?", job.getId());
        jdbc.update(
                "UPDATE feedback_dispatch SET next_attempt_at = CURRENT_TIMESTAMP WHERE destination_key = ?",
                "review:" + job.getId());
        AgentJob cancelled = reload();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            try {
                feedbackDeliveryService.recoverAutomaticPackageIfPresent(cancelled);
            } catch (JobDeliveryException stillWaiting) {
                // A package still settling reports itself unreconciled; its state is what counts here.
            }
            assertThat(automaticPackage().getState()).isEqualTo(FeedbackDispatchState.SUPPRESSED);
        });

        assertThat(automaticPackage().getSuppressionReason())
                .isEqualTo(FeedbackSuppressionReason.REVIEW_CANCELLED.name());
        assertThat(automaticPackage().getWriteStarted()).isFalse();
        verify(commentPoster, never()).post(any());
        assertThat(rows(FeedbackDeliveryState.DELIVERED)).isEmpty();
    }

    @Test
    void shouldNeitherDecideAgainNorRepostTheAcceptedReviewFromADifferentTerminalOutput() {
        admit(BOTH_OBSERVATIONS);
        var outcome = completionService.complete(identity(), ADMISSION_DIGEST, summaryOn(SUMMARY, all()));
        assertThat(outcome.accepted()).isTrue();

        AgentJob ended = endWithOutput(summaryOn("A terminal output that says something else.", all()));
        handler().deliver(ended);

        verify(commentPoster, times(1)).post(any());
        assertThat(dispatchRepository.findReviewPackages(workspace.getId(), job.getId()))
                .singleElement()
                .satisfies(dispatch -> assertThat(dispatch.getState()).isEqualTo(FeedbackDispatchState.SENT));
        assertThat(rows(FeedbackDeliveryState.DELIVERED)).hasSize(1);
        assertThat(completion().getReviewDigest()).isEqualTo(outcome.reviewDigest());
        assertThat(Objects.requireNonNull(completion().getReview())
                        .path("summary")
                        .path("body")
                        .asString())
                .isEqualTo(SUMMARY);
    }

    @Test
    void shouldRemoveTheKeptInputsSoNoWriterContinuesAReviewWhoseQuietReviewWasAccepted() {
        admit(ONE_MET_OBSERVATION);
        keepOriginalInputs();
        assertThat(bases.findOriginalCopy(workspace.getId(), job.getId())).isPresent();

        var outcome = completionService.complete(identity(), ADMISSION_DIGEST, MAPPER.createObjectNode());

        assertThat(outcome.accepted()).isTrue();
        assertThat(bases.findOriginalCopy(workspace.getId(), job.getId())).isEmpty();
    }

    @Test
    void shouldKeepTheInputsOfAReviewWhoseSubmissionWasRefused() {
        admit(BOTH_OBSERVATIONS);
        keepOriginalInputs();

        var outcome =
                completionService.complete(identity(), ADMISSION_DIGEST, summaryOn(SUMMARY, observation(Outcome.MET)));

        assertThat(outcome.accepted()).isFalse();
        // The refusal, not the deletion, keeps a second writer away: the continuation reads it off the job.
        assertThat(bases.findOriginalCopy(workspace.getId(), job.getId())).isPresent();
        assertThat(PublicReviewCompletionService.wasRefused(reload())).isTrue();
    }

    @Test
    void shouldRollBackTheWholeOutcomeWhenCompletionInsertionFailsAndAllowIdenticalRetry() {
        admit(BOTH_OBSERVATIONS);
        jdbc.execute(
                "ALTER TABLE public_review_completion ADD CONSTRAINT completion_test_fail CHECK (accepted_worker_id <> 'test-worker')");
        try {
            assertThatThrownBy(
                            () -> completionService.complete(identity(), ADMISSION_DIGEST, summaryOn(SUMMARY, all())))
                    .isInstanceOf(RuntimeException.class)
                    .hasStackTraceContaining("completion_test_fail");
            assertThat(completions.existsByWorkspaceIdAndJobId(workspace.getId(), job.getId()))
                    .isFalse();
            assertThat(dispatchRepository.findReviewPackages(workspace.getId(), job.getId()))
                    .isEmpty();
            assertThat(jobFeedback()).isEmpty();
            assertThat(bases.findOriginalCopy(workspace.getId(), job.getId())).isPresent();
            assertThat(PublicReviewCompletionService.wasRefused(reload())).isFalse();
            verify(commentPoster, never()).post(any());
        } finally {
            jdbc.execute("ALTER TABLE public_review_completion DROP CONSTRAINT completion_test_fail");
        }
        var accepted = completionService.complete(identity(), ADMISSION_DIGEST, summaryOn(SUMMARY, all()));
        assertThat(accepted.accepted()).isTrue();
        assertThat(automaticPackage().getState()).isEqualTo(FeedbackDispatchState.SENT);
        verify(commentPoster, times(1)).post(any());
    }

    @Test
    void shouldExposeOnlyTheAcceptanceTimeThroughScopedDetailAndBulkListWhileTheQuietJobRuns() {
        admit(ONE_MET_OBSERVATION);
        var outcome = completionService.complete(identity(), ADMISSION_DIGEST, MAPPER.createObjectNode());
        assertThat(outcome.accepted()).isTrue();
        var moments = agentJobRepository
                .findReviewRunMoments(job.getId(), workspace.getId())
                .orElseThrow();
        assertThat(moments.getPublicReviewAcceptedAt()).isEqualTo(completion().getAcceptedAt());
        assertThat(moments.getFirstFeedbackPostedAt()).isNull();
        var page = agentJobRepository.findListRows(workspace.getId(), AgentJobStatus.RUNNING, PageRequest.of(0, 10));
        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.getPublicReviewAcceptedAt()).isEqualTo(completion().getAcceptedAt());
            assertThat(row.getStatus()).isEqualTo(AgentJobStatus.RUNNING);
            assertThat(row.getDeliveryStatus()).isNull();
        });
        assertThat(agentJobRepository.findReviewRunMoments(job.getId(), workspace.getId() + 1))
                .isEmpty();
        assertThat(agentJobRepository.findListRows(workspace.getId() + 1, null, PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    void shouldRefuseWhenTheOriginalCopyWasRevokedBeforeAcceptance() {
        admit(BOTH_OBSERVATIONS);
        jdbc.update("UPDATE person_evidence_copy SET state='ERASE_REQUESTED' WHERE job_id=?", job.getId());
        var outcome = completionService.complete(identity(), ADMISSION_DIGEST, summaryOn(SUMMARY, all()));
        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.reasons()).containsExactly(PublicReviewCompletionService.COPY_GONE);
        assertThat(completions.existsByWorkspaceIdAndJobId(workspace.getId(), job.getId()))
                .isFalse();
        assertThat(jobFeedback()).isEmpty();
        assertThat(dispatchRepository.findReviewPackages(workspace.getId(), job.getId()))
                .isEmpty();
        verify(commentPoster, never()).post(any());
    }

    @Test
    void shouldUseTheOriginalAdmittingAttemptWhenAContinuedWriterCompletesTheReview() {
        admit(ONE_MET_OBSERVATION);
        jdbc.update("UPDATE agent_job SET retry_count=1 WHERE id=?", job.getId());
        var outcome = completionService.complete(
                new AdmissionIdentity(job.getId(), workspace.getId(), 1, WORKER),
                ADMISSION_DIGEST,
                MAPPER.createObjectNode());
        assertThat(outcome.accepted()).isTrue();
        assertThat(completion().getAdmissionAttempt()).isZero();
        assertThat(completion().getAcceptedAttempt()).isEqualTo(1);
        verify(commentPoster, never()).post(any());
    }

    @Test
    void shouldStripTheAcceptedProseWhenTheEndedJobPassesItsPayloadRetentionAndKeepItsDigest() {
        admit(ONE_MET_OBSERVATION);
        var outcome = completionService.complete(identity(), ADMISSION_DIGEST, MAPPER.createObjectNode());
        jdbc.update(
                "UPDATE agent_job SET status = 'COMPLETED', completed_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(30))),
                job.getId());

        Integer stripped = new TransactionTemplate(transactionManager)
                .execute(status -> completions.stripEndedReviews(Instant.now().minus(Duration.ofDays(14)), 100));

        assertThat(stripped).isGreaterThanOrEqualTo(1);
        PublicReviewCompletion kept = completion();
        assertThat(kept.getReview()).isNull();
        assertThat(kept.getReviewDigest()).isEqualTo(outcome.reviewDigest());
    }

    // --- fixtures ----------------------------------------------------------------------------------------------

    private static final String BOTH_OBSERVATIONS = """
            {"observations": [
              {"practiceSlug": "pr-description-quality", "summary": "Good PR description", "outcome": "MET",
               "severity": null, "evidenceRationale": "The description names what changed."},
              {"practiceSlug": "error-handling", "summary": "Missing null check", "outcome": "NOT_MET",
               "severity": "MAJOR", "evidenceRationale": "The method does not check for null input."}
            ]}""";

    private static final String ONE_MET_OBSERVATION = """
            {"observations": [
              {"practiceSlug": "pr-description-quality", "summary": "Good PR description", "outcome": "MET",
               "severity": null, "evidenceRationale": "The description names what changed."}
            ]}""";

    private void leaveCaptureLineageUnknown() {
        preparedEvidence.forEach(PreparedJobInputs::close);
        preparedEvidence.clear();
        try {
            FileUtils.deleteDirectory(evidenceLayout
                    .jobsRoot()
                    .resolve(workspace.getId().toString())
                    .resolve(job.getId().toString())
                    .toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        jdbc.update("DELETE FROM person_evidence_copy WHERE job_id=?", job.getId());
        evidenceFiles.beginPersonCapture(job);
        // A copied source job without a retained receipt makes capture lineage UNKNOWN before runtime admission.
        personCopies.recordCopiedJob(UUID.randomUUID());
        preparedEvidence.add(evidenceFiles.prepare(job, new PreparedEvidence(capturedFiles, null), null));
        assertThat(evidenceFiles.completeReceipt(identity())).isEmpty();
    }

    private String admitLive() {
        JsonNode response = observationAdmission.admit(
                identity(), MAPPER.readTree(withEvidence(BOTH_OBSERVATIONS)).path("observations"));
        job = reload();
        return response.path("admissionDigest").asString();
    }

    private AdmissionIdentity identity() {
        return new AdmissionIdentity(job.getId(), workspace.getId(), 0, WORKER);
    }

    private PullRequestReviewHandler handler() {
        return (PullRequestReviewHandler) handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW);
    }

    /**
     * Admits the observations through the handler's own admission body, with the digest and admitting attempt the
     * admission records; the job keeps running and has no output.
     */
    private void admit(String rawObservations) {
        JsonNode observations = MAPPER.readTree(withEvidence(rawObservations)).path("observations");
        handler().prepareObservations(job, observations).record(job);
        ObjectNode metadata =
                (ObjectNode) Objects.requireNonNull(reload().getMetadata()).deepCopy();
        metadata.put(ObservationAdmissionService.DIGEST_METADATA_KEY, ADMISSION_DIGEST);
        metadata.put(ObservationAdmissionService.ATTEMPT_METADATA_KEY, 0);
        AgentJob admitted = reload();
        admitted.setMetadata(metadata);
        job = agentJobRepository.saveAndFlush(admitted);
        keepOriginalInputs();
    }

    /** The job ended with a terminal output that composed {@code review} against its admitted observations. */
    private AgentJob endWithOutput(JsonNode review) {
        AgentJob ended = reload();
        ObjectNode output = MAPPER.createObjectNode();
        ObjectNode feedback = output.putObject("feedback");
        feedback.put("admissionDigest", ADMISSION_DIGEST);
        feedback.put("contractVersion", ComposedReview.CONTRACT_VERSION);
        feedback.putArray("units");
        feedback.putArray("compositionFailures");
        var staged = feedback.putArray("observations");
        for (Observation observation : observationRepository.findByAgentJobId(job.getId(), workspace.getId())) {
            staged.addObject()
                    .put("id", observation.getId().toString())
                    .put("practiceSlug", observation.getPractice().getSlug())
                    .put("outcome", observation.getOutcome().name())
                    .put("anchorable", false)
                    .putArray("citations");
        }
        feedback.set("review", review);
        ended.setOutput(output);
        ended.setStatus(AgentJobStatus.COMPLETED);
        ended.setCompletedAt(Instant.now());
        return agentJobRepository.saveAndFlush(ended);
    }

    private JsonNode summaryOn(String body, Observation... support) {
        ObjectNode review = MAPPER.createObjectNode();
        ObjectNode summary = review.putObject("summary").put("body", body);
        var basedOn = summary.putArray("basedOn");
        for (Observation observation : support) basedOn.add(observation.getId().toString());
        return review;
    }

    private Observation[] all() {
        return observationRepository.findByAgentJobId(job.getId(), workspace.getId()).stream()
                .filter(observation -> observation.getOutcome().isDecided())
                .toArray(Observation[]::new);
    }

    private Observation observation(Outcome outcome) {
        return observationRepository.findByAgentJobId(job.getId(), workspace.getId()).stream()
                .filter(observation -> observation.getOutcome() == outcome)
                .findFirst()
                .orElseThrow();
    }

    private AgentJob reload() {
        return agentJobRepository.findById(job.getId()).orElseThrow();
    }

    private PublicReviewCompletion completion() {
        return completions
                .findByWorkspaceIdAndJobId(workspace.getId(), job.getId())
                .orElseThrow();
    }

    private FeedbackDispatch automaticPackage() {
        return dispatchRepository
                .findByDestinationKeyAndWorkspaceId("review:" + job.getId(), workspace.getId())
                .orElseThrow();
    }

    private List<Feedback> jobFeedback() {
        return feedbackRepository.findAll().stream()
                .filter(row -> job.getId().equals(row.getAgentJobId()))
                .toList();
    }

    private List<Feedback> rows(FeedbackDeliveryState state) {
        return jobFeedback().stream()
                .filter(row -> row.getDeliveryState() == state)
                .toList();
    }

    /**
     * Neither private lane ran: their listeners record that they ran even with nothing to prepare, so a lane woken by
     * the early public projection would leave its mark within the listener's asynchronous hand-off.
     */
    private void assertNoPrivatePreparation() {
        await().during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(5))
                .until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT in_app_prepared_at IS NULL AND in_chat_prepared_at IS NULL FROM agent_job WHERE id = ?",
                        Boolean.class,
                        job.getId())));
    }

    /** The original public writer inputs #2709 keeps for a continuing writer, as its admission saves them. */
    private void keepOriginalInputs() {
        var basis = new ReviewCompositionBasis();
        basis.setJobId(job.getId());
        basis.setWorkspaceId(workspace.getId());
        basis.setAdmissionAttempt(0);
        basis.setAdmissionDigest(ADMISSION_DIGEST);
        basis.setBasisVersion(ReviewCompositionBasis.VERSION);
        basis.setBasisDigest(ReviewCompositionBasis.digest(Map.of()));
        basis.setCopyId(evidenceFiles.completeReceipt(identity()).orElseThrow());
        basis.setByteSize(2);
        basis.setCreatedAt(Instant.now());
        basis.setExpiresAt(Instant.now().plus(ReviewCompositionBasis.RETENTION));
        basis.setPayload(MAPPER.createObjectNode().putObject("files"));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> bases.save(basis));
    }

    private Practice createPractice(String slug, String name) {
        Practice practice = new Practice();
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        practice.setWorkspace(workspace);
        practice.setAutonomy(PracticeAutonomy.AUTOMATIC);
        practice.setSlug(slug);
        practice.setName(name);
        practice.setCriteria("Test " + slug);
        PracticeTestEvidence.configure(practice, ScmSignals.PULL_REQUEST_OPENED);
        practice = practiceRepository.saveAndFlush(practice);
        PracticeRevision revision = practiceRevisionRepository.save(new PracticeRevision(practice, 1));
        practice.setCurrentRevision(revision);
        return practiceRepository.saveAndFlush(practice);
    }

    private ObjectNode evidenceSnapshot(Practice... practices) {
        ObjectNode snapshot = EvidenceSnapshotFixtures.snapshot(MAPPER);
        ObjectNode core = EvidenceSnapshotFixtures.availableSource(snapshot, "scm.pull-request.core", null);
        addArtifact(
                snapshot,
                core,
                "context/metadata.json",
                MAPPER.createObjectNode()
                        .put("title", "Pipeline Test PR")
                        .put("body", "Test body")
                        .put("commit_sha", HEAD_SHA)
                        .toString());
        addArtifact(snapshot, core, PullRequestContentSource.DESCRIPTION_FILE, "Test body");
        addArtifact(
                snapshot,
                EvidenceSnapshotFixtures.availableSource(snapshot, "scm.pull-request.diff", BASE_SHA + ":" + HEAD_SHA),
                PullRequestContentSource.CHANGE_FILE,
                "{\"base_sha\":\"" + BASE_SHA + "\",\"head_sha\":\"" + HEAD_SHA + "\"}");
        snapshot.set(
                ReviewedWork.SNAPSHOT_KEY,
                MAPPER.valueToTree(ReviewedWork.captured(
                                MAPPER.writeValueAsBytes(snapshot.path("manifest")), capturedFiles, prId, MAPPER)
                        .orElseThrow()));
        for (Practice practice : practices) {
            EvidenceSnapshotFixtures.admittedPractice(
                    snapshot,
                    practice.getSlug(),
                    Objects.requireNonNull(practice.getCurrentRevision().getId()));
        }
        return snapshot;
    }

    private void addArtifact(ObjectNode snapshot, ObjectNode source, String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        capturedFiles.put(path, bytes);
        EvidenceSnapshotFixtures.artifact(snapshot, source, path, ProvenanceDigest.sha256Hex(bytes))
                .put("mediaType", path.endsWith(".json") ? "application/json" : "text/markdown")
                .put("bytes", bytes.length);
    }

    /** Each observation cites the captured description, which admission verifies against the prepared folder. */
    private static String withEvidence(String rawObservations) {
        var root = MAPPER.readTree(rawObservations);
        for (var observation : root.path("observations")) {
            var citation = ((ObjectNode) observation)
                    .putObject("evidence")
                    .putArray("citations")
                    .addObject();
            citation.put("sourceKind", "scm.pull-request.core");
            citation.put("artifactPath", "context/metadata.json");
            citation.put("path", "body");
            citation.put("startLine", 1);
            citation.put("endLine", 1);
            citation.put("quote", "Test body");
        }
        return MAPPER.writeValueAsString(root);
    }
}
