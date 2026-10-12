package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignal;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignalRepository;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalState;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackUsefulness;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.ObservationInvalidation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationInvalidationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.Reaction;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

class ReviewLineageIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String LINEAGE = "/workspaces/{slug}/practices/reviews/lineage";
    private static final ParameterizedTypeReference<List<String>> IDS = new ParameterizedTypeReference<>() {};

    @Autowired
    private ConnectionRepository sourceConnections;

    @Autowired
    private ConnectionService sourceConnectionService;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackObservationRepository feedbackObservationRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private ObservationInvalidationRepository invalidationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private ArtifactSignalRepository signalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final AtomicLong NATIVE_IDS = new AtomicLong(880_000);

    private Workspace workspace;
    private Workspace otherWorkspace;
    private Practice practice;
    private Practice otherPractice;
    private AgentJob job;
    private AgentJob otherJob;
    private User alice;

    @BeforeEach
    void setUpWorkspaces() {
        User owner = persistUser("lineage-owner");
        workspace = createWorkspace("lineage-ws", "Lineage WS", "lineage-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        alice = persistUser("alice");
        ensureWorkspaceMembership(workspace, alice, WorkspaceMembership.WorkspaceRole.MEMBER);
        ensureWorkspaceMembership(workspace, persistUser("testuser"), WorkspaceMembership.WorkspaceRole.MEMBER);
        practice = persistPractice(workspace, "pr-description-quality");
        job = persistJob(workspace);

        User otherOwner = persistUser("lineage-other-owner");
        otherWorkspace =
                createWorkspace("lineage-other-ws", "Other WS", "lineage-other-org", AccountType.ORG, otherOwner);
        otherPractice = persistPractice(otherWorkspace, "pr-description-quality");
        otherJob = persistJob(otherWorkspace);
    }

    private Practice persistPractice(Workspace ws, String slug) {
        Practice created = new Practice();
        created.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        created.setWorkspace(ws);
        created.setSlug(slug);
        created.setName("PR Description Quality");
        created.setCriteria("Criteria for " + slug);
        PracticeTestEvidence.configure(created, ScmSignals.PULL_REQUEST_OPENED);
        created.setAutonomy(PracticeAutonomy.AUTOMATIC);
        return practiceRepository.save(created);
    }

    private AgentJob persistJob(Workspace ws) {
        AgentJob agentJob = new AgentJob();
        agentJob.setWorkspace(ws);
        agentJob.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setLlmModel("claude-test");
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob.setEvidenceSnapshot(OBJECT_MAPPER.valueToTree(Map.of("manifest", Map.of("contractVersion", "1.2.0"))));
        return agentJobRepository.save(agentJob);
    }

    private UUID observe(Practice measured, AgentJob agentJob, String summary, String outcome, Instant observedAt) {
        return observe(measured, agentJob, summary, outcome, observedAt, 7L);
    }

    private UUID observe(
            Practice measured, AgentJob agentJob, String summary, String outcome, Instant observedAt, long artifactId) {
        UUID id = UUID.randomUUID();
        observationRepository.insertIfAbsent(
                id,
                "occurrence-" + id,
                agentJob.getId(),
                measured.getWorkspace().getId(),
                measured.getId(),
                null,
                ArtifactKinds.PULL_REQUEST.value(),
                artifactId,
                alice.getId(),
                summary,
                outcome,
                "NOT_MET".equals(outcome) ? "MAJOR" : null,
                "{}",
                "Reasoning for " + summary,
                "recurrence-" + id,
                observedAt,
                "LIVE");
        return id;
    }

    private UUID observe(String summary) {
        return observe(practice, job, summary, "NOT_MET", Instant.now());
    }

    private Feedback compose(
            Workspace ws, AgentJob agentJob, int position, FeedbackDeliveryState state, @Nullable UUID replacesId) {
        return feedbackRepository.save(Feedback.builder()
                .agentJobId(agentJob.getId())
                .workspaceId(ws.getId())
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(7L)
                .recipientUserId(alice.getId())
                .aboutUserId(alice.getId())
                .channel(FeedbackChannel.IN_CONTEXT)
                .position(position)
                .deliveryState(state)
                .body("Feedback " + position)
                .source(FeedbackSource.AGENT)
                .replacesId(replacesId)
                .createdAt(Instant.now().plus(position, ChronoUnit.MILLIS))
                .deliveredAt(state == FeedbackDeliveryState.DELIVERED ? Instant.now() : null)
                .build());
    }

    /** A pull request in a repository that {@code monitoredBy} monitors, merged at {@code mergedAt} if set. */
    private PullRequest pullRequest(Workspace monitoredBy, @Nullable Instant mergedAt) {
        Connection source = sourceConnections.saveAndFlush(new Connection(
                monitoredBy,
                IntegrationKind.GITHUB,
                "pat",
                new ConnectionConfig.GitHubPatConfig("lineage-org", null, Set.of())));
        sourceConnectionService.transition(
                source,
                new ConnectionService.TransitionRequest(
                        IntegrationState.ACTIVE,
                        "TEST_CONNECTED",
                        "SYSTEM",
                        "review-read-test",
                        UUID.randomUUID().toString(),
                        "Source connected"));
        long nativeId = NATIVE_IDS.incrementAndGet();
        String nameWithOwner = "lineage-org/repo-" + nativeId;
        Repository repository = new Repository();
        repository.setNativeId(nativeId);
        repository.setProvider(ensureGitHubProvider());
        repository.setName("repo-" + nativeId);
        repository.setNameWithOwner(nameWithOwner);
        repository.setHtmlUrl("https://github.com/" + nameWithOwner);
        repository.setDefaultBranch("main");
        repository = repositoryRepository.save(repository);
        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setWorkspace(monitoredBy);
        monitor.setNameWithOwner(nameWithOwner);
        repositoryToMonitorRepository.save(monitor);
        PullRequest work = new PullRequest();
        work.setNativeId(nativeId);
        work.setProvider(ensureGitHubProvider());
        work.setNumber((int) (nativeId % 100_000));
        work.setTitle("Pull request " + nativeId);
        work.setState(mergedAt == null ? Issue.State.OPEN : Issue.State.CLOSED);
        work.setMergedAt(mergedAt);
        work.setHtmlUrl(repository.getHtmlUrl() + "/pull/" + nativeId);
        work.setRepository(repository);
        work.setAuthor(alice);
        work.setCreatedAt(Instant.parse("2026-09-01T09:00:00Z"));
        work.setUpdatedAt(Instant.parse("2026-09-01T09:00:00Z"));
        return pullRequestRepository.save(work);
    }

    /** One moment on the pull request, naming {@code agentJob} as the review it started or was folded into. */
    private void recordMoment(
            PullRequest work, SignalName signal, DiscoveredVia via, @Nullable AgentJob agentJob, Instant occurredAt) {
        ArtifactSignal row = new ArtifactSignal();
        row.setId(UUID.randomUUID());
        row.setWorkspace(workspace);
        row.setArtifactKind(ArtifactKinds.PULL_REQUEST.value());
        row.setArtifactId(work.getId());
        row.setSignalName(signal.value());
        row.setRevision("sha~" + occurredAt.getEpochSecond() + "~" + via);
        row.setOccurredAt(occurredAt);
        row.setDiscoveredVia(via);
        row.setState(SignalState.TRIGGERED);
        row.setJobId(agentJob == null ? null : agentJob.getId());
        row.setStateChangedAt(occurredAt);
        signalRepository.save(row);
    }

    private void bind(Feedback piece, UUID observationId, int ordinal) {
        feedbackObservationRepository.insertIfAbsent(piece.getId(), observationId, "PRIMARY", ordinal);
    }

    private WebTestClient.ResponseSpec get(String query, Object... uriVariables) {
        Object[] variables = new Object[uriVariables.length + 1];
        variables[0] = workspace.getWorkspaceSlug();
        System.arraycopy(uriVariables, 0, variables, 1, uriVariables.length);
        return webTestClient
                .get()
                .uri(LINEAGE + query, variables)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange();
    }

    private WebTestClient.BodyContentSpec getOk(String query, Object... uriVariables) {
        return get(query, uriVariables).expectStatus().isOk().expectBody();
    }

    @Test
    @WithAdminUser
    void shouldTraceAnObservationToItsReviewAndTheFeedbackWrittenFromIt() {
        UUID traced = observe("Description omits the why");
        observe("Tests missing");
        observe("Second sibling");
        Feedback piece = compose(workspace, job, 0, FeedbackDeliveryState.DELIVERED, null);
        bind(piece, traced, 0);

        getOk("?kind=OBSERVATION&id={id}", traced)
                .jsonPath("$.review.id")
                .isEqualTo(job.getId().toString())
                .jsonPath("$.review.model")
                .isEqualTo("claude-test")
                .jsonPath("$.work.kind")
                .isEqualTo("scm.pull_request")
                .jsonPath("$.observations.length()")
                .isEqualTo(1)
                .jsonPath("$.observations[0].id")
                .isEqualTo(traced.toString())
                .jsonPath("$.observations[0].focus")
                .isEqualTo(true)
                .jsonPath("$.observations[0].feedbackCount")
                .isEqualTo(1)
                .jsonPath("$.moreObservations")
                .isEqualTo(2)
                .jsonPath("$.feedback.length()")
                .isEqualTo(1)
                .jsonPath("$.feedback[0].id")
                .isEqualTo(piece.getId().toString())
                .jsonPath("$.feedback[0].focus")
                .isEqualTo(true)
                .jsonPath("$.feedback[0].observationIds[0]")
                .isEqualTo(traced.toString())
                .jsonPath("$.feedback[0].deliveryState")
                .isEqualTo("DELIVERED");
    }

    @Test
    @WithAdminUser
    void shouldSayNoFeedbackCameFromAnObservationThatLedToNone() {
        UUID traced = observe("Met the standard");

        getOk("?kind=OBSERVATION&id={id}", traced)
                .jsonPath("$.observations[0].feedbackCount")
                .isEqualTo(0)
                .jsonPath("$.feedback.length()")
                .isEqualTo(0)
                .jsonPath("$.moreFeedback")
                .isEqualTo(0);
    }

    @Test
    @WithAdminUser
    void shouldListEveryObservationFeedbackWasWrittenFromInRenderOrder() {
        UUID first = observe("First");
        UUID second = observe("Second");
        UUID third = observe("Third");
        observe("Unrelated sibling");
        Feedback piece = compose(workspace, job, 0, FeedbackDeliveryState.DELIVERED, null);
        bind(piece, third, 0);
        bind(piece, first, 1);
        bind(piece, second, 2);

        getOk("?kind=FEEDBACK&id={id}", piece.getId())
                .jsonPath("$.observations.length()")
                .isEqualTo(3)
                .jsonPath("$.observations[0].id")
                .isEqualTo(third.toString())
                .jsonPath("$.observations[1].id")
                .isEqualTo(first.toString())
                .jsonPath("$.observations[0].focus")
                .isEqualTo(true)
                .jsonPath("$.observations[2].focus")
                .isEqualTo(true)
                .jsonPath("$.moreObservations")
                .isEqualTo(1)
                .jsonPath("$.feedback[0].observationIds.length()")
                .isEqualTo(3);
    }

    @Test
    @WithAdminUser
    void shouldLinkReplacedFeedbackBothWays() {
        UUID traced = observe("Repeated problem");
        Feedback older = compose(workspace, job, 0, FeedbackDeliveryState.SUPERSEDED, null);
        Feedback newer = compose(workspace, job, 1, FeedbackDeliveryState.DELIVERED, older.getId());
        bind(older, traced, 0);
        bind(newer, traced, 0);

        getOk("?kind=FEEDBACK&id={id}", older.getId())
                .jsonPath("$.feedback[0].replacedById")
                .isEqualTo(newer.getId().toString())
                .jsonPath("$.feedback[0].replacesId")
                .doesNotExist();
        getOk("?kind=FEEDBACK&id={id}", newer.getId())
                .jsonPath("$.feedback[0].replacesId")
                .isEqualTo(older.getId().toString());
    }

    @Test
    @WithAdminUser
    void shouldCarryAStandingDisputeWithItsExplanationButNeverTheRating() {
        UUID traced = observe("Disputed claim");
        Feedback piece = compose(workspace, job, 0, FeedbackDeliveryState.DELIVERED, null);
        bind(piece, traced, 0);
        Instant answeredAt = Instant.now();
        reactionRepository.save(Reaction.builder()
                .feedback(piece)
                .reactorUserId(alice.getId())
                .usefulness(FeedbackUsefulness.UNHELPFUL)
                .resolution(FeedbackResolution.DISPUTED)
                .explanation("The description does say why")
                .createdAt(answeredAt)
                .build());

        getOk("?kind=OBSERVATION&id={id}", traced)
                .jsonPath("$.feedback[0].response.disputed")
                .isEqualTo(true)
                .jsonPath("$.feedback[0].response.disputeExplanation")
                .isEqualTo("The description does say why")
                .jsonPath("$.feedback[0].response.respondedAt")
                .exists()
                .jsonPath("$.feedback[0].response.usefulness")
                .doesNotExist()
                .jsonPath("$.feedback[0].response.resolution")
                .doesNotExist()
                .jsonPath("$.feedback[0].response.explanation")
                .doesNotExist()
                .jsonPath("$.feedback[0].response.resolvedByWorkAt")
                .doesNotExist();
    }

    @Test
    @WithAdminUser
    void shouldExposeActionResponseMetadataButKeepRatingOnlyResponsesPrivate() {
        UUID traced = observe("Answered claim");
        Feedback addressed = compose(workspace, job, 0, FeedbackDeliveryState.DELIVERED, null);
        Feedback notApplicable = compose(workspace, job, 1, FeedbackDeliveryState.DELIVERED, null);
        Feedback rated = compose(workspace, job, 2, FeedbackDeliveryState.DELIVERED, null);
        bind(addressed, traced, 0);
        bind(notApplicable, traced, 0);
        bind(rated, traced, 0);
        reactionRepository.save(Reaction.builder()
                .feedback(addressed)
                .reactorUserId(alice.getId())
                .usefulness(FeedbackUsefulness.HELPFUL)
                .resolution(FeedbackResolution.ADDRESSED)
                .explanation("Fixed in the next push")
                .createdAt(Instant.now())
                .build());
        reactionRepository.save(Reaction.builder()
                .feedback(notApplicable)
                .reactorUserId(alice.getId())
                .resolution(FeedbackResolution.NOT_APPLICABLE)
                .explanation("A private note on why it does not fit")
                .createdAt(Instant.now())
                .build());
        reactionRepository.save(Reaction.builder()
                .feedback(rated)
                .reactorUserId(alice.getId())
                .usefulness(FeedbackUsefulness.UNHELPFUL)
                .explanation("A private comment on the rating")
                .createdAt(Instant.now())
                .build());

        byte[] content = getOk("?kind=OBSERVATION&id={id}", traced)
                .jsonPath("$.feedback.length()")
                .isEqualTo(3)
                .jsonPath("$.feedback[?(@.id=='" + addressed.getId() + "')].response.respondedAt")
                .exists()
                .jsonPath("$.feedback[?(@.id=='" + addressed.getId() + "')].response.disputed")
                .isEqualTo(false)
                .jsonPath("$.feedback[?(@.id=='" + notApplicable.getId() + "')].response.respondedAt")
                .exists()
                .jsonPath("$.feedback[?(@.id=='" + notApplicable.getId() + "')].response.disputed")
                .isEqualTo(false)
                .jsonPath("$.feedback[?(@.id=='" + rated.getId() + "')].response")
                .doesNotExist()
                .returnResult()
                .getResponseBodyContent();
        String body = content == null ? "" : new String(content, StandardCharsets.UTF_8);
        assertThat(body)
                .doesNotContain("\"usefulness\"")
                .doesNotContain("\"resolution\"")
                .doesNotContain("Fixed in the next push")
                .doesNotContain("A private note on why it does not fit")
                .doesNotContain("A private comment on the rating");
        reactionRepository.saveAndFlush(Reaction.builder()
                .feedback(addressed)
                .reactorUserId(alice.getId())
                .createdAt(Instant.now().plusSeconds(1))
                .build());
        getOk("?kind=OBSERVATION&id={id}", traced)
                .jsonPath("$.feedback[?(@.id=='" + addressed.getId() + "')].response")
                .doesNotExist();
    }

    @Test
    @WithAdminUser
    void shouldSayWhereFeedbackWentWhoDecidedAboutItAndWhetherItWasWithdrawn() {
        UUID traced = observe("Needs a decision");
        Feedback rejected = compose(workspace, job, 0, FeedbackDeliveryState.DISCARDED, null);
        Feedback card = compose(workspace, job, 1, FeedbackDeliveryState.DELIVERED, null);
        bind(rejected, traced, 0);
        bind(card, traced, 0);
        Instant decidedAt = Instant.parse("2026-09-01T10:00:00Z");
        Instant withdrawnAt = Instant.parse("2026-09-02T08:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO feedback_approval (id, feedback_id, workspace_id, decision, rejection_reason,"
                        + " content_digest, decided_at) VALUES (?, ?, ?, 'REJECTED', 'UNHELPFUL', ?, ?)",
                UUID.randomUUID(),
                rejected.getId(),
                workspace.getId(),
                "0".repeat(64),
                Timestamp.from(decidedAt));
        jdbcTemplate.update(
                "UPDATE feedback SET channel = 'IN_APP' WHERE id = ? AND workspace_id = ?",
                card.getId(),
                workspace.getId());
        jdbcTemplate.update(
                "INSERT INTO feedback_withdrawal (id, workspace_id, feedback_id, reason, withdrawn_at)"
                        + " VALUES (?, ?, ?, 'Wrong words', ?)",
                UUID.randomUUID(),
                workspace.getId(),
                card.getId(),
                Timestamp.from(withdrawnAt));

        getOk("?kind=FEEDBACK&id={id}", rejected.getId())
                .jsonPath("$.feedback[0].decision")
                .isEqualTo("REJECTED")
                .jsonPath("$.feedback[0].decidedAt")
                .isEqualTo("2026-09-01T10:00:00Z")
                .jsonPath("$.feedback[0].location")
                .isEqualTo("OTHER_WORK")
                .jsonPath("$.feedback[0].withdrawnAt")
                .doesNotExist();
        getOk("?kind=FEEDBACK&id={id}", card.getId())
                .jsonPath("$.feedback[0].location")
                .isEqualTo("PRACTICE_PAGES")
                .jsonPath("$.feedback[0].withdrawnAt")
                .isEqualTo("2026-09-02T08:00:00Z")
                .jsonPath("$.feedback[0].decision")
                .doesNotExist();
    }

    @Test
    @WithAdminUser
    void shouldCapAReviewsObservationsAndCountTheRest() {
        for (int index = 0; index < ReviewLineageService.OBSERVATION_CAP + 2; index++) {
            observe("Observation " + index);
        }

        getOk("?kind=REVIEW&id={id}", job.getId())
                .jsonPath("$.observations.length()")
                .isEqualTo(ReviewLineageService.OBSERVATION_CAP)
                .jsonPath("$.moreObservations")
                .isEqualTo(2)
                .jsonPath("$.review.status")
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    @WithAdminUser
    void shouldTraceTheNewestReviewOfAPieceOfWork() {
        observe(practice, job, "Older review", "NOT_MET", Instant.now().minus(1, ChronoUnit.DAYS));
        AgentJob newest = persistJob(workspace);
        observe(practice, newest, "Newest review", "MET", Instant.now());

        getOk("?kind=WORK&artifactKind=scm.pull_request&artifactId=7")
                .jsonPath("$.review.id")
                .isEqualTo(newest.getId().toString())
                .jsonPath("$.observations[0].summary")
                .isEqualTo("Newest review");
    }

    @Test
    @WithAdminUser
    void shouldNotTraceAnotherWorkspacesRecords() {
        UUID theirs = observe(otherPractice, otherJob, "Theirs", "NOT_MET", Instant.now());
        Feedback theirFeedback = compose(otherWorkspace, otherJob, 0, FeedbackDeliveryState.DELIVERED, null);

        get("?kind=OBSERVATION&id={id}", theirs).expectStatus().isNotFound().expectBody(Void.class);
        get("?kind=FEEDBACK&id={id}", theirFeedback.getId())
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
        get("?kind=REVIEW&id={id}", otherJob.getId())
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
    }

    @Test
    @WithAdminUser
    void shouldSayWhenTheWorkWasMergedAndThatTheMergeStartedTheReview() {
        Instant mergedAt = Instant.parse("2026-09-01T14:51:00Z");
        PullRequest work = pullRequest(workspace, mergedAt);
        UUID traced = observe(practice, job, "Merged past an open thread", "NOT_MET", Instant.now(), work.getId());
        recordMoment(work, ScmSignals.PULL_REQUEST_MERGED, DiscoveredVia.EVENT, job, mergedAt);
        // A later moment folded into the same review does not change what started it.
        recordMoment(work, ScmSignals.PULL_REQUEST_CLOSED, DiscoveredVia.EVENT, job, mergedAt.plusSeconds(5));

        getOk("?kind=OBSERVATION&id={id}", traced)
                .jsonPath("$.work.id")
                .isEqualTo(work.getId().toString())
                .jsonPath("$.workMergedAt")
                .isEqualTo("2026-09-01T14:51:00Z")
                .jsonPath("$.reviewTrigger")
                .isEqualTo("MERGED");
    }

    @Test
    @WithAdminUser
    void shouldSayAReviewOfOpenWorkWasRequested() {
        PullRequest work = pullRequest(workspace, null);
        UUID traced = observe(practice, job, "Requested", "NOT_MET", Instant.now(), work.getId());
        recordMoment(
                work, ScmSignals.PULL_REQUEST_OPENED, DiscoveredVia.MANUAL, job, Instant.parse("2026-09-01T10:00:00Z"));

        getOk("?kind=OBSERVATION&id={id}", traced)
                .jsonPath("$.workMergedAt")
                .doesNotExist()
                .jsonPath("$.reviewTrigger")
                .isEqualTo("REQUESTED");
    }

    @Test
    @WithAdminUser
    void shouldNotReadTheMergeOrTheMomentsOfAnotherWorkspace() {
        PullRequest theirs = pullRequest(otherWorkspace, Instant.parse("2026-09-01T14:51:00Z"));
        UUID traced = observe(practice, job, "Same id, other workspace", "NOT_MET", Instant.now(), theirs.getId());
        ArtifactSignal moment = new ArtifactSignal();
        moment.setId(UUID.randomUUID());
        moment.setWorkspace(otherWorkspace);
        moment.setArtifactKind(ArtifactKinds.PULL_REQUEST.value());
        moment.setArtifactId(theirs.getId());
        moment.setSignalName(ScmSignals.PULL_REQUEST_READY.value());
        moment.setRevision("sha~other");
        moment.setOccurredAt(Instant.now());
        moment.setDiscoveredVia(DiscoveredVia.EVENT);
        moment.setState(SignalState.TRIGGERED);
        moment.setJobId(job.getId());
        moment.setStateChangedAt(Instant.now());
        signalRepository.save(moment);

        getOk("?kind=OBSERVATION&id={id}&whole=true", traced)
                .jsonPath("$.workMergedAt")
                .doesNotExist()
                .jsonPath("$.reviewTrigger")
                .doesNotExist()
                .jsonPath("$.workMoments.length()")
                .isEqualTo(0);
    }

    @Test
    @WithAdminUser
    void shouldListTheWholeReviewAroundAnObservationPastTheShortChainsCaps() {
        UUID traced = observe("Traced");
        for (int index = 0; index < ReviewLineageService.OBSERVATION_CAP + 1; index++) {
            observe("Sibling " + index);
        }
        Feedback fromTraced = compose(workspace, job, 0, FeedbackDeliveryState.DELIVERED, null);
        bind(fromTraced, traced, 0);
        for (int index = 1; index <= ReviewLineageService.FEEDBACK_CAP + 1; index++) {
            compose(workspace, job, index, FeedbackDeliveryState.DELIVERED, null);
        }
        // Another review wrote feedback from the traced observation too: it belongs to the chain.
        AgentJob later = persistJob(workspace);
        Feedback fromLater = compose(workspace, later, 20, FeedbackDeliveryState.DELIVERED, null);
        bind(fromLater, traced, 0);

        int observations = ReviewLineageService.OBSERVATION_CAP + 2;
        int feedback = ReviewLineageService.FEEDBACK_CAP + 3;
        getOk("?kind=OBSERVATION&id={id}&whole=true", traced)
                .jsonPath("$.observations.length()")
                .isEqualTo(observations)
                .jsonPath("$.moreObservations")
                .isEqualTo(0)
                .jsonPath("$.observations[0].id")
                .isEqualTo(traced.toString())
                .jsonPath("$.observations[0].focus")
                .isEqualTo(true)
                .jsonPath("$.observations[1].focus")
                .isEqualTo(false)
                .jsonPath("$.feedback.length()")
                .isEqualTo(feedback)
                .jsonPath("$.moreFeedback")
                .isEqualTo(0)
                .jsonPath("$.feedback[?(@.id == '%s')].focus".formatted(fromTraced.getId()))
                .isEqualTo(true)
                .jsonPath("$.feedback[?(@.id == '%s')].agentJobId".formatted(fromLater.getId()))
                .isEqualTo(later.getId().toString())
                .jsonPath("$.feedback[?(@.focus == true)].id")
                .value(
                        IDS,
                        ids -> assertThat(ids)
                                .containsExactlyInAnyOrder(
                                        fromTraced.getId().toString(),
                                        fromLater.getId().toString()));
    }

    @Test
    @WithAdminUser
    void shouldListTheWholeReviewAroundFeedbackWithTheObservationsOfAnotherReview() {
        AgentJob earlier = persistJob(workspace);
        UUID carried = observe(practice, earlier, "Seen by an earlier review", "NOT_MET", Instant.now());
        UUID own = observe("Seen by this review");
        observe("Sibling");
        Feedback piece = compose(workspace, job, 0, FeedbackDeliveryState.DELIVERED, null);
        bind(piece, carried, 0);
        bind(piece, own, 1);

        getOk("?kind=FEEDBACK&id={id}&whole=true", piece.getId())
                .jsonPath("$.review.id")
                .isEqualTo(job.getId().toString())
                .jsonPath("$.observations.length()")
                .isEqualTo(3)
                .jsonPath("$.observations[0].id")
                .isEqualTo(carried.toString())
                .jsonPath("$.observations[?(@.focus == true)].id")
                .value(IDS, ids -> assertThat(ids).containsExactlyInAnyOrder(carried.toString(), own.toString()))
                .jsonPath("$.feedback[0].focus")
                .isEqualTo(true);
    }

    @Test
    @WithAdminUser
    void shouldListTheWorksMomentsAndWhenTheReviewFinishedOnlyForTheWholeReview() {
        Instant mergedAt = Instant.parse("2026-09-01T14:51:00Z");
        PullRequest work = pullRequest(workspace, mergedAt);
        job.setCompletedAt(Instant.parse("2026-09-01T14:55:00Z"));
        agentJobRepository.save(job);
        observe(practice, job, "Merged past an open thread", "NOT_MET", Instant.now(), work.getId());
        recordMoment(
                work, ScmSignals.PULL_REQUEST_OPENED, DiscoveredVia.EVENT, null, Instant.parse("2026-09-01T13:30:00Z"));
        recordMoment(
                work,
                ScmSignals.PULL_REQUEST_SYNCHRONIZED,
                DiscoveredVia.EVENT,
                null,
                Instant.parse("2026-09-01T14:20:00Z"));
        recordMoment(work, ScmSignals.PULL_REQUEST_MERGED, DiscoveredVia.EVENT, job, mergedAt);
        // The same merge found again by a sync is one moment.
        recordMoment(work, ScmSignals.PULL_REQUEST_MERGED, DiscoveredVia.SYNC, null, mergedAt);

        getOk("?kind=REVIEW&id={id}&whole=true", job.getId())
                .jsonPath("$.review.finishedAt")
                .isEqualTo("2026-09-01T14:55:00Z")
                .jsonPath("$.workMoments.length()")
                .isEqualTo(3)
                .jsonPath("$.workMoments[0].kind")
                .isEqualTo("OPENED")
                .jsonPath("$.workMoments[0].startedReview")
                .isEqualTo(false)
                .jsonPath("$.workMoments[1].kind")
                .isEqualTo("PUSHED")
                .jsonPath("$.workMoments[2].kind")
                .isEqualTo("MERGED")
                .jsonPath("$.workMoments[2].occurredAt")
                .isEqualTo("2026-09-01T14:51:00Z")
                .jsonPath("$.workMoments[2].startedReview")
                .isEqualTo(true);
        getOk("?kind=REVIEW&id={id}", job.getId())
                .jsonPath("$.workMoments.length()")
                .isEqualTo(0);
    }

    @Test
    @WithAdminUser
    void shouldKeepTheMomentThatStartedTheReviewWhenManyCameAfterIt() {
        PullRequest work = pullRequest(workspace, null);
        observe(practice, job, "Many pushes after the start", "NOT_MET", Instant.now(), work.getId());
        Instant opened = Instant.parse("2026-09-01T09:00:00Z");
        recordMoment(work, ScmSignals.PULL_REQUEST_OPENED, DiscoveredVia.EVENT, job, opened);
        for (int push = 1; push <= 70; push++) {
            recordMoment(
                    work,
                    ScmSignals.PULL_REQUEST_SYNCHRONIZED,
                    DiscoveredVia.EVENT,
                    null,
                    opened.plusSeconds(60L * push));
        }

        getOk("?kind=REVIEW&id={id}&whole=true", job.getId())
                .jsonPath("$.reviewTrigger")
                .isEqualTo("OPENED")
                .jsonPath("$.workMoments.length()")
                .isEqualTo(ReviewLineageService.MOMENT_CAP)
                .jsonPath("$.workMoments[0].kind")
                .isEqualTo("OPENED")
                .jsonPath("$.workMoments[0].occurredAt")
                .isEqualTo("2026-09-01T09:00:00Z")
                .jsonPath("$.workMoments[0].startedReview")
                .isEqualTo(true)
                .jsonPath("$.workMoments[1].startedReview")
                .isEqualTo(false);
    }

    @Test
    @WithAdminUser
    void shouldSayOnlyTheFirstMomentStartedTheReviewAndTheLaterOneJoinedIt() {
        PullRequest work = pullRequest(workspace, null);
        observe(practice, job, "Two pushes, one review", "NOT_MET", Instant.now(), work.getId());
        recordMoment(
                work, ScmSignals.PULL_REQUEST_READY, DiscoveredVia.EVENT, job, Instant.parse("2026-09-01T13:30:00Z"));
        recordMoment(
                work,
                ScmSignals.PULL_REQUEST_SYNCHRONIZED,
                DiscoveredVia.EVENT,
                job,
                Instant.parse("2026-09-01T13:31:00Z"));

        getOk("?kind=REVIEW&id={id}&whole=true", job.getId())
                .jsonPath("$.reviewTrigger")
                .isEqualTo("READY")
                .jsonPath("$.workMoments[0].kind")
                .isEqualTo("READY")
                .jsonPath("$.workMoments[0].startedReview")
                .isEqualTo(true)
                .jsonPath("$.workMoments[0].joinedReview")
                .isEqualTo(false)
                .jsonPath("$.workMoments[1].kind")
                .isEqualTo("PUSHED")
                .jsonPath("$.workMoments[1].startedReview")
                .isEqualTo(false)
                .jsonPath("$.workMoments[1].joinedReview")
                .isEqualTo(true);
    }

    @Test
    @WithAdminUser
    void shouldSayWhyACompletedReviewRecordedNothing() {
        job.setStatus(AgentJobStatus.COMPLETED);
        job.setCompletedAt(Instant.parse("2026-09-01T09:05:00Z"));
        job.setOutput(OBJECT_MAPPER.valueToTree(Map.of("outcome", "COALESCED")));
        agentJobRepository.save(job);
        AgentJob insufficient = persistJob(workspace);
        insufficient.setStatus(AgentJobStatus.COMPLETED);
        insufficient.setCompletedAt(Instant.parse("2026-09-01T09:06:00Z"));
        insufficient.setOutput(OBJECT_MAPPER.valueToTree(Map.of("outcome", "INSUFFICIENT_EVIDENCE")));
        agentJobRepository.save(insufficient);
        AgentJob reviewed = persistJob(workspace);
        reviewed.setStatus(AgentJobStatus.COMPLETED);
        reviewed.setCompletedAt(Instant.parse("2026-09-01T09:07:00Z"));
        agentJobRepository.save(reviewed);

        getOk("?kind=REVIEW&id={id}", job.getId()).jsonPath("$.review.outcome").isEqualTo("COALESCED");
        getOk("?kind=REVIEW&id={id}", insufficient.getId())
                .jsonPath("$.review.outcome")
                .isEqualTo("INSUFFICIENT_EVIDENCE");
        getOk("?kind=REVIEW&id={id}", reviewed.getId())
                .jsonPath("$.review.outcome")
                .isEqualTo("REVIEWED");
    }

    @Test
    @WithAdminUser
    void shouldSayHowTheReviewEndedWhenItStartedAndWhatItWroteItself() {
        Instant picked = Instant.parse("2026-09-01T09:02:53Z");
        job.setStatus(AgentJobStatus.TIMED_OUT);
        job.setStartedAt(picked);
        agentJobRepository.save(job);
        UUID own = observe("Written about here");
        UUID later = observe("Written about later");
        AgentJob laterJob = persistJob(workspace);
        Feedback ownPiece = compose(workspace, job, 0, FeedbackDeliveryState.DELIVERED, null);
        bind(ownPiece, own, 0);
        Feedback laterPiece = compose(workspace, laterJob, 1, FeedbackDeliveryState.DELIVERED, ownPiece.getId());
        bind(laterPiece, later, 0);

        getOk("?kind=OBSERVATION&id={id}", later)
                .jsonPath("$.review.phase")
                .isEqualTo("TIMED_OUT")
                .jsonPath("$.review.status")
                .isEqualTo("FAILED")
                .jsonPath("$.review.startedAt")
                .isEqualTo("2026-09-01T09:02:53Z")
                .jsonPath("$.review.outcome")
                .doesNotExist()
                .jsonPath("$.observations[0].feedbackCount")
                .isEqualTo(1)
                .jsonPath("$.observations[0].ownFeedbackCount")
                .isEqualTo(0);
        getOk("?kind=FEEDBACK&id={id}", ownPiece.getId())
                .jsonPath("$.feedback[0].replacedById")
                .isEqualTo(laterPiece.getId().toString())
                .jsonPath("$.feedback[0].replacedAt")
                .isNotEmpty();
    }

    @Test
    @WithAdminUser
    void shouldSayAReviewIsHeldOnlyWhileItWaits() {
        job.setStatus(AgentJobStatus.QUEUED);
        job.setHoldReason(AgentJob.HOLD_REASON_BUDGET);
        agentJobRepository.save(job);
        UUID traced = observe("Held for the budget");

        getOk("?kind=OBSERVATION&id={id}", traced)
                .jsonPath("$.review.holdReason")
                .isEqualTo("BUDGET");

        // The executor clears the hold only on claim, so a job cancelled while held keeps the marker.
        job.setStatus(AgentJobStatus.CANCELLED);
        agentJobRepository.save(job);
        getOk("?kind=OBSERVATION&id={id}", traced)
                .jsonPath("$.review.holdReason")
                .doesNotExist();
    }

    @Test
    @WithAdminUser
    void shouldSayWhenAnObservationWasMarkedIncorrect() {
        UUID traced = observe("Wrong when made");
        invalidationRepository.save(new ObservationInvalidation(
                observationRepository
                        .findByIdAndWorkspaceId(traced, workspace.getId())
                        .orElseThrow(),
                1L,
                "Wrong when made",
                Instant.parse("2026-09-01T13:58:00Z")));

        getOk("?kind=OBSERVATION&id={id}", traced)
                .jsonPath("$.observations[0].invalidated")
                .isEqualTo(true)
                .jsonPath("$.observations[0].markedIncorrectAt")
                .isEqualTo("2026-09-01T13:58:00Z");
    }

    @Test
    @WithAdminUser
    void shouldRejectARequestThatNamesNothing() {
        get("?kind=OBSERVATION").expectStatus().isBadRequest().expectBody(Void.class);
        get("?kind=WORK&artifactId=7").expectStatus().isBadRequest().expectBody(Void.class);
    }

    @Test
    @WithUser
    void shouldForbidAWorkspaceMember() {
        UUID traced = observe("Hidden from members");

        get("?kind=OBSERVATION&id={id}", traced).expectStatus().isForbidden().expectBody(Void.class);
    }
}
