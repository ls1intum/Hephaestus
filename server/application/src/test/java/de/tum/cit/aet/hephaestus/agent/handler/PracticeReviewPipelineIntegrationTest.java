package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidencePlan;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.context.PreparedJobInputsFixtures;
import de.tum.cit.aet.hephaestus.agent.context.ReviewedWork;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.context.providers.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedProblem;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedResult;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedReview;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationalDeliveryListener;
import de.tum.cit.aet.hephaestus.agent.handler.inapp.InAppCompositionListener;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ResultsRefusedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobPracticeScriptRun;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobPracticeScriptRunRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.job.PracticeScriptRunRecorderFixtures;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.core.settings.InstanceSettings;
import de.tum.cit.aet.hephaestus.core.settings.InstanceSettingsService;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState.Available;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig.GitHubAppConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig.OutlineConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor.DiffAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFeedbackChannel.DeliveredSignal;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFeedbackChannel.Disposition;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFeedbackChannel.Placement;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryChannel.SummaryHandle;
import de.tum.cit.aet.hephaestus.integration.outline.documentation.OutlineDocumentProjector;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.integration.slack.SlackConversationTestSupport;
import de.tum.cit.aet.hephaestus.practices.EvidenceStance;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceRequirement;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeProgramKind;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeProgramPath;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptRunStatus;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptStopReason;
import de.tum.cit.aet.hephaestus.testconfig.AdmittedReviewJobFixtures;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipService;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@TestPropertySource(
        properties = {
            "hephaestus.integration.slack.enabled=true",
            "hephaestus.integration.slack.signing-secret=test-signing-secret-not-real",
            "hephaestus.integration.outline.enabled=true",
            "hephaestus.integration.outline.allowed-origins=https://wiki.example.com"
        })
class PracticeReviewPipelineIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** The pinned range of the reviewed change; nothing here quotes it, so no checkout is staged. */
    private static final String BASE_SHA = "a".repeat(40);

    private static final String HEAD_SHA = "b".repeat(40);

    /** A valid review that says nothing on the work: the composer's choice, not a missing review. */
    private static final String SILENT_REVIEW = "{}";

    /** The summary a review writes about its problems; a MET result is never acknowledged on the work. */
    private static final String PROBLEMS_SUMMARY = "The new method reads its input before it checks it for null.";

    @Autowired
    private JobTypeHandlerRegistry handlerRegistry;

    @Autowired
    private PracticeResultRepository practiceResultRepository;

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

    private final List<UUID> preparedJobIds = new ArrayList<>();
    private final Map<String, byte[]> capturedFiles = new LinkedHashMap<>();
    private final List<PreparedJobInputs> preparedEvidence = new ArrayList<>();

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
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private IdentityProviderRepository gitProviderRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private WorkspaceMembershipService workspaceMembershipService;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackDeliveryService feedbackDeliveryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InstanceSettingsService instanceSettingsService;

    @Autowired
    private PullRequestCommentPoster commentPoster;

    @Autowired
    private DiffNotePoster diffNotePoster;

    @Autowired
    private PracticeFeedbackCommentFormatter commentFormatter;

    @Autowired
    private AccountPreferencesQuery accountPreferencesQuery;

    private JobTypeHandler handler;
    private Workspace workspace;
    private AgentJob agentJob;
    private Long prId;
    private Repository repository;
    private User developer;

    @AfterEach
    void resetHandlerDoubles() {
        reset(commentPoster, diffNotePoster, accountPreferencesQuery);
    }

    @AfterEach
    void releasePreparedEvidence() throws Exception {
        preparedEvidence.forEach(PreparedJobInputs::close);
        preparedEvidence.clear();
        for (var jobId : preparedJobIds) {
            FileUtils.deleteDirectory(evidenceLayout
                    .jobsRoot()
                    .resolve(workspace.getId().toString())
                    .resolve(jobId.toString())
                    .toFile());
        }
        preparedJobIds.clear();
    }

    @BeforeEach
    void setUp() {
        reset(commentPoster, diffNotePoster, accountPreferencesQuery);
        databaseTestUtils.cleanDatabase();
        releaseSilentMode();
        AgentHandlerTestDoubles.resolveSummaryWrites(commentPoster);
        when(commentPoster.findExisting(any())).thenReturn(ExistingDeliveryLookup.absent());

        workspace = WorkspaceTestFixtures.activeWorkspace("pipeline-test");
        workspace.getFeatures().setPracticesEnabled(true);
        workspace = workspaceRepository.save(workspace);

        var scm = new Connection(
                workspace,
                IntegrationKind.GITHUB,
                "1732",
                new GitHubAppConfig(1732L, workspace.getAccountLogin(), null, Set.of()));
        ReflectionTestUtils.setField(scm, "state", IntegrationState.ACTIVE);
        connections.save(scm);

        Practice description = createPractice("pr-description-quality", "PR Description Quality");
        Practice errors = createPractice("error-handling", "Error Handling");

        IdentityProvider provider = gitProviderRepository
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseGet(() -> gitProviderRepository.save(
                        new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com")));

        developer = userRepository.save(TestUserFactory.createUser(500L, "pipeline-author", provider));
        workspaceMembershipService.createMembership(
                workspace, developer.getId(), WorkspaceMembership.WorkspaceRole.MEMBER);

        Repository repo = new Repository();
        repo.setNativeId(4001L);
        repo.setProvider(provider);
        repo.setName("pipeline-repo");
        repo.setNameWithOwner("org/pipeline-repo");
        repo.setHtmlUrl("https://github.com/org/pipeline-repo");
        repo.setDefaultBranch("main");
        repo = repositoryRepository.save(repo);
        repository = repo;
        repositoryToMonitorRepository.save(WorkspaceTestFixtures.repositoryMonitor(workspace, repo.getNameWithOwner()));

        Instant now = Instant.now();
        Long providerId = Objects.requireNonNull(provider.getId());
        pullRequestRepository.upsertCore(
                8001L,
                providerId,
                50,
                "Pipeline Test PR",
                "Test body",
                "OPEN",
                null,
                "https://github.com/org/pipeline-repo/pull/50",
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
                "feature/pipeline",
                "main",
                HEAD_SHA,
                BASE_SHA,
                null,
                null // mergeCommitSha
                );
        prId = pullRequestRepository
                .findByRepositoryIdAndNumber(repo.getId(), 50)
                .orElseThrow()
                .getId();

        agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setWorkerId("test-worker");
        agentJob.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setArtifactKind(ArtifactKinds.PULL_REQUEST);
        agentJob.setStatus(AgentJobStatus.RUNNING);
        agentJob.setConfigSnapshot(AdmittedReviewJobFixtures.snapshot(
                workspace,
                llmConnectionRepository,
                llmModelRepository,
                workspaceAgentBindingRepository,
                llmModelResolver,
                OBJECT_MAPPER));

        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        metadata.put("pull_request_id", prId);
        metadata.put("repository_id", repo.getId());
        metadata.put("repository_full_name", "org/pipeline-repo");
        metadata.put("pr_number", 50);
        metadata.put("pr_url", "https://github.com/org/pipeline-repo/pull/50");
        metadata.put("commit_sha", HEAD_SHA);
        metadata.put("source_branch", "feature/pipeline");
        metadata.put("target_branch", "main");
        metadata.put("title", "Pipeline Test PR");
        metadata.put("body", "Test body");
        agentJob.setMetadata(metadata);
        agentJob.setEvidenceSnapshot(evidenceSnapshot(description, errors));
        agentJob = agentJobRepository.save(agentJob);
        preparedEvidence.add(PreparedJobInputsFixtures.prepare(
                evidenceFiles, agentJob, PreparedJobInputsFixtures.filesOnly(capturedFiles)));
        agentJob.setStatus(AgentJobStatus.COMPLETED);
        agentJob = agentJobRepository.saveAndFlush(agentJob);
        preparedJobIds.add(agentJob.getId());

        handler = handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW);
        stubInlinePackageDelivered();
        when(accountPreferencesQuery.practiceFeedbackDeliveryEnabled(anyLong())).thenReturn(true);
    }

    /** The provider accounts for every line note of a package without returning a handle to record. */
    private void stubInlinePackageDelivered() {
        when(diffNotePoster.deliverPackage(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DiffNotePoster.DiffNoteResult(
                        List.of(), true, false, false, false, false, List.of(), null));
    }

    @Autowired
    private WorkspaceContextBuilder folderBuilder;

    @Autowired
    private ConnectionRepository connections;

    @Autowired
    private OutlineDocumentRepository outlineDocuments;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private ResultAdmissionService admissionService;

    @MockitoSpyBean
    private OutlineDocumentProjector documentProjection;

    @Test
    void jobFolderVerifiesCrossSourceQuotesAndIncludesFreshlyCommittedPullRequest() throws Exception {
        setJobOutput(validAgentOutput());
        var provider = Objects.requireNonNull(repository.getProvider());
        var other = new Repository();
        other.setProvider(provider);
        other.setNativeId(4002L);
        other.setName("other-repo");
        other.setNameWithOwner("org/other-repo");
        other.setHtmlUrl("https://github.com/org/other-repo");
        other.setDefaultBranch("main");
        other = repositoryRepository.save(other);
        repositoryToMonitorRepository.save(
                WorkspaceTestFixtures.repositoryMonitor(workspace, other.getNameWithOwner()));
        long otherId = Objects.requireNonNull(other.getId());
        var committedPullRequest = new AtomicLong();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Instant now = Instant.now();
            pullRequestRepository.upsertCore(
                    8002L,
                    Objects.requireNonNull(provider.getId()),
                    51,
                    "Just committed",
                    "Fresh pull request body",
                    "OPEN",
                    null,
                    "https://github.com/org/other-repo/pull/51",
                    false,
                    null,
                    0,
                    now,
                    now,
                    now,
                    developer.getId(),
                    otherId,
                    null,
                    null,
                    false,
                    false,
                    1,
                    1,
                    0,
                    1,
                    null,
                    null,
                    null,
                    "feature/fresh",
                    "main",
                    HEAD_SHA,
                    BASE_SHA,
                    null,
                    null);
            long freshId = Objects.requireNonNull(pullRequestRepository
                    .findByRepositoryIdAndNumber(otherId, 51)
                    .orElseThrow()
                    .getId());
            committedPullRequest.set(freshId);
            jdbcTemplate.update(
                    "INSERT INTO issue_comment (native_id,provider_id,issue_id,body,html_url,author_association,created_at,updated_at) VALUES (?,?,?,?,?,'MEMBER',now(),now())",
                    1732001L,
                    provider.getId(),
                    prId,
                    "Other repository comment",
                    "https://github.com/" + repository.getNameWithOwner() + "/pull/50#issuecomment-1732001");
        });
        var slack = new SlackConversationTestSupport(jdbcTemplate);
        slack.seedChannel(workspace.getId(), "C1732", "ACTIVE");
        slack.seedMessage(workspace.getId(), "C1732", "1704067200.000001", null, "Slack source quote");
        var outline = new Connection(
                workspace,
                IntegrationKind.OUTLINE,
                "https://wiki.example.com",
                new OutlineConfig("https://wiki.example.com", null, null, Set.of()));
        ReflectionTestUtils.setField(outline, "state", IntegrationState.ACTIVE);
        outline = connections.save(outline);
        var document = new OutlineDocument();
        document.setWorkspaceId(workspace.getId());
        document.setConnectionId(outline.getId());
        document.setDocumentId(UUID.randomUUID().toString());
        document.setCollectionId(UUID.randomUUID().toString());
        document.setCollectionSlug("engineering");
        document.setSlug("outside-the-old-cap");
        document.setTitle("Design");
        document.setBodyMarkdown("Outline paragraph quote");
        document.setLastMaterializedAt(Instant.now());
        outlineDocuments.save(document);
        for (int n = 0; n < 20; n++) {
            var extra = new OutlineDocument();
            extra.setWorkspaceId(workspace.getId());
            extra.setConnectionId(outline.getId());
            extra.setDocumentId(UUID.randomUUID().toString());
            extra.setCollectionId(document.getCollectionId());
            extra.setCollectionSlug("engineering");
            extra.setSlug("page-" + n);
            extra.setBodyMarkdown("Permitted page " + n);
            extra.setLastMaterializedAt(Instant.now());
            outlineDocuments.save(extra);
        }
        var practice = createPractice("folder-context", "Workspace context");
        practice.setSignals(List.of(ScmSignals.PULL_REQUEST_OPENED));
        practice.setEvidenceRequirements(
                List.of("scm.pull-request.core", "outline.documents", "workspace.project-inventory").stream()
                        .map(kind -> new PracticeEvidenceRequirement(new SourceKind(kind), EvidenceStance.REQUIRED))
                        .toList());
        practice.setReviewWhen(Map.of());
        practice.setSubject(ActorRole.AUTHOR);
        practice.setPrecondition(null);
        practice.setCurrentRevision(practiceRevisionRepository.save(new PracticeRevision(practice, 2)));
        practice = practiceRepository.saveAndFlush(practice);
        var next = new AgentJob();
        next.setWorkspace(workspace);
        next.setWorkerId("test-worker");
        next.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        next.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        next.setArtifactKind(ArtifactKinds.PULL_REQUEST);
        next.setStatus(AgentJobStatus.QUEUED);
        next.setConfigSnapshot(agentJob.getConfigSnapshot());
        var nextMetadata =
                (ObjectNode) Objects.requireNonNull(agentJob.getMetadata()).deepCopy();
        nextMetadata.remove(ResultAdmissionService.DIGEST_METADATA_KEY);
        nextMetadata.put("pull_request_id", committedPullRequest.get());
        nextMetadata
                .put("repository_id", otherId)
                .put("repository_full_name", "org/other-repo")
                .put("pr_number", 51)
                .put("pr_url", "https://github.com/org/other-repo/pull/51")
                .put("commit_sha", HEAD_SHA)
                .put("source_branch", "feature/fresh")
                .put("title", "Just committed")
                .put("body", "Fresh pull request body");
        next.setMetadata(nextMetadata);
        next = agentJobRepository.save(next);
        UUID claimId = next.getId();
        next = Objects.requireNonNull(new TransactionTemplate(transactions).execute(status -> {
            var claimed = agentJobRepository
                    .findByIdQueuedForUpdateSkipLocked(claimId, Instant.now())
                    .orElseThrow();
            claimed.setStatus(AgentJobStatus.RUNNING);
            claimed.setWorkerId("test-worker");
            return agentJobRepository.saveAndFlush(claimed);
        }));
        doAnswer(call -> {
                    assertThat(jdbcTemplate.queryForObject("SHOW transaction_isolation", String.class))
                            .isEqualTo("repeatable read");
                    assertThat(jdbcTemplate.queryForObject("SHOW transaction_read_only", String.class))
                            .isEqualTo("on");
                    CompletableFuture.runAsync(() -> slack.seedMessage(
                                    workspace.getId(),
                                    "C1732",
                                    "1704067201.000001",
                                    null,
                                    "Committed during rendering"))
                            .join();
                    return call.callRealMethod();
                })
                .when(documentProjection)
                .documentsForWorkspace(workspace.getId());
        JobFolderIndex index;
        var snapshot = OBJECT_MAPPER.createObjectNode();
        PreparedJobInputs prepared;
        evidenceFiles.beginPersonCapture(next);
        try (var raw = folderBuilder.prepare(
                new ContextRequest.PracticeReviewRequest(next), EvidencePlan.compile(List.of(practice)))) {
            index = Objects.requireNonNull(raw.manifest());
            assertThat(folderBuilder
                            .prepareAutomatedReviewReadiness(
                                    index, List.of(practice), next.getCreatedAt(), raw.files(), null)
                            .readyPractices())
                    .containsExactly(practice);

            for (String workspaceSource : List.of("outline.documents", "workspace.project-inventory")) {
                assertThat(index.sources())
                        .filteredOn(source -> source.kind().value().equals(workspaceSource))
                        .singleElement()
                        .satisfies(source -> assertThat(source.state()).isInstanceOf(Available.class));
            }

            assertThat(index.artifacts().stream()
                            .filter(a -> a.artifact().path().startsWith("context/docs/")))
                    .hasSize(21);

            snapshot.set("manifest", OBJECT_MAPPER.valueToTree(index));
            snapshot.putArray("practices")
                    .addObject()
                    .put("slug", practice.getSlug())
                    .put(
                            "revisionId",
                            Objects.requireNonNull(practice.getCurrentRevision().getId()));
            next.setEvidenceSnapshot(snapshot);
            next = agentJobRepository.save(next);
            prepared = evidenceFiles.prepare(
                    next,
                    new PreparedEvidence(raw.files(), raw.filesOnDisk(), raw.cleanups(), null, raw.directories()),
                    null);
        } finally {
            evidenceFiles.abortPersonCapture(next);
        }
        preparedEvidence.add(prepared);
        preparedJobIds.add(next.getId());
        assertThat(Files.readString(prepared.filesOnDisk().get("context/scm/reviewed/pulls/51/record.json")))
                .contains("Just committed", "synced_at");
        var comment = OBJECT_MAPPER.readTree(Files.readAllLines(
                        prepared.filesOnDisk().get("context/scm/" + repository.getId() + "/pulls/50/comments.jsonl"))
                .getFirst());
        assertThat(comment.has("synced_at")).isTrue();
        assertThat(comment.path("synced_at").isNull()).isTrue();
        assertThat(Files.readString(prepared.filesOnDisk().get("INDEX.md")))
                .contains("context/chat/", "context/docs/", "context/people/");
        assertThat(Files.readString(prepared.filesOnDisk().get("context/chat/C1732/2024-01.jsonl")))
                .doesNotContain("Committed during rendering");
        var candidate = OBJECT_MAPPER
                .createObjectNode()
                .put("outcome", "MET")
                .put("summary", "Cross-source context")
                .put("explanation", "Four independently captured sources support the review.");
        var citations = candidate.putArray("citations");
        candidate.putObject("evidence");
        candidate.set("coverage", hostCoverage());
        Map<String, String> quotes = Map.of(
                "context/scm/" + repository.getId() + "/pulls/50/comments.jsonl",
                "Other repository comment",
                "context/chat/C1732/2024-01.jsonl",
                "Slack source quote",
                "context/docs/engineering/outside-the-old-cap.md",
                "Outline paragraph quote",
                "context/people/" + developer.getId() + "/observations.jsonl",
                "Good PR description");
        for (var quote : quotes.entrySet()) {
            var lines = Files.readAllLines(prepared.filesOnDisk().get(quote.getKey()));
            int line = IntStream.range(0, lines.size())
                            .filter(n -> lines.get(n).contains(quote.getValue()))
                            .findFirst()
                            .orElseThrow()
                    + 1;
            var artifact = index.artifacts().stream()
                    .filter(a -> a.artifact().path().equals(quote.getKey()))
                    .findFirst()
                    .orElseThrow();
            citations
                    .addObject()
                    .put("sourceKind", artifact.kind().value())
                    .put("path", quote.getKey())
                    .put("artifactPath", quote.getKey())
                    .put("startLine", line)
                    .put("endLine", line)
                    .put("quote", quote.getValue());
        }
        assertThat(agentJobRepository.discardRetiredArtifactInventory(
                        next.getId(), workspace.getId(), 0, "test-worker"))
                .isZero();
        var request = OBJECT_MAPPER.createObjectNode().put("schemaVersion", ResultAdmissionService.SCHEMA_VERSION);
        request.putArray("results").add(entry(practice.getSlug(), candidate));
        admissionService.admit(
                new ResultAdmissionService.AdmissionIdentity(next.getId(), workspace.getId(), 0, "test-worker"),
                request);
        assertThat(prepared.filesOnDisk().get("INDEX.md")).doesNotExist();
        var admitted = practiceResultRepository.findByAgentJobId(next.getId(), workspace.getId());
        assertThat(admitted).hasSize(1);
        assertThat(admitted.getFirst().getEvidence().path("citations")).hasSize(4);
        var receipt = agentJobRepository.findById(next.getId()).orElseThrow().getEvidenceSnapshot();
        assertThat(receipt.path("manifest").path("artifacts")).isEmpty();
        for (var source : receipt.path("manifest").path("sources"))
            assertThat(source.path("artifacts")).isEmpty();
        assertThat(receipt.path("manifest").path("contractVersion").asString()).isEqualTo("1.3.0");
        assertThat(receipt.path("manifest").path("refusals")).isEmpty();
        var ended = new AgentJob();
        ended.setWorkspace(workspace);
        ended.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        ended.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        ended.setConfigSnapshot(next.getConfigSnapshot());
        ended.setEvidenceSnapshot(snapshot.deepCopy());
        ended.setWorkerId("ended-worker");
        ended.setStatus(AgentJobStatus.FAILED);
        ended = agentJobRepository.saveAndFlush(ended);
        assertThat(agentJobRepository.discardRetiredArtifactInventory(
                        ended.getId(), workspace.getId(), 1, "ended-worker"))
                .isZero();
        assertThat(agentJobRepository.discardRetiredArtifactInventory(
                        ended.getId(), workspace.getId(), 0, "another-worker"))
                .isZero();
        assertThat(agentJobRepository
                        .findById(ended.getId())
                        .orElseThrow()
                        .getEvidenceSnapshot()
                        .path("manifest")
                        .path("artifacts"))
                .isNotEmpty();
        assertThat(agentJobRepository.discardRetiredArtifactInventory(
                        ended.getId(), workspace.getId(), 0, "ended-worker"))
                .isOne();
        assertThat(agentJobRepository
                        .findById(ended.getId())
                        .orElseThrow()
                        .getEvidenceSnapshot()
                        .path("manifest")
                        .path("artifacts"))
                .isEmpty();
    }

    @Test
    void shouldWithholdRequiredCoreReadinessWhenAdmittedWordsAreUnknownDespiteKnownMirrorWords() throws Exception {
        var practice = createPractice("retained-core", "Retain the admitted description");
        practice.setEvidenceRequirements(
                List.of(new PracticeEvidenceRequirement(PullRequestContentSource.CORE, EvidenceStance.REQUIRED)));
        practice.setReviewWhen(Map.of());
        practice.setSubject(ActorRole.AUTHOR);
        practice.setPrecondition(null);
        practice = practiceRepository.saveAndFlush(practice);
        var unknown = new AgentJob();
        unknown.setWorkspace(workspace);
        unknown.setWorkerId("test-worker");
        unknown.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        unknown.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        unknown.setArtifactKind(ArtifactKinds.PULL_REQUEST);
        unknown.setStatus(AgentJobStatus.RUNNING);
        unknown.setConfigSnapshot(agentJob.getConfigSnapshot());
        var metadata =
                (ObjectNode) Objects.requireNonNull(agentJob.getMetadata()).deepCopy();
        metadata.remove(List.of("title", "body", ResultAdmissionService.DIGEST_METADATA_KEY));
        unknown.setMetadata(metadata);
        unknown = agentJobRepository.saveAndFlush(unknown);

        evidenceFiles.beginPersonCapture(unknown);
        try (var raw = folderBuilder.prepare(
                new ContextRequest.PracticeReviewRequest(unknown), EvidencePlan.compile(List.of(practice)))) {
            var index = Objects.requireNonNull(raw.manifest());
            assertThat(folderBuilder
                            .prepareAutomatedReviewReadiness(
                                    index, List.of(practice), unknown.getCreatedAt(), raw.files(), null)
                            .readyPractices())
                    .isEmpty();
            var core = index.sources().stream()
                    .filter(source -> source.kind().equals(PullRequestContentSource.CORE))
                    .findFirst()
                    .orElseThrow();
            assertThat(core.state()).isInstanceOfSatisfying(Available.class, available -> {
                assertThat(available.completeness()).isEqualTo(SourceCompleteness.PARTIAL);
                assertThat(available.limitations())
                        .containsExactlyInAnyOrder("RETAINED_TITLE_UNKNOWN", "RETAINED_BODY_UNKNOWN");
            });
            var captured = OBJECT_MAPPER.readTree(raw.files().get("context/metadata.json"));
            assertThat(captured.has("title")).isFalse();
            assertThat(captured.has("body")).isFalse();
            assertThat(raw.files()).doesNotContainKey(PullRequestContentSource.DESCRIPTION_FILE);
        } finally {
            evidenceFiles.abortPersonCapture(unknown);
        }
    }

    private Practice createPractice(String slug, String name) {
        Practice p = new Practice();
        p.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        p.setWorkspace(workspace);
        p.setAutonomy(PracticeAutonomy.AUTOMATIC);
        p.setSlug(slug);
        p.setName(name);
        p.setCriteria("Test " + slug);
        PracticeTestEvidence.configure(p, ScmSignals.PULL_REQUEST_OPENED);
        p = practiceRepository.saveAndFlush(p);
        PracticeRevision revision = practiceRevisionRepository.save(new PracticeRevision(p, 1));
        p.setCurrentRevision(revision);
        return practiceRepository.saveAndFlush(p);
    }

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private InAppCompositionListener inAppLane;

    @Autowired
    private ConversationalDeliveryListener conversationLane;

    @Test
    void shouldPrepareBothPrivateChannelsWhenTheIssueClosedAfterAdmission() {
        Practice practice = createPractice("record-issue-outcome", "Record the issue outcome");
        PracticeTestEvidence.configure(practice, ArtifactKinds.ISSUE);
        practice = practiceRepository.saveAndFlush(practice);
        AgentJob current = agentJob;
        UUID currentResult = UUID.randomUUID();
        UUID currentProblem = UUID.randomUUID();
        for (int number = 71; number <= 73; number++) {
            Issue issue = new Issue();
            issue.setProvider(repository.getProvider());
            issue.setNativeId(9000L + number);
            issue.setNumber(number);
            issue.setTitle("Export " + number);
            issue.setBody("Record the exported format.");
            issue.setRepository(repository);
            issue.setAuthor(developer);
            issue.setState(Issue.State.CLOSED);
            issue.setCreatedAt(Instant.now().minusSeconds(3600));
            issue.setUpdatedAt(Instant.now());
            issue.setHtmlUrl("https://github.com/org/pipeline-repo/issues/" + number);
            issue = issueRepository.saveAndFlush(issue);
            AgentJob run = new AgentJob();
            run.setWorkspace(workspace);
            run.setJobType(AgentJobType.ISSUE_REVIEW);
            run.setArtifactKind(ArtifactKinds.ISSUE);
            run.setPurpose(AgentPurpose.PRACTICE_REVIEW);
            run.setIntegrationKind(IntegrationKind.GITHUB);
            run.setStatus(AgentJobStatus.COMPLETED);
            run.setCompletedAt(Instant.now());
            run.setConfigSnapshot(agentJob.getConfigSnapshot());
            run.setEvidenceSnapshot(EvidenceSnapshotFixtures.snapshot(OBJECT_MAPPER, ArtifactKinds.ISSUE.value()));
            run.setMetadata(OBJECT_MAPPER
                    .createObjectNode()
                    .put("issue_id", issue.getId())
                    .put("issue_number", number)
                    .put("state", "open")
                    .put(ResultAdmissionService.DIGEST_METADATA_KEY, "private-lifecycle-digest")
                    .put("repository_id", repository.getId())
                    .put("repository_full_name", repository.getNameWithOwner()));
            run = agentJobRepository.saveAndFlush(run);
            UUID practiceResultId = UUID.randomUUID();
            practiceResultRepository.insertIfAbsent(
                    practiceResultId,
                    "occ-" + practiceResultId,
                    run.getId(),
                    workspace.getId(),
                    practice.getId(),
                    practice.getCurrentRevision().getId(),
                    ArtifactKinds.ISSUE.value(),
                    issue.getId(),
                    developer.getId(),
                    "The issue has no recorded outcome",
                    "NOT_MET",
                    "MINOR",
                    AdmittedResultFixtures.evidence(run.getId(), "scm.issue.core")
                            .toString(),
                    "The closed issue does not record the exported format.",
                    "test-scope",
                    null,
                    null,
                    null,
                    Instant.now(),
                    "LIVE");
            UUID problemId = UUID.randomUUID();
            observationRepository.insert(
                    problemId,
                    workspace.getId(),
                    practiceResultId,
                    0,
                    null,
                    "MINOR",
                    "The issue has no recorded outcome",
                    "The closed issue does not record the exported format.",
                    AdmittedResultFixtures.evidence(run.getId(), "scm.issue.core")
                            .path("citations")
                            .toString(),
                    null,
                    null,
                    null);
            current = run;
            currentResult = practiceResultId;
            currentProblem = problemId;
        }
        current.setOutput(OBJECT_MAPPER.readTree("""
                {"feedback":{"admissionDigest":"private-lifecycle-digest","results":[{"practiceResultId":"%1$s",
                  "practiceSlug":"%2$s","outcome":"NOT_MET","anchorable":false,
                  "observations":[{"observationId":"%3$s","anchorable":false,"citations":[]}]}],
                "units":[
                  {"channel":"IN_APP","action":"NEW","practiceSlug":"%2$s",
                   "basedOn":[{"practiceResultId":"%1$s","observationId":"%3$s"}],
                   "title":"Record the outcome","body":"Three closed issues omit the exported format.",
                   "nextStep":"Name the exported format when closing the next issue."},
                  {"channel":"IN_CHAT","action":"NEW","practiceSlug":"%2$s",
                   "basedOn":[{"practiceResultId":"%1$s","observationId":"%3$s"}],
                   "title":"Record the outcome","notes":{
                    "situation":"Closed issues omit the exported format.",
                    "capability":"Check that the outcome is named.",
                    "evidenceSummary":"Three closed issues omit the format.",
                    "inConversationSignal":"When planning the next export."}}],
                "contractVersion":2,"review":{"summary":{"body":"Record the exported format.",
                  "basedOn":[{"practiceResultId":"%1$s","observationId":"%3$s"}]},
                "inline":[],"withheld":[]}}}
                """.formatted(currentResult, practice.getSlug(), currentProblem)));
        current = agentJobRepository.saveAndFlush(current);
        UUID jobId = current.getId();
        handlerRegistry.getHandler(AgentJobType.ISSUE_REVIEW).deliver(current);
        await().untilAsserted(() -> {
            AgentJob routed = agentJobRepository.findById(jobId).orElseThrow();
            assertThat(routed.getInAppPreparedAt()).isNotNull();
            assertThat(routed.getInChatPreparedAt()).isNotNull();
            assertThat(feedbackRepository.findAll().stream()
                            .filter(row -> jobId.equals(row.getAgentJobId()))
                            .toList())
                    .extracting(Feedback::getChannel, Feedback::getDeliveryState)
                    .containsExactlyInAnyOrder(
                            tuple(FeedbackChannel.IN_CONTEXT, FeedbackDeliveryState.SUPPRESSED),
                            tuple(FeedbackChannel.IN_APP, FeedbackDeliveryState.PREPARED),
                            tuple(FeedbackChannel.IN_CHAT, FeedbackDeliveryState.PREPARED));
        });
        assertThat(feedbackRepository.findAll().stream()
                        .filter(row ->
                                jobId.equals(row.getAgentJobId()) && row.getChannel() == FeedbackChannel.IN_CONTEXT)
                        .toList())
                .singleElement()
                .satisfies(row ->
                        assertThat(row.getSuppressionReason()).isEqualTo(FeedbackSuppressionReason.ARTIFACT_CLOSED));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM feedback_placement p
                JOIN feedback f ON f.id = p.feedback_id WHERE f.agent_job_id = ?
                """, Integer.class, jobId)).isZero();
        assertThat(conversationLane.prepare(jobId, workspace.getId())).isZero();
        inAppLane.prepare(jobId, workspace.getId());
        assertThat(feedbackRepository.findAll().stream()
                        .filter(row -> jobId.equals(row.getAgentJobId()))
                        .toList())
                .hasSize(3);
    }

    private void setJobOutput(JsonNode results) {
        agentJob = admitAndSetOutput(agentJob, results);
    }

    /** The admission fence's own two steps, minus the ownership transaction it wraps them in. */
    private void admit(AgentJob job, JsonNode practiceResults) {
        var pullRequests = (PullRequestReviewHandler) handler;
        pullRequests.prepareResults(job, practiceResults).record(job);
    }

    /**
     * Admits the results, the admission body's {@code results}, and leaves a composition that has written nothing
     * yet; {@code compose} writes it.
     */
    private AgentJob admitAndSetOutput(AgentJob job, JsonNode results) {
        admit(job, results);
        String digest = "test-admission-digest";
        JsonNode jobMetadata = job.getMetadata();
        assertThat(jobMetadata).isNotNull();
        ObjectNode metadata = (ObjectNode) jobMetadata.deepCopy();
        metadata.put(ResultAdmissionService.DIGEST_METADATA_KEY, digest);
        job.setMetadata(metadata);
        ObjectNode output = OBJECT_MAPPER.createObjectNode();
        output.putObject("feedback").put("admissionDigest", digest).putArray("units");
        job.setOutput(output);
        var rows = practiceResultRepository.findByAgentJobId(job.getId(), workspace.getId());
        String review = rows.stream().anyMatch(o -> o.getOutcome() == Outcome.NOT_MET)
                ? summaryOn(
                        PROBLEMS_SUMMARY,
                        rows.stream()
                                .filter(o -> o.getOutcome() == Outcome.NOT_MET)
                                .map(o -> o.getPractice().getSlug())
                                .toArray(String[]::new))
                : SILENT_REVIEW;
        compose(job, review);
        return agentJobRepository.save(job);
    }

    private void releaseSilentMode() {
        var current = instanceSettingsService.get();
        instanceSettingsService.updateSilentMode(false, null, null, version(current));
    }

    private void engageSilentMode() {
        var current = instanceSettingsService.get();
        instanceSettingsService.updateSilentMode(true, "pipeline safety test", null, version(current));
    }

    private static EntityTagPrecondition version(InstanceSettings settings) {
        return EntityTagPrecondition.parse("\"" + settings.getVersion() + "\"");
    }

    private AgentJob newJobWithOutput(JsonNode results) {
        AgentJob next = new AgentJob();
        next.setWorkspace(workspace);
        next.setWorkerId("test-worker");
        next.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        next.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        next.setArtifactKind(ArtifactKinds.PULL_REQUEST);
        next.setStatus(AgentJobStatus.RUNNING);
        next.setConfigSnapshot(agentJob.getConfigSnapshot());
        JsonNode metadata = agentJob.getMetadata();
        assertThat(metadata).isNotNull();
        next.setMetadata(metadata.deepCopy());
        next.setEvidenceSnapshot(agentJob.getEvidenceSnapshot().deepCopy());
        next = agentJobRepository.save(next);
        preparedEvidence.add(PreparedJobInputsFixtures.prepare(
                evidenceFiles, next, PreparedJobInputsFixtures.filesOnly(capturedFiles)));
        next.setStatus(AgentJobStatus.COMPLETED);
        next = agentJobRepository.saveAndFlush(next);
        preparedJobIds.add(next.getId());
        return admitAndSetOutput(next, results);
    }

    private ObjectNode evidenceSnapshot(Practice... practices) {
        ObjectNode snapshot = EvidenceSnapshotFixtures.snapshot(OBJECT_MAPPER);
        ObjectNode core = EvidenceSnapshotFixtures.availableSource(snapshot, "scm.pull-request.core", null);
        addArtifact(
                snapshot,
                core,
                "context/metadata.json",
                OBJECT_MAPPER
                        .createObjectNode()
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
                OBJECT_MAPPER.valueToTree(ReviewedWork.captured(
                                OBJECT_MAPPER.writeValueAsBytes(snapshot.path("manifest")),
                                capturedFiles,
                                prId,
                                OBJECT_MAPPER)
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

    /** Line 1 of the captured pull request record, which holds the description {@code Test body}. */
    private static ObjectNode describedBody() {
        return OBJECT_MAPPER
                .createObjectNode()
                .put("sourceKind", "scm.pull-request.core")
                .put("artifactPath", "context/metadata.json")
                .put("path", "body")
                .put("startLine", 1)
                .put("endLine", 1)
                .put("quote", "Test body");
    }

    /** Coverage the host counted from its own reads, with nothing left unread. */
    private static ObjectNode hostCoverage() {
        return OBJECT_MAPPER
                .createObjectNode()
                .put("source", "host")
                .put("reads", 1)
                .put("truncatedReads", 0)
                .put("failedReads", 0)
                .put("complete", true);
    }

    /** One entry of the admission body's {@code results}. */
    private static ObjectNode entry(String practiceSlug, JsonNode candidate) {
        ObjectNode entry = OBJECT_MAPPER.createObjectNode().put("practiceSlug", practiceSlug);
        entry.set("candidate", candidate);
        return entry;
    }

    /** A MET candidate resting on the captured description. */
    private static ObjectNode met(String practiceSlug, String summary, String explanation) {
        ObjectNode candidate = OBJECT_MAPPER
                .createObjectNode()
                .put("outcome", "MET")
                .put("summary", summary)
                .put("explanation", explanation);
        candidate.putArray("citations").add(describedBody());
        candidate.putObject("evidence");
        candidate.set("coverage", hostCoverage());
        return entry(practiceSlug, candidate);
    }

    /** A NOT_MET candidate with one problem of {@code severity}, located on the captured description. */
    private static ObjectNode notMet(String practiceSlug, String summary, Severity severity, String explanation) {
        ObjectNode candidate = OBJECT_MAPPER
                .createObjectNode()
                .put("outcome", "NOT_MET")
                .put("summary", summary)
                .put("explanation", explanation);
        candidate.putArray("citations");
        ObjectNode problem = candidate
                .putArray("problems")
                .addObject()
                .put("severity", severity.name())
                .put("summary", summary)
                .put("explanation", explanation);
        problem.putArray("citations").add(describedBody());
        problem.putObject("basis")
                .put("type", "introduced")
                .put("head", 0)
                .put("explanation", "The pull request introduces this.");
        candidate.putObject("evidence");
        candidate.set("coverage", hostCoverage());
        return entry(practiceSlug, candidate);
    }

    private static ArrayNode results(ObjectNode... entries) {
        ArrayNode results = OBJECT_MAPPER.createArrayNode();
        for (ObjectNode entry : entries) {
            results.add(entry);
        }
        return results;
    }

    private static ArrayNode validAgentOutput() {
        return results(
                met("pr-description-quality", "Good PR description", "The description names what changed."),
                notMet(
                        "error-handling",
                        "Missing null check",
                        Severity.MAJOR,
                        "The method does not check for null input."));
    }

    /**
     * The composer's output as the runner writes it: the job's admitted results echoed under their persisted
     * ids with their problems, the review written whole for the work, and the private units. A
     * {@code {practice-slug}} placeholder in the review or a unit names the result of that practice.
     */
    private void compose(AgentJob job, String review, String... units) {
        ObjectNode output = (ObjectNode) Objects.requireNonNull(job.getOutput());
        ObjectNode feedback = (ObjectNode) output.get("feedback");
        feedback.put("contractVersion", ComposedReview.CONTRACT_VERSION);
        var staged = feedback.putArray("results");
        String written = "[" + String.join(",", units) + "]";
        String said = review;
        List<PracticeResult> admitted = practiceResultRepository.findByAgentJobId(job.getId(), workspace.getId());
        Map<UUID, List<Observation>> problems = problemsOf(admitted);
        for (PracticeResult practiceResult : admitted) {
            String slug = practiceResult.getPractice().getSlug();
            String id = practiceResult.getId().toString();
            var children = staged.addObject()
                    .put("practiceResultId", id)
                    .put("practiceSlug", slug)
                    .put("outcome", practiceResult.getOutcome().name())
                    .put("anchorable", false)
                    .putArray("observations");
            for (Observation problem : problems.getOrDefault(practiceResult.getId(), List.of())) {
                children.addObject()
                        .put("observationId", problem.getId().toString())
                        .put("anchorable", false)
                        .putArray("citations");
            }
            written = written.replace("{" + slug + "}", id);
            said = said.replace("{" + slug + "}", id);
        }
        feedback.set("units", OBJECT_MAPPER.readTree(written));
        feedback.set("review", OBJECT_MAPPER.readTree(said));
        agentJobRepository.save(job);
    }

    /** A review whose one text is a summary resting on the results of these practices. */
    private static String summaryOn(String body, String... practiceSlugs) {
        ObjectNode review = OBJECT_MAPPER.createObjectNode();
        ObjectNode summary = review.putObject("summary").put("body", body);
        var basedOn = summary.putArray("basedOn");
        for (String slug : practiceSlugs) {
            basedOn.addObject().put("practiceResultId", "{" + slug + "}");
        }
        return OBJECT_MAPPER.writeValueAsString(review);
    }

    /** The stored problems of these results, by result, in display order. */
    private Map<UUID, List<Observation>> problemsOf(List<PracticeResult> results) {
        Map<UUID, List<Observation>> byResult = new HashMap<>();
        if (results.isEmpty()) return byResult;
        observationRepository
                .findByPracticeResultIds(
                        workspace.getId(),
                        results.stream().map(PracticeResult::getId).toList())
                .forEach(problem -> byResult.computeIfAbsent(problem.getPracticeResultId(), id -> new ArrayList<>())
                        .add(problem));
        return byResult;
    }

    /**
     * The files a real runner wrote ({@code pi-runner-server-contract.spec.ts}): the server admits its request as the
     * runner submits it, records its execution report as the worker does, and delivers a review that rests on the
     * admitted problem.
     */
    @Test
    void shouldAdmitRecordAndDeliverWhatTheRunnerWroteWhenItsOutputReachesTheServer() throws Exception {
        JsonNode staged = runnerOutput("staged.json");
        JsonNode request = runnerOutput("result.json");
        JsonNode report = runnerOutput("practice-scripts.json");
        String criteria = staged.path("criteria").asString();
        JsonNode programs = staged.path("programs");
        List<Practice> practices = List.of(
                runnerPractice("auth-review", criteria, null),
                runnerPractice("ui-scope", criteria, programs.path("ui-scope").asString()),
                runnerPractice("zz-legacy", criteria, programs.path("zz-legacy").asString()));
        AgentJob job = runningJobOver(staged.path("captured"), practices);

        ObjectNode response = admissionService.admit(
                new ResultAdmissionService.AdmissionIdentity(
                        job.getId(), workspace.getId(), job.getRetryCount(), RUNNER_OUTPUT_WORKER),
                request);

        assertThat(response.path("schemaVersion").asInt()).isEqualTo(ResultAdmissionService.SCHEMA_VERSION);
        assertThat(response.path("withheld").isEmpty()).as(response.toString()).isTrue();
        Map<String, PracticeResult> rows =
                practiceResultRepository.findByAgentJobId(job.getId(), workspace.getId()).stream()
                        .collect(Collectors.toMap(row -> row.getPractice().getSlug(), row -> row));
        assertThat(rows).containsOnlyKeys("auth-review", "ui-scope");
        PracticeResult negative = Objects.requireNonNull(rows.get("auth-review"));
        assertThat(negative.getOutcome()).isEqualTo(Outcome.NOT_MET);
        assertThat(negative.getSeverity())
                .as("the worst problem's, as none was declared")
                .isEqualTo(Severity.MAJOR);
        assertThat(negative.getScopeKey()).startsWith(ArtifactKinds.PULL_REQUEST.value() + ":" + prId + ":");
        assertThat(negative.getCoverage())
                .isEqualTo(request.path("results").path(0).path("candidate").path("coverage"));
        JsonNode builtinProgram =
                Objects.requireNonNull(negative.getProvenance()).path("program");
        assertThat(builtinProgram.path("kind").asString()).isEqualTo("builtin");
        assertThat(builtinProgram.path("digest").isNull()).isTrue();
        PracticeResult inapplicable = Objects.requireNonNull(rows.get("ui-scope"));
        assertThat(inapplicable.getOutcome()).isEqualTo(Outcome.NOT_APPLICABLE);
        assertThat(inapplicable.getCoverage())
                .isEqualTo(request.path("results").path(1).path("candidate").path("coverage"));
        String digest = Objects.requireNonNull(inapplicable.getProvenance())
                .path("program")
                .path("digest")
                .asString();
        assertThat(digest)
                .as("the server's digest of the script is the runner's digest of the staged program")
                .isEqualTo(reportEntry(report, "ui-scope")
                        .path("program")
                        .path("digest")
                        .asString());
        List<Observation> problems =
                observationRepository.findByPracticeResultIds(workspace.getId(), List.of(negative.getId()));
        assertThat(problems).hasSize(1);
        Observation problem = problems.getFirst();
        assertThat(problem.getPosition()).isZero();
        assertThat(problem.getSeverity()).isEqualTo(Severity.MAJOR);
        assertThat(Objects.requireNonNull(problem.getBasis()).path("type").asString())
                .isEqualTo("duty");
        assertThat(problem.getNextStep()).isEqualTo("Call checked(token) before the session starts.");
        JsonNode projected = response.path("results")
                .valueStream()
                .filter(result -> result.path("practiceSlug").asString().equals("auth-review"))
                .findFirst()
                .orElseThrow();
        assertThat(projected.path("practiceResultId").asString())
                .isEqualTo(negative.getId().toString());
        assertThat(projected.path("observations").path(0).path("observationId").asString())
                .isEqualTo(problem.getId().toString());

        AgentJob attemptEnd = agentJobRepository.findById(job.getId()).orElseThrow();
        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> PracticeScriptRunRecorderFixtures.record(
                        piResultParser,
                        practiceScriptRuns,
                        meterRegistry,
                        OBJECT_MAPPER,
                        attemptEnd,
                        report.toString().getBytes(StandardCharsets.UTF_8),
                        ConfigSnapshot.fromJson(attemptEnd.getConfigSnapshot(), OBJECT_MAPPER)
                                .practiceScriptModels(),
                        Instant.now()));
        Map<String, AgentJobPracticeScriptRun> runs =
                practiceScriptRuns.findByWorkspaceIdAndJobIdIn(workspace.getId(), List.of(job.getId())).stream()
                        .collect(Collectors.toMap(AgentJobPracticeScriptRun::practiceSlug, run -> run));
        assertThat(runs).containsOnlyKeys("auth-review", "ui-scope", "zz-legacy");
        AgentJobPracticeScriptRun reviewed = Objects.requireNonNull(runs.get("auth-review"));
        assertThat(reviewed.getStatus()).isEqualTo(PracticeScriptRunStatus.OK);
        assertThat(reviewed.getProgramKind()).isEqualTo(PracticeProgramKind.BUILTIN);
        assertThat(reviewed.path()).isEqualTo(PracticeProgramPath.REVIEW);
        AgentJobPracticeScriptRun coded = Objects.requireNonNull(runs.get("ui-scope"));
        assertThat(coded.getStatus()).isEqualTo(PracticeScriptRunStatus.OK);
        assertThat(coded.getProgramKind()).isEqualTo(PracticeProgramKind.CUSTOM);
        assertThat(coded.getProgramDigest()).isEqualTo(digest);
        assertThat(coded.path()).isEqualTo(PracticeProgramPath.CODE);
        AgentJobPracticeScriptRun retired = Objects.requireNonNull(runs.get("zz-legacy"));
        assertThat(retired.getStatus()).isEqualTo(PracticeScriptRunStatus.FAILED);
        assertThat(retired.getStopReason()).isEqualTo(PracticeScriptStopReason.INVALID_PROGRAM);
        assertThat(retired.getError())
                .isEqualTo(reportEntry(report, "zz-legacy").path("error").asString());

        AgentJob ended = agentJobRepository.findById(job.getId()).orElseThrow();
        ended.setStatus(AgentJobStatus.COMPLETED);
        ObjectNode output = OBJECT_MAPPER.createObjectNode();
        output.putObject("feedback")
                .put("admissionDigest", response.path("admissionDigest").asString())
                .putArray("units");
        ended.setOutput(output);
        ended = agentJobRepository.saveAndFlush(ended);
        ObjectNode review = OBJECT_MAPPER.createObjectNode();
        review.putObject("summary")
                .put(
                        "body",
                        "Call checked(token) before the session starts: the criteria ask it of every added sign-in.")
                .putArray("basedOn")
                .addObject()
                .put("practiceResultId", negative.getId().toString())
                .put("observationId", problem.getId().toString());
        compose(ended, review.toString());
        when(commentPoster.post(any())).thenReturn(new SummaryHandle("runner-output-summary"));

        handler.deliver(agentJobRepository.findById(job.getId()).orElseThrow());

        assertThat(jdbcTemplate.queryForList("""
                        SELECT fe.practice_result_id, fe.observation_id, fe.workspace_id FROM feedback_evidence fe
                        JOIN feedback f ON f.id = fe.feedback_id
                        WHERE f.agent_job_id = ? AND f.delivery_state = 'DELIVERED'
                        """, job.getId()))
                .as("the delivered summary rests on the admitted problem, under its result and workspace")
                .extracting(
                        row -> row.get("practice_result_id"),
                        row -> row.get("observation_id"),
                        row -> row.get("workspace_id"))
                .containsExactly(tuple(negative.getId(), problem.getId(), workspace.getId()));
    }

    private static final String RUNNER_OUTPUT_WORKER = "runner-output-worker";

    private static JsonNode runnerOutput(String name) throws Exception {
        try (var in = PracticeReviewPipelineIntegrationTest.class.getResourceAsStream(
                "/practice-results/runner-output/" + name)) {
            return OBJECT_MAPPER.readTree(Objects.requireNonNull(in, name));
        }
    }

    private static JsonNode reportEntry(JsonNode report, String slug) {
        return report.path("practices")
                .valueStream()
                .filter(entry -> entry.path("practiceSlug").asString().equals(slug))
                .findFirst()
                .orElseThrow();
    }

    /** A practice of the runner's run: its criteria, and its program, none for the built-in review. */
    private Practice runnerPractice(String slug, String criteria, @Nullable String script) {
        Practice p = new Practice();
        p.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        p.setWorkspace(workspace);
        p.setAutonomy(PracticeAutonomy.AUTOMATIC);
        p.setSlug(slug);
        p.setName(slug);
        p.setCriteria(criteria);
        p.setPracticeScript(script);
        PracticeTestEvidence.configure(p, ScmSignals.PULL_REQUEST_OPENED);
        p = practiceRepository.saveAndFlush(p);
        p.setCurrentRevision(practiceRevisionRepository.save(new PracticeRevision(p, 1)));
        return practiceRepository.saveAndFlush(p);
    }

    /** A running review of the pull request that captured these files and admitted these practices. */
    private AgentJob runningJobOver(JsonNode capturedRecords, List<Practice> admitted) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        ObjectNode snapshot = EvidenceSnapshotFixtures.snapshot(OBJECT_MAPPER);
        // The record is the core source; the change names the pinned range that the record's commit is the head of.
        JsonNode range = OBJECT_MAPPER.readTree(
                capturedRecords.path(PullRequestContentSource.CHANGE_FILE).asString());
        ObjectNode core = EvidenceSnapshotFixtures.availableSource(snapshot, "scm.pull-request.core", null);
        ObjectNode diff = EvidenceSnapshotFixtures.availableSource(
                snapshot,
                "scm.pull-request.diff",
                range.path("base_sha").asString() + ":" + range.path("head_sha").asString());
        capturedRecords.properties().forEach(record -> {
            byte[] bytes = record.getValue().asString().getBytes(StandardCharsets.UTF_8);
            files.put(record.getKey(), bytes);
            EvidenceSnapshotFixtures.artifact(
                            snapshot,
                            record.getKey().equals(PullRequestContentSource.CHANGE_FILE) ? diff : core,
                            record.getKey(),
                            ProvenanceDigest.sha256Hex(bytes))
                    .put("mediaType", "application/json")
                    .put("bytes", bytes.length);
        });
        snapshot.set(
                ReviewedWork.SNAPSHOT_KEY,
                OBJECT_MAPPER.valueToTree(ReviewedWork.captured(
                                OBJECT_MAPPER.writeValueAsBytes(snapshot.path("manifest")), files, prId, OBJECT_MAPPER)
                        .orElseThrow()));
        for (Practice practice : admitted) {
            EvidenceSnapshotFixtures.admittedPractice(
                    snapshot,
                    practice.getSlug(),
                    Objects.requireNonNull(practice.getCurrentRevision().getId()));
        }
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setWorkerId(RUNNER_OUTPUT_WORKER);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setArtifactKind(ArtifactKinds.PULL_REQUEST);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(agentJob.getConfigSnapshot());
        ObjectNode metadata =
                (ObjectNode) Objects.requireNonNull(agentJob.getMetadata()).deepCopy();
        metadata.remove(ResultAdmissionService.DIGEST_METADATA_KEY);
        metadata.put("commit_sha", range.path("head_sha").asString());
        // The pull request still stands as it was captured, so the review may go out on it.
        JsonNode record = OBJECT_MAPPER.readTree(
                capturedRecords.path("context/metadata.json").asString());
        jdbcTemplate.update(
                "UPDATE issue SET head_ref_oid = ?, base_ref_oid = ?, title = ?, body = ? WHERE id = ?",
                range.path("head_sha").asString(),
                range.path("base_sha").asString(),
                record.path("title").asString(),
                record.path("body").asString(),
                prId);
        job.setMetadata(metadata);
        job.setEvidenceSnapshot(snapshot);
        job = agentJobRepository.saveAndFlush(job);
        preparedEvidence.add(
                PreparedJobInputsFixtures.prepare(evidenceFiles, job, PreparedJobInputsFixtures.filesOnly(files)));
        preparedJobIds.add(job.getId());
        return agentJobRepository.findById(job.getId()).orElseThrow();
    }

    @Autowired
    private PiResultParser piResultParser;

    @Autowired
    private AgentJobPracticeScriptRunRepository practiceScriptRuns;

    @Autowired
    private MeterRegistry meterRegistry;

    @Nested
    class PartialDelivery {

        private static ArrayNode fourResults() {
            return results(
                    notMet(
                            "pr-description-quality",
                            "The description never says why",
                            Severity.MINOR,
                            "The body lists what changed only."),
                    notMet(
                            "error-handling",
                            "Missing null check",
                            Severity.MAJOR,
                            "The method does not check for null input."),
                    notMet(
                            "parser-input-validation",
                            "Second unchecked input",
                            Severity.MINOR,
                            "The parser does not check its input either."),
                    met("issue-linking", "The description names the issue", "The body closes the issue."));
        }

        @Test
        void aLineNoteThatNeverLandsIsRecordedFailedAndNotBehindTheDeliveredComment() {
            Practice parser = createPractice("parser-input-validation", "Parser input validation");
            Practice linking = createPractice("issue-linking", "Issue linking");
            ObjectNode snapshot = (ObjectNode) Objects.requireNonNull(agentJob.getEvidenceSnapshot());
            EvidenceSnapshotFixtures.admittedPractice(
                    snapshot,
                    parser.getSlug(),
                    Objects.requireNonNull(
                            Objects.requireNonNull(parser.getCurrentRevision()).getId()));
            EvidenceSnapshotFixtures.admittedPractice(
                    snapshot,
                    linking.getSlug(),
                    Objects.requireNonNull(
                            Objects.requireNonNull(linking.getCurrentRevision()).getId()));
            agentJob.setEvidenceSnapshot(snapshot);
            agentJob = agentJobRepository.saveAndFlush(agentJob);
            setJobOutput(fourResults());
            List<PracticeResult> rows = practiceResultRepository.findByAgentJobId(agentJob.getId(), workspace.getId());
            Map<UUID, List<Observation>> problems = problemsOf(rows);
            PracticeResult summarised = row(rows, "The description never says why");
            PracticeResult landing = row(rows, "Missing null check");
            PracticeResult failing = row(rows, "Second unchecked input");
            PracticeResult supporting = row(rows, "The description names the issue");
            // Every negative was admitted with its one problem; a line note anchors on a problem, never a result.
            String summarisedProblem = onlyProblem(problems, summarised);
            String landingProblem = onlyProblem(problems, landing);
            String failingProblem = onlyProblem(problems, failing);
            assertThat(problems.get(supporting.getId()))
                    .as("a positive has no problems")
                    .isNull();
            var inlineA = onLine(landing, problems, 3);
            var inlineB = onLine(failing, problems, 9);
            String failingNote = "The parser reads this input before it checks it; check it first, as on line 3.";
            var review = new ComposedReview(
                    new ComposedReview.Summary(
                            "The description lists what changed but never says why.",
                            List.of(summarised.getId().toString()),
                            List.of(summarisedProblem)),
                    List.of(
                            new ComposedReview.InlineNote(
                                    "Null reaches the handler here. Guard the input before the lookup.",
                                    List.of(landing.getId().toString()),
                                    List.of(landingProblem),
                                    new ComposedReview.ResolvedAnchor(
                                            landing.getId().toString(),
                                            landingProblem,
                                            0,
                                            "src/App.java",
                                            "NEW",
                                            3,
                                            null)),
                            new ComposedReview.InlineNote(
                                    failingNote,
                                    List.of(failing.getId().toString()),
                                    List.of(failingProblem),
                                    new ComposedReview.ResolvedAnchor(
                                            failing.getId().toString(),
                                            failingProblem,
                                            0,
                                            "src/App.java",
                                            "NEW",
                                            9,
                                            null))),
                    List.of());
            List<ValidatedResult> admitted =
                    List.of(validated(summarised, problems), inlineA, inlineB, validated(supporting, problems));
            var decided = AdmittedDelivery.decide(
                    review,
                    ArtifactKinds.PULL_REQUEST,
                    admitted,
                    PullRequestReviewHandler.subjectsOf(
                            rows, rows.stream().map(PracticeResult::getId).collect(Collectors.toSet())),
                    List.of(),
                    admitted);
            if (!(decided instanceof AdmittedDelivery.Automatic automatic)) {
                throw new AssertionError("Every result is automatic, so nothing waits for approval");
            }
            DeliveryContent content = Objects.requireNonNull(automatic.content());
            assertThat(content.diffNotes()).hasSize(2);
            assertThat(content.diffNotes().get(0).observations()).containsExactly(landingProblem);
            assertThat(content.diffNotes().get(1).observations()).containsExactly(failingProblem);
            // One note per cited place of a problem: the result, the problem's position and the citation's index.
            String landingKey = "observation:" + landing.getOccurrenceKey() + ":0:0";
            String failingKey = "observation:" + failing.getOccurrenceKey() + ":0:0";
            when(commentPoster.post(any())).thenReturn(new SummaryHandle("summary-ref"));
            when(diffNotePoster.deliverPackage(eq(agentJob), any(), any(), any(), any(), any()))
                    .thenReturn(
                            incomplete(
                                    signal(landingKey, 3, Disposition.POSTED),
                                    signal(failingKey, 9, Disposition.FAILED)),
                            incomplete(
                                    signal(landingKey, 3, Disposition.PRESERVED_EXISTING),
                                    signal(failingKey, 9, Disposition.FAILED)));

            assertThatThrownBy(() -> feedbackDeliveryService.deliverFeedback(
                            agentJob, content, automatic.contributingPracticeSlugs()))
                    .isInstanceOf(JobDeliveryException.class);
            jdbcTemplate.update(
                    "UPDATE feedback_dispatch SET attempt_count = ?, next_attempt_at = CURRENT_TIMESTAMP"
                            + " WHERE destination_key = ?",
                    PracticeFeedbackDispatchService.MAX_ATTEMPTS - 1,
                    "review:" + agentJob.getId());
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                try {
                    feedbackDeliveryService.recoverAutomaticPackageIfPresent(agentJob);
                } catch (JobDeliveryException stillFailing) {
                    // A package that ends FAILED still reports itself unreconciled; its ledger is what counts here.
                }
                assertThat(stateOf(FeedbackDeliveryState.FAILED)).isNotEmpty();
            });
            try {
                feedbackDeliveryService.recoverAutomaticPackageIfPresent(agentJob);
            } catch (JobDeliveryException stillFailing) {
                // Replaying a terminal package must neither write to the provider nor record it again.
            }

            verify(commentPoster, times(1)).post(any());
            verify(diffNotePoster, times(2)).deliverPackage(eq(agentJob), any(), any(), any(), any(), any());
            assertThat(stateOf(FeedbackDeliveryState.DELIVERED))
                    .as("the summary and the note that landed; never the note that failed")
                    .containsExactlyInAnyOrder(summarised.getId(), landing.getId());
            assertThat(stateOf(FeedbackDeliveryState.FAILED)).containsExactly(failing.getId());
            assertThat(jdbcTemplate.queryForList("""
                            SELECT fe.observation_id FROM feedback_evidence fe
                            JOIN feedback f ON f.id = fe.feedback_id
                            WHERE f.agent_job_id = ? AND f.delivery_state = 'DELIVERED' AND fe.observation_id IS NOT NULL
                            """, UUID.class, agentJob.getId()))
                    .as("the delivered words name the summarised problem and the problem the landed note sits on")
                    .containsExactlyInAnyOrder(UUID.fromString(summarisedProblem), UUID.fromString(landingProblem));
            assertThat(jdbcTemplate.queryForList(
                            "SELECT body FROM feedback WHERE agent_job_id = ? AND delivery_state = 'FAILED'",
                            String.class,
                            agentJob.getId()))
                    .as("the note that never landed is kept as the package sealed it for the pull request")
                    .containsExactly(commentFormatter.appendInlineFeedbackPrompt(failingNote, agentJob));
            assertThat(jdbcTemplate.queryForList("""
                            SELECT fp.posted_comment_ref FROM feedback_placement fp
                            JOIN feedback f ON f.id = fp.feedback_id
                            WHERE f.agent_job_id = ? AND f.delivery_state = 'DELIVERED'
                            """, String.class, agentJob.getId()))
                    .containsExactlyInAnyOrder("summary-ref", "note-" + landingKey);
        }

        private static PracticeResult row(List<PracticeResult> rows, String summary) {
            return rows.stream()
                    .filter(o -> summary.equals(o.getSummary()))
                    .findFirst()
                    .orElseThrow();
        }

        /** The id of the one problem a result was admitted with. */
        private static String onlyProblem(Map<UUID, List<Observation>> problems, PracticeResult row) {
            List<Observation> stored = problems.get(row.getId());
            assertThat(stored).as("the problems of %s", row.getSummary()).hasSize(1);
            return Objects.requireNonNull(stored).getFirst().getId().toString();
        }

        /** The result as a delivery handler reads it: the stored row with its stored problems. */
        private static ValidatedResult validated(PracticeResult row, Map<UUID, List<Observation>> problems) {
            return ValidatedResult.of(row, problems.getOrDefault(row.getId(), List.of()));
        }

        /**
         * The result whose one problem is cited on a verified line of the change, so the composer places a line note
         * there. Admission verified the problem on the captured description; the change is not checked out here.
         */
        private ValidatedResult onLine(PracticeResult row, Map<UUID, List<Observation>> problems, int line) {
            ValidatedResult stored = validated(row, problems);
            ValidatedProblem problem = stored.problems().getFirst();
            ArrayNode citations = OBJECT_MAPPER.createArrayNode();
            ObjectNode citation = citations.addObject();
            citation.put("sourceKind", "scm.pull-request.diff");
            citation.put("artifactPath", "context/diff.patch");
            citation.put("path", "src/App.java");
            citation.put("side", "NEW");
            citation.put("startLine", line);
            citation.put("quote", "return input;");
            CitationVerification.record(
                    citation, agentJob, "a".repeat(64), CitationVerification.quoteDigest("return input;"));
            ValidatedProblem placed = new ValidatedProblem(
                    problem.observationId(),
                    problem.position(),
                    problem.observationKey(),
                    problem.severity(),
                    problem.summary(),
                    problem.explanation(),
                    citations,
                    problem.basis(),
                    problem.nextStep(),
                    problem.recurrenceKey());
            return new ValidatedResult(
                    stored.practiceSlug(),
                    stored.summary(),
                    stored.outcome(),
                    stored.severity(),
                    stored.evidence(),
                    stored.evidenceRationale(),
                    stored.keys(),
                    stored.deliveryBehavior(),
                    List.of(placed),
                    stored.example());
        }

        /** A line comment that landed under {@code disposition}, or for {@code FAILED} one provably never requested. */
        private static DeliveredSignal signal(String key, int line, Disposition disposition) {
            DiffAnchor anchor = new DiffAnchor("src/App.java", line, null);
            return disposition == Disposition.FAILED
                    ? DeliveredSignal.notSent(key, anchor)
                    : new DeliveredSignal(
                            key, anchor, disposition, "note-" + key, "discussion-" + key, null, null, Placement.LINE);
        }

        private static DiffNotePoster.DiffNoteResult incomplete(DeliveredSignal... signals) {
            return new DiffNotePoster.DiffNoteResult(
                    List.of(signals), false, false, false, false, false, List.of(), null);
        }

        /** The results bound to this job's feedback recorded in {@code state}. */
        private List<@Nullable UUID> stateOf(FeedbackDeliveryState state) {
            return jdbcTemplate.queryForList("""
                    SELECT fo.practice_result_id FROM feedback_evidence fo
                    JOIN feedback f ON f.id = fo.feedback_id WHERE f.agent_job_id = ? AND f.delivery_state = ?
                    """, UUID.class, agentJob.getId(), state.name());
        }
    }

    @Nested
    class HappyPath {

        @Test
        void shouldPersistWithoutExternalWritesOrReplayWhenSilentModeIsEngaged() {
            setJobOutput(validAgentOutput());
            engageSilentMode();

            handler.deliver(agentJob);

            assertThat(practiceResultRepository.findAll()).hasSize(2);
            assertThat(feedbackRepository.findAll())
                    .singleElement()
                    .extracting(Feedback::getDeliveryState, Feedback::getSuppressionReason)
                    .containsExactly(FeedbackDeliveryState.SUPPRESSED, FeedbackSuppressionReason.INSTANCE_SILENCED);
            verify(commentPoster, never()).post(any());
            verify(diffNotePoster, never()).deliverPackage(any(), any(), any(), any(), any(), any());

            releaseSilentMode();
            assertThat(feedbackRepository.findAll())
                    .noneMatch(feedback -> feedback.getDeliveryState() == FeedbackDeliveryState.PREPARED);

            AgentJob newEvent = newJobWithOutput(validAgentOutput());
            when(commentPoster.post(any())).thenReturn(new SummaryHandle("comment-after-release"));
            handler.deliver(newEvent);

            verify(commentPoster).post(argThat(write -> write.job().equals(newEvent)));
            assertThat(practiceResultRepository.findAll()).hasSize(4);
            assertThat(feedbackRepository.findAll())
                    .extracting(Feedback::getDeliveryState)
                    .containsExactlyInAnyOrder(FeedbackDeliveryState.SUPPRESSED, FeedbackDeliveryState.DELIVERED);
        }

        @Test
        void fullPipelineFromParseToDelivery() {
            setJobOutput(validAgentOutput());
            when(commentPoster.post(any())).thenReturn(new SummaryHandle("comment-123"));

            handler.deliver(agentJob);

            List<PracticeResult> practiceResults = practiceResultRepository.findAll();
            assertThat(practiceResults).hasSize(2);
            assertThat(practiceResults)
                    .extracting(PracticeResult::getOutcome)
                    .containsExactlyInAnyOrder(Outcome.MET, Outcome.NOT_MET);

            verify(commentPoster).post(argThat(write -> write.job().equals(agentJob)));
            // A package with no line notes writes nothing inline, so it never touches what earlier packages placed.
            verify(diffNotePoster, never()).deliverPackage(any(), any(), any(), any(), any(), any());

            // AgentJobExecutor persists deliveryStatus, not handler.deliver(), so it stays null here.
            assertThat(agentJob.getDeliveryCommentId()).isEqualTo("comment-123");
            assertThat(agentJob.getDeliveryStatus()).isNull();
        }

        @Test
        void allPositiveResultsStayQuietWhenTheReviewDidNotReachEveryPractice() {
            ArrayNode output =
                    results(met("pr-description-quality", "Good description", "The description explains the change."));
            agentJob = admitAndSetOutput(agentJob, output);

            handler.deliver(agentJob);

            assertThat(practiceResultRepository.findAll()).hasSize(1);
            verify(commentPoster, never()).post(any());
        }

        @Test
        void aPartialReviewStillReportsTheProblemItFound() {
            agentJob = admitAndSetOutput(agentJob, validAgentOutput());
            when(commentPoster.post(any())).thenReturn(new SummaryHandle("comment-partial"));

            handler.deliver(agentJob);

            assertThat(practiceResultRepository.findAll()).hasSize(2);
            verify(commentPoster).post(argThat(write -> write.job().equals(agentJob)));
        }
    }

    @Nested
    class ErrorCases {

        @Test
        void unknownSlugRejectsDeliveryAtomically() {
            ArrayNode submitted = results(
                    met("pr-description-quality", "Good description", "The description explains the change."),
                    met("nonexistent-practice", "Unknown practice", "The submitted practice does not exist."),
                    notMet(
                            "error-handling",
                            "Good handling",
                            Severity.MINOR,
                            "The implementation omits the required check."));

            assertThatThrownBy(() -> admit(agentJob, submitted))
                    .isInstanceOfSatisfying(
                            ResultsRefusedException.class,
                            refused -> assertThat(refused.reasonCode())
                                    .isEqualTo(ReviewOutputService.PRACTICE_NOT_ADMITTED))
                    .hasMessageContaining("nonexistent-practice");

            assertThat(practiceResultRepository.findAll()).isEmpty();
            verify(commentPoster, never()).post(any());
            verify(diffNotePoster, never()).deliverPackage(any(), any(), any(), any(), any(), any());
        }

        @Test
        void closedPrSkipsDelivery() {
            var pr = pullRequestRepository.findById(prId).orElseThrow();
            var provider = Objects.requireNonNull(pr.getProvider());
            var author = Objects.requireNonNull(pr.getAuthor());
            var createdAt = Objects.requireNonNull(pr.getCreatedAt());
            pullRequestRepository.upsertCore(
                    8001L,
                    Objects.requireNonNull(provider.getId()),
                    50,
                    "Pipeline Test PR",
                    "Test body",
                    "CLOSED",
                    null,
                    "https://github.com/org/pipeline-repo/pull/50",
                    false,
                    null,
                    0,
                    createdAt,
                    Instant.now(),
                    createdAt,
                    author.getId(),
                    pr.requireRepository().getId(),
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
                    "feature/pipeline",
                    "main",
                    HEAD_SHA,
                    BASE_SHA,
                    null,
                    null // mergeCommitSha
                    );

            setJobOutput(validAgentOutput());

            handler.deliver(agentJob);

            // Results are still persisted: deliver() persists first, then posts.
            assertThat(practiceResultRepository.findAll()).hasSize(2);

            verify(commentPoster, never()).post(any());
            verify(diffNotePoster, never()).deliverPackage(any(), any(), any(), any(), any(), any());

            assertThat(agentJob.getDeliveryCommentId()).isNull();
            assertThat(agentJob.getDeliveryStatus()).isNull();
        }
    }

    @Nested
    class ResultIdempotency {

        @Test
        @DisplayName("re-delivering same job creates no duplicate results")
        void redeliveryNoDuplicates() {
            setJobOutput(validAgentOutput());
            when(commentPoster.post(any())).thenReturn(new SummaryHandle("comment-789"));

            handler.deliver(agentJob);
            assertThat(practiceResultRepository.findAll()).hasSize(2);

            handler.deliver(agentJob);
            assertThat(practiceResultRepository.findAll()).hasSize(2);
        }
    }
}
