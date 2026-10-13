package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.mentor.ChatMessage;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.mentor.ThreadSurface;
import de.tum.cit.aet.hephaestus.practices.PracticeGroupRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluation;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicySurface;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacement;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackWithdrawalRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementAnchorKind;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementAnchorSide;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementType;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResultInvalidation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultInvalidationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.reaction.Reaction;
import de.tum.cit.aet.hephaestus.practices.result.reaction.ReactionRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PracticeReviewOutputControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String RESULTS = "/workspaces/{slug}/practices/reviews/results";
    private static final String OBSERVATIONS = "/workspaces/{slug}/practices/reviews/observations";
    private static final String FEEDBACK = "/workspaces/{slug}/practices/reviews/feedback";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeResultRepository practiceResultRepository;

    @Autowired
    private PracticeRevisionRepository practiceRevisionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeGroupRepository practiceGroupRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackEvidenceRepository feedbackEvidenceRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private FeedbackPlacementRepository feedbackPlacementRepository;

    @Autowired
    private DeliveryPolicyEvaluationRepository deliveryPolicyEvaluationRepository;

    @Autowired
    private PracticeResultInvalidationRepository invalidationRepository;

    @Autowired
    private FeedbackWithdrawalRepository withdrawalRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private Workspace workspace;
    private Workspace otherWorkspace;
    private Practice practiceA;
    private Practice practiceB;
    private Practice otherPractice;
    private AgentJob job;
    private AgentJob otherJob;
    private User alice;
    private User bob;
    private User workspaceMember;

    @BeforeEach
    void setUpWorkspaces() {
        User owner = persistUser("review-output-owner");
        workspace =
                createWorkspace("review-output-ws", "Review Output WS", "review-output-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        alice = persistUser("alice");
        bob = persistUser("bob");
        workspaceMember = persistUser("testuser");
        User plainWorkspaceAdmin = persistUser("mentor");
        ensureWorkspaceMembership(workspace, alice, WorkspaceMembership.WorkspaceRole.MEMBER);
        ensureWorkspaceMembership(workspace, bob, WorkspaceMembership.WorkspaceRole.MEMBER);
        ensureWorkspaceMembership(workspace, workspaceMember, WorkspaceMembership.WorkspaceRole.MEMBER);
        ensureWorkspaceMembership(workspace, plainWorkspaceAdmin, WorkspaceMembership.WorkspaceRole.ADMIN);

        practiceA = persistPractice(workspace, "pr-description-quality", "PR Description Quality");
        practiceB = persistPractice(workspace, "test-coverage", "Test Coverage");
        job = persistJob(workspace);

        User otherOwner = persistUser("other-owner");
        otherWorkspace = createWorkspace("other-ws", "Other WS", "other-org", AccountType.ORG, otherOwner);
        otherPractice = persistPractice(otherWorkspace, "pr-description-quality", "PR Description Quality");
        otherJob = persistJob(otherWorkspace);
    }

    private Practice persistPractice(Workspace ws, String slug, String name) {
        Practice practice = new Practice();
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        practice.setWorkspace(ws);
        practice.setSlug(slug);
        practice.setName(name);
        practice.setCriteria("Criteria for " + slug);
        PracticeTestEvidence.configure(practice, ScmSignals.PULL_REQUEST_OPENED);
        practice.setAutonomy(PracticeAutonomy.AUTOMATIC);
        return practiceRepository.save(practice);
    }

    private PracticeGroup persistGroup(Workspace ws, String slug, String name) {
        PracticeGroup group = new PracticeGroup();
        group.setWorkspace(ws);
        group.setSlug(slug);
        group.setName(name);
        group.setIcon("MessageSquareText");
        group.setColor("blue");
        return practiceGroupRepository.save(group);
    }

    private AgentJob persistJob(Workspace ws) {
        AgentJob agentJob = new AgentJob();
        agentJob.setWorkspace(ws);
        agentJob.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob.setEvidenceSnapshot(OBJECT_MAPPER.valueToTree(Map.of("manifest", Map.of("contractVersion", "1.2.0"))));
        return agentJobRepository.save(agentJob);
    }

    private UUID insertProblem(Practice practice, AgentJob agentJob, User about, String title, String severity) {
        return insertResult(practice, agentJob, about, title, "NOT_MET", severity, 7L, Instant.now());
    }

    private UUID insertResult(
            Practice practice,
            AgentJob agentJob,
            User about,
            String title,
            String outcome,
            @Nullable String severity,
            Long artifactId,
            Instant observedAt) {
        UUID id = UUID.randomUUID();
        practiceResultRepository.insertIfAbsent(
                id,
                "occurrence-" + id,
                agentJob.getId(),
                practice.getWorkspace().getId(),
                practice.getId(),
                null,
                ArtifactKinds.PULL_REQUEST.value(),
                artifactId,
                about.getId(),
                title,
                outcome,
                severity,
                "{\"citations\":[{\"sourceKind\":\"scm.pull-request.diff\",\"artifactPath\":\"context/diff.patch\",\"path\":\"src/Main.java\",\"side\":\"NEW\",\"startLine\":42,\"endLine\":50,\"quote\":\"example\",\"quoteRedacted\":false}]}",
                "Reasoning for " + title,
                "test-scope",
                null,
                null,
                null,
                observedAt,
                "LIVE");
        return id;
    }

    private static String diffCitation(String path, int line) {
        return """
            {"sourceKind":"scm.pull-request.diff","artifactPath":"context/diff.patch","path":"%s",\
            "side":"NEW","startLine":%d,"endLine":%d,"quote":"catch (Exception e) {}","quoteRedacted":false}\
            """.formatted(path, line, line);
    }

    /** Files one located problem under a NOT_MET result, as admission writes it. */
    private void addProblem(UUID practiceResultId, int position, String severity, String path, int line) {
        assertThat(observationRepository.insert(
                        UUID.randomUUID(),
                        workspace.getId(),
                        practiceResultId,
                        position,
                        null,
                        severity,
                        "Problem at " + path,
                        "The catch block drops the error.",
                        "[" + diffCitation(path, line) + "]",
                        null,
                        null,
                        null))
                .isOne();
    }

    private Feedback persistUnit(
            Workspace ws,
            AgentJob agentJob,
            User recipient,
            int position,
            FeedbackDeliveryState state,
            @Nullable FeedbackSuppressionReason reason,
            @Nullable String body) {
        return persistUnit(
                ws, agentJob, recipient, position, state, reason, body, Instant.now(), ArtifactKinds.PULL_REQUEST, 7L);
    }

    private Feedback persistUnit(
            Workspace ws,
            AgentJob agentJob,
            User recipient,
            int position,
            FeedbackDeliveryState state,
            @Nullable FeedbackSuppressionReason reason,
            @Nullable String body,
            Instant createdAt) {
        return persistUnit(
                ws, agentJob, recipient, position, state, reason, body, createdAt, ArtifactKinds.PULL_REQUEST, 7L);
    }

    private Feedback persistUnit(
            Workspace ws,
            AgentJob agentJob,
            User recipient,
            int position,
            FeedbackDeliveryState state,
            @Nullable FeedbackSuppressionReason reason,
            @Nullable String body,
            Instant createdAt,
            ArtifactKind artifactKind,
            Long artifactId) {
        return feedbackRepository.save(Feedback.builder()
                .agentJobId(agentJob.getId())
                .workspaceId(ws.getId())
                .artifactKind(artifactKind)
                .artifactId(artifactId)
                .recipientUserId(recipient.getId())
                .aboutUserId(recipient.getId())
                .channel(FeedbackChannel.IN_CONTEXT)
                .position(position)
                .deliveryState(state)
                .suppressionReason(reason)
                .body(body)
                .source(FeedbackSource.AGENT)
                .createdAt(createdAt)
                .deliveredAt(
                        state == FeedbackDeliveryState.DELIVERED || state == FeedbackDeliveryState.SUPERSEDED
                                ? Instant.now()
                                : null)
                .build());
    }

    private WebTestClient.ResponseSpec get(String uri, Object... uriVariables) {
        return webTestClient
                .get()
                .uri(uri, uriVariables)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange();
    }

    private WebTestClient.ResponseSpec patchValidity(
            Workspace ws, UUID practiceResultId, boolean valid, String reason) {
        return webTestClient
                .patch()
                .uri(RESULTS + "/{id}/validity", ws.getWorkspaceSlug(), practiceResultId)
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("valid", valid, "reason", reason))
                .exchange();
    }

    private WebTestClient.BodyContentSpec getOk(String uri, Object... uriVariables) {
        return get(uri, uriVariables).expectStatus().isOk().expectBody();
    }

    private void expectResolvedPullRequestArtifact(String uri, String path, Object... uriVariables) {
        getOk(uri, uriVariables)
                .jsonPath(path + ".kind")
                .isEqualTo("scm.pull_request")
                .jsonPath(path + ".id")
                .isEqualTo("7")
                .jsonPath(path + ".label")
                .isEqualTo("#42")
                .jsonPath(path + ".provider")
                .isEqualTo("GITHUB")
                .jsonPath(path + ".title")
                .isEqualTo("Make review output visible")
                .jsonPath(path + ".container")
                .isEqualTo("review-output-org/review-ui")
                .jsonPath(path + ".url")
                .isEqualTo("https://github.com/review-output-org/review-ui/pull/42");
    }

    private void bind(Feedback unit, UUID practiceResultId) {
        feedbackEvidenceRepository.insertIfAbsent(unit.getId(), practiceResultId, null, "PRIMARY", 0);
    }

    @Nested
    @DisplayName("Access control")
    class AccessControl {

        @Test
        void anonymousCallerCannotReadReviewOutput() {
            webTestClient
                    .get()
                    .uri(RESULTS, workspace.getWorkspaceSlug())
                    .exchange()
                    .expectStatus()
                    .isUnauthorized()
                    .expectBody(Void.class);
        }

        @Test
        @WithUser
        void workspaceMemberCannotReadReviewOutput() {
            get(RESULTS, workspace.getWorkspaceSlug())
                    .expectStatus()
                    .isForbidden()
                    .expectBody(Void.class);
        }

        @Test
        @WithMentorUser
        void workspaceAdminWithoutInstanceAuthorityIsAdmitted() {
            get(RESULTS, workspace.getWorkspaceSlug()).expectStatus().isOk().expectBody(Void.class);
        }
    }

    @Nested
    @DisplayName("Practice results")
    class PracticeResults {

        @Test
        @WithAdminUser
        void spansEveryDeveloperNotJustTheCaller() {
            insertProblem(practiceA, job, alice, "Alice problem", "MAJOR");
            insertProblem(practiceB, job, bob, "Bob problem", "MINOR");

            getOk(RESULTS, workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(2)
                    .jsonPath("$.content[?(@.summary == 'Alice problem')].subject.login")
                    .isEqualTo("alice")
                    .jsonPath("$.content[?(@.summary == 'Alice problem')].claimCurrentness")
                    .isEqualTo("UNVERIFIABLE")
                    .jsonPath("$.content[?(@.summary == 'Bob problem')].subject.login")
                    .isEqualTo("bob");
        }

        @Test
        @WithAdminUser
        void excludesOtherWorkspaces() {
            insertProblem(practiceA, job, alice, "Mine", "MAJOR");
            insertProblem(otherPractice, otherJob, bob, "Theirs", "MAJOR");

            getOk(RESULTS, workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].summary")
                    .isEqualTo("Mine");
        }

        @Test
        @WithAdminUser
        void detailOfAnotherWorkspaceIsNotFound() {
            UUID theirs = insertProblem(otherPractice, otherJob, bob, "Theirs", "MAJOR");

            get(RESULTS + "/{id}", workspace.getWorkspaceSlug(), theirs)
                    .expectStatus()
                    .isNotFound()
                    .expectBody(Void.class);
        }

        @Test
        @WithAdminUser
        void shouldFilterEveryOutcomeDirectly() {
            insertResult(practiceA, job, alice, "Risk avoided", "MET", null, 7L, Instant.now());
            insertResult(practiceA, job, alice, "No occasion", "NOT_APPLICABLE", null, 7L, Instant.now());
            insertResult(practiceA, job, alice, "Criterion unresolved", "UNDETERMINED", null, 7L, Instant.now());
            for (String status : List.of("NOT_APPLICABLE", "UNDETERMINED")) {
                getOk(RESULTS + "?agentJobId={id}&outcome={status}", workspace.getWorkspaceSlug(), job.getId(), status)
                        .jsonPath("$.page.totalElements")
                        .isEqualTo(1)
                        .jsonPath("$.content[0].outcome")
                        .isEqualTo(status)
                        .jsonPath("$.content[0].presence")
                        .doesNotExist()
                        .jsonPath("$.content[0].assessment")
                        .doesNotExist()
                        .jsonPath("$.content[0].severity")
                        .doesNotExist();
            }
            getOk(RESULTS + "?agentJobId={id}&outcome=MET", workspace.getWorkspaceSlug(), job.getId())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].summary")
                    .isEqualTo("Risk avoided")
                    .jsonPath("$.content[0].outcome")
                    .isEqualTo("MET");
        }

        @Test
        @WithAdminUser
        void filtersByPracticeSeverityAndSubject() {
            insertProblem(practiceA, job, alice, "A major", "MAJOR");
            insertProblem(practiceA, job, alice, "A minor", "MINOR");
            insertProblem(practiceB, job, bob, "B major", "MAJOR");

            getOk(
                            RESULTS + "?practiceSlug={slug}&severity=MAJOR&subjectUserId={uid}",
                            workspace.getWorkspaceSlug(),
                            practiceA.getSlug(),
                            alice.getId())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].summary")
                    .isEqualTo("A major");
        }

        @Test
        @WithAdminUser
        void filtersByGroupAndReturnsItsMetadata() {
            PracticeGroup group = persistGroup(workspace, "communication", "Communication");
            practiceA.setGroup(group);
            practiceRepository.save(practiceA);
            UUID practiceResultId = insertProblem(practiceA, job, alice, "In group", "MAJOR");
            insertProblem(practiceB, job, bob, "Ungrouped", "MAJOR");

            getOk(RESULTS + "?groupSlug=communication", workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].summary")
                    .isEqualTo("In group")
                    .jsonPath("$.content[0].group.slug")
                    .isEqualTo("communication")
                    .jsonPath("$.content[0].group.name")
                    .isEqualTo("Communication")
                    .jsonPath("$.content[0].group.icon")
                    .isEqualTo("MessageSquareText")
                    .jsonPath("$.content[0].group.color")
                    .isEqualTo("blue");

            getOk(RESULTS + "/{id}", workspace.getWorkspaceSlug(), practiceResultId)
                    .jsonPath("$.group.slug")
                    .isEqualTo("communication");
        }

        @Test
        @WithAdminUser
        void filtersBySeveralSeverities() {
            insertProblem(practiceA, job, alice, "Critical", "CRITICAL");
            insertProblem(practiceA, job, alice, "Major", "MAJOR");
            insertProblem(practiceA, job, alice, "Info", "INFO");

            getOk(RESULTS + "?severity=CRITICAL&severity=MAJOR", workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(2);
        }

        @Test
        @WithAdminUser
        void filtersByRunAndArtifact() {
            insertProblem(practiceA, job, alice, "This run", "MAJOR");
            AgentJob second = persistJob(workspace);
            insertResult(practiceA, second, alice, "Other run", "NOT_MET", "MAJOR", 9L, Instant.now());

            getOk(RESULTS + "?agentJobId={id}", workspace.getWorkspaceSlug(), job.getId())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].summary")
                    .isEqualTo("This run");

            getOk(RESULTS + "?artifactKind=scm.pull_request&artifactId=9", workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].summary")
                    .isEqualTo("Other run");
        }

        @Test
        @WithAdminUser
        void sortsResultsByActionabilityWithoutChangingTheDefault() {
            record ResultInput(
                    String summary,
                    String outcome,
                    @Nullable String severity) {}

            Instant base = Instant.parse("2026-01-10T00:00:00Z");
            List<ResultInput> practiceResults = List.of(
                    new ResultInput("Critical problem", "NOT_MET", "CRITICAL"),
                    new ResultInput("Major problem", "NOT_MET", "MAJOR"),
                    new ResultInput("Minor problem", "NOT_MET", "MINOR"),
                    new ResultInput("Info problem", "NOT_MET", "INFO"),
                    new ResultInput("Strength", "MET", null),
                    new ResultInput("Not applicable", "NOT_APPLICABLE", null));
            for (int i = 0; i < practiceResults.size(); i++) {
                ResultInput practiceResult = practiceResults.get(i);
                insertResult(
                        practiceA,
                        job,
                        alice,
                        practiceResult.summary(),
                        practiceResult.outcome(),
                        practiceResult.severity(),
                        7L,
                        base.plusSeconds(i));
            }

            getOk(RESULTS + "?agentJobId={id}&sort=ACTIONABILITY&size=5", workspace.getWorkspaceSlug(), job.getId())
                    .jsonPath("$.content[0].summary")
                    .isEqualTo("Critical problem")
                    .jsonPath("$.content[1].summary")
                    .isEqualTo("Major problem")
                    .jsonPath("$.content[2].summary")
                    .isEqualTo("Minor problem")
                    .jsonPath("$.content[3].summary")
                    .isEqualTo("Info problem")
                    .jsonPath("$.content[4].summary")
                    .isEqualTo("Strength");

            getOk(RESULTS + "?agentJobId={id}&size=5", workspace.getWorkspaceSlug(), job.getId())
                    .jsonPath("$.content[0].summary")
                    .isEqualTo("Not applicable")
                    .jsonPath("$.content[4].summary")
                    .isEqualTo("Major problem");
        }

        @Test
        @WithAdminUser
        void filtersByObservedAtWindow() {
            Instant from = Instant.parse("2026-01-10T00:00:00Z");
            Instant to = Instant.parse("2026-01-20T00:00:00Z");
            insertResult(practiceA, job, alice, "Before", "NOT_MET", "MAJOR", 7L, from.minusSeconds(1));
            insertResult(practiceA, job, alice, "Inside", "NOT_MET", "MAJOR", 8L, from);
            insertResult(practiceA, job, alice, "At end", "NOT_MET", "MAJOR", 9L, to);

            getOk(RESULTS + "?from={from}&to={to}", workspace.getWorkspaceSlug(), from, to)
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].summary")
                    .isEqualTo("Inside");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "?artifactId=7",
                    "?subjectUserId=-1",
                    "?from=2026-01-20T00:00:00Z&to=2026-01-10T00:00:00Z",
                    "?sort=UNKNOWN",
                })
        @WithAdminUser
        void rejectsInvalidResultQuery(String query) {
            get(RESULTS + query, workspace.getWorkspaceSlug())
                    .expectStatus()
                    .isBadRequest()
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody()
                    .jsonPath("$.status")
                    .isEqualTo(400);
        }

        @Test
        @WithAdminUser
        void reportsEveryLinkedFeedbackOutcome() {
            UUID practiceResultId = insertProblem(practiceA, job, alice, "Every outcome", "MAJOR");
            bind(
                    persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "Posted body"),
                    practiceResultId);
            bind(
                    persistUnit(workspace, job, alice, 1, FeedbackDeliveryState.SUPERSEDED, null, "Old body"),
                    practiceResultId);
            bind(persistUnit(workspace, job, alice, 2, FeedbackDeliveryState.PREPARED, null, null), practiceResultId);
            bind(
                    persistUnit(
                            workspace,
                            job,
                            alice,
                            3,
                            FeedbackDeliveryState.SUPPRESSED,
                            FeedbackSuppressionReason.VOLUME_CAPPED,
                            null),
                    practiceResultId);
            bind(
                    persistUnit(workspace, job, alice, 4, FeedbackDeliveryState.FAILED, null, "Failed body"),
                    practiceResultId);

            getOk(RESULTS, workspace.getWorkspaceSlug())
                    .jsonPath("$.content[0].feedback.prepared")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].feedback.delivered")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].feedback.superseded")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].feedback.suppressed")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].feedback.failed")
                    .isEqualTo(1);
        }

        @Test
        @WithAdminUser
        void shouldCountAndReturnOneParentResultWhenFeedbackBindsTwoOfItsProblems() {
            UUID result = insertProblem(practiceA, job, alice, "Two problems", "MAJOR");
            addProblem(result, 0, "MAJOR", "src/Auth.java", 8);
            addProblem(result, 1, "MINOR", "src/Auth.java", 14);
            var problems = observationRepository.findByPracticeResultIds(workspace.getId(), List.of(result));
            Feedback unit = persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "Both places");
            for (int ordinal = 0; ordinal < problems.size(); ordinal++) {
                feedbackEvidenceRepository.insertIfAbsent(
                        unit.getId(), result, problems.get(ordinal).getId(), "PRIMARY", ordinal);
            }

            getOk(RESULTS + "?agentJobId={id}", workspace.getWorkspaceSlug(), job.getId())
                    .jsonPath("$.content[0].feedback.delivered")
                    .isEqualTo(1);
            getOk(FEEDBACK + "?agentJobId={id}", workspace.getWorkspaceSlug(), job.getId())
                    .jsonPath("$.content[0].practiceResultCount")
                    .isEqualTo(1);
            getOk(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), unit.getId())
                    .jsonPath("$.practiceResults.length()")
                    .isEqualTo(1)
                    .jsonPath("$.practiceResults[0].practiceResultId")
                    .isEqualTo(result.toString())
                    .jsonPath("$.practiceResults[0].role")
                    .isEqualTo("PRIMARY")
                    .jsonPath("$.practiceResults[0].ordinal")
                    .isEqualTo(0);
            getOk(
                            "/workspaces/{slug}/practices/reviews/lineage?kind=FEEDBACK&id={id}",
                            workspace.getWorkspaceSlug(),
                            unit.getId())
                    .jsonPath("$.results.length()")
                    .isEqualTo(1)
                    .jsonPath("$.results[0].feedbackCount")
                    .isEqualTo(1)
                    .jsonPath("$.feedback[0].practiceResultIds")
                    .isEqualTo(List.of(result.toString()));
        }

        /** Feedback waiting on an admin, or rejected by one, was still composed from the result. */
        @Test
        @WithAdminUser
        void shouldCountFeedbackAwaitingApprovalAndDiscardedWhenBoundToTheResult() {
            UUID practiceResultId = insertProblem(practiceA, job, alice, "Needs approval", "MAJOR");
            bind(
                    persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.AWAITING_APPROVAL, null, "Proposed"),
                    practiceResultId);
            bind(
                    persistUnit(workspace, job, alice, 1, FeedbackDeliveryState.DISCARDED, null, "Rejected"),
                    practiceResultId);

            getOk(RESULTS, workspace.getWorkspaceSlug())
                    .jsonPath("$.content[0].id")
                    .isEqualTo(practiceResultId.toString())
                    .jsonPath("$.content[0].feedback.awaitingApproval")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].feedback.discarded")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].feedback.prepared")
                    .isEqualTo(0)
                    .jsonPath("$.content[0].feedback.suppressed")
                    .isEqualTo(0);
        }

        @Test
        @WithAdminUser
        void shouldListOnlyResultsWithThatOutcomeWhenFilteredByOutcome() {
            Instant now = Instant.now();
            UUID metDescription =
                    insertResult(practiceA, job, alice, "Description meets the criteria", "MET", null, 7L, now);
            UUID metIssueLink =
                    insertResult(practiceA, job, alice, "Issue link meets the criteria", "MET", null, 7L, now);
            UUID notMetErrorHandling =
                    insertProblem(practiceA, job, alice, "Error handling does not meet the criteria", "MAJOR");
            UUID notMetTests =
                    insertResult(practiceB, job, bob, "Tests do not meet the criteria", "NOT_MET", "MINOR", 7L, now);
            insertResult(practiceA, job, alice, "Not applicable", "NOT_APPLICABLE", null, 7L, now);

            expectListed("?outcome=MET", metDescription, metIssueLink);
            expectListed("?outcome=NOT_MET", notMetErrorHandling, notMetTests);
            expectListed(
                    "?outcome=MET&outcome=NOT_MET", metDescription, metIssueLink, notMetErrorHandling, notMetTests);
        }

        @Test
        @WithAdminUser
        void shouldListOnlyResultsMarkedIncorrectWhenFilteredByInvalidated() {
            UUID markedIncorrect = insertProblem(practiceA, job, alice, "Marked incorrect", "MAJOR");
            UUID restored = insertProblem(practiceA, job, alice, "Restored", "MAJOR");
            UUID standing = insertProblem(practiceB, job, bob, "Standing", "MINOR");
            invalidate(markedIncorrect);
            PracticeResultInvalidation correction = invalidate(restored);
            correction.restore(1L, "It was right", Instant.now());
            invalidationRepository.save(correction);

            expectListed("?invalidated=true", markedIncorrect);
            expectListed("?invalidated=false", restored, standing);
            expectListed("", markedIncorrect, restored, standing);
        }

        /**
         * A developer's dispute reaches the admins on its result and on the feedback, with their explanation,
         * and filters the list; a dispute they withdrew does neither.
         */
        @Test
        @WithAdminUser
        void shouldShowTheDevelopersStandingDisputeOnItsResultAndFilterByIt() {
            UUID disputed = insertProblem(practiceA, job, alice, "Disputed", "MAJOR");
            UUID withdrawn = insertProblem(practiceA, job, alice, "Dispute withdrawn", "MAJOR");
            UUID undisputed = insertProblem(practiceB, job, bob, "Undisputed", "MINOR");
            Feedback standing = persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "One");
            bind(standing, disputed);
            Feedback takenBack = persistUnit(workspace, job, alice, 1, FeedbackDeliveryState.DELIVERED, null, "Two");
            bind(takenBack, withdrawn);
            Instant earlier = Instant.now().minusSeconds(60);
            respond(standing, FeedbackResolution.DISPUTED, "The caller retries the export.", earlier);
            respond(takenBack, FeedbackResolution.DISPUTED, "Not my change.", earlier);
            respond(takenBack, null, null, Instant.now());

            expectListed("?disputed=true", disputed);
            expectListed("?disputed=false", withdrawn, undisputed);
            getOk(RESULTS + "/{id}", workspace.getWorkspaceSlug(), disputed)
                    .jsonPath("$.disputes.length()")
                    .isEqualTo(1)
                    .jsonPath("$.disputes[0].feedbackId")
                    .isEqualTo(standing.getId().toString())
                    .jsonPath("$.disputes[0].channel")
                    .isEqualTo("IN_CONTEXT")
                    .jsonPath("$.disputes[0].explanation")
                    .isEqualTo("The caller retries the export.");
            getOk(RESULTS + "/{id}", workspace.getWorkspaceSlug(), withdrawn)
                    .jsonPath("$.disputes.length()")
                    .isEqualTo(0);
            getOk(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), standing.getId())
                    .jsonPath("$.dispute.explanation")
                    .isEqualTo("The caller retries the export.");
            getOk(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), takenBack.getId())
                    .jsonPath("$.dispute")
                    .doesNotExist();
        }

        private void respond(
                Feedback feedback, @Nullable FeedbackResolution resolution, @Nullable String explanation, Instant at) {
            reactionRepository.save(Reaction.builder()
                    .feedback(feedback)
                    .reactorUserId(feedback.getRecipientUserId())
                    .resolution(resolution)
                    .explanation(explanation)
                    .createdAt(at)
                    .build());
        }

        /** Each overview count opens the result list on exactly the rows it counted. */
        @Test
        @WithAdminUser
        void shouldListAsManyResultsAsTheOverviewCountsWhenFilteredToTheSameRange() {
            Instant inside = Instant.parse("2026-02-03T12:00:00Z");
            insertResult(practiceA, job, alice, "Strength", "MET", null, 7L, inside);
            insertResult(practiceB, job, bob, "Another strength", "MET", null, 7L, inside);
            invalidate(insertResult(practiceA, job, alice, "Problem", "NOT_MET", "MAJOR", 7L, inside));
            insertResult(practiceB, job, bob, "Not applicable", "NOT_APPLICABLE", null, 7L, inside);
            // On the exclusive upper bound and just before the lower one: counted by neither.
            invalidate(insertResult(
                    practiceA, job, alice, "Late", "MET", null, 7L, Instant.parse("2026-02-08T00:00:00Z")));
            insertResult(practiceA, job, alice, "Early", "NOT_MET", "MAJOR", 7L, Instant.parse("2026-01-31T23:59:59Z"));
            String range = "from=2026-02-01T00:00:00Z&to=2026-02-08T00:00:00Z";

            WebTestClient.BodyContentSpec overview =
                    getOk("/workspaces/{slug}/practices/reviews/overview?" + range, workspace.getWorkspaceSlug());
            overview.jsonPath("$.practiceResults.met")
                    .isEqualTo(2)
                    .jsonPath("$.practiceResults.notMet")
                    .isEqualTo(1)
                    .jsonPath("$.practiceResultsInvalidated")
                    .isEqualTo(1)
                    .jsonPath("$.practices[0].practiceSlug")
                    .isEqualTo(practiceA.getSlug())
                    .jsonPath("$.practices[0].practiceResults.met")
                    .isEqualTo(1)
                    .jsonPath("$.practices[0].practiceResults.notMet")
                    .isEqualTo(1)
                    .jsonPath("$.practices[0].practiceResultsInvalidated")
                    .isEqualTo(1)
                    .jsonPath("$.practices[1].practiceSlug")
                    .isEqualTo(practiceB.getSlug())
                    .jsonPath("$.practices[1].practiceResults.met")
                    .isEqualTo(1);
            expectTotal("?outcome=MET&" + range, 2);
            expectTotal("?outcome=NOT_MET&" + range, 1);
            expectTotal("?invalidated=true&" + range, 1);
            String practiceARange = "practiceSlug=" + practiceA.getSlug() + "&" + range;
            expectTotal("?outcome=MET&" + practiceARange, 1);
            expectTotal("?outcome=NOT_MET&" + practiceARange, 1);
            expectTotal("?invalidated=true&" + practiceARange, 1);
            expectTotal("?outcome=MET&practiceSlug=" + practiceB.getSlug() + "&" + range, 1);
        }

        private void expectListed(String query, UUID... ids) {
            getOk(RESULTS + query, workspace.getWorkspaceSlug())
                    .jsonPath("$.content[*].id")
                    .value(listed -> assertThat(listed)
                            .asInstanceOf(InstanceOfAssertFactories.LIST)
                            .containsExactlyInAnyOrder(
                                    Arrays.stream(ids).map(UUID::toString).toArray()));
        }

        private void expectTotal(String query, int total) {
            getOk(RESULTS + query, workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(total);
        }

        private PracticeResultInvalidation invalidate(UUID practiceResultId) {
            return invalidationRepository.save(new PracticeResultInvalidation(
                    practiceResultRepository
                            .findByIdAndWorkspaceId(practiceResultId, workspace.getId())
                            .orElseThrow(),
                    1L,
                    "Wrong when made",
                    Instant.now()));
        }

        @Test
        @WithAdminUser
        void reportsZeroFeedbackCountsWhenNeverBound() {
            insertProblem(practiceA, job, alice, "Orphan", "MAJOR");

            getOk(RESULTS, workspace.getWorkspaceSlug())
                    .jsonPath("$.content[0].feedback.prepared")
                    .isEqualTo(0)
                    .jsonPath("$.content[0].feedback.delivered")
                    .isEqualTo(0)
                    .jsonPath("$.content[0].feedback.superseded")
                    .isEqualTo(0)
                    .jsonPath("$.content[0].feedback.suppressed")
                    .isEqualTo(0)
                    .jsonPath("$.content[0].feedback.failed")
                    .isEqualTo(0);
        }

        @Test
        @WithAdminUser
        void excludesCrossWorkspaceFeedbackBindings() {
            UUID practiceResultId = insertProblem(practiceA, job, alice, "Local result", "MAJOR");
            Feedback foreignUnit = persistUnit(
                    otherWorkspace, otherJob, bob, 0, FeedbackDeliveryState.DELIVERED, null, "Foreign body");
            bind(foreignUnit, practiceResultId);

            getOk(RESULTS, workspace.getWorkspaceSlug())
                    .jsonPath("$.content[0].feedback.delivered")
                    .isEqualTo(0);

            getOk(RESULTS + "/{id}", workspace.getWorkspaceSlug(), practiceResultId)
                    .jsonPath("$.feedback.length()")
                    .isEqualTo(0);
        }

        @Test
        @WithAdminUser
        void detailCarriesEvidenceReasoningAndTheUnitsItFed() {
            UUID id = insertProblem(practiceA, job, alice, "Detailed", "MAJOR");
            bind(
                    persistUnit(
                            workspace,
                            job,
                            alice,
                            5000,
                            FeedbackDeliveryState.SUPPRESSED,
                            FeedbackSuppressionReason.ARTIFACT_CLOSED,
                            "Would have said this"),
                    id);

            getOk(RESULTS + "/{id}", workspace.getWorkspaceSlug(), id)
                    .jsonPath("$.evidence.citations[0].path")
                    .isEqualTo("src/Main.java")
                    .jsonPath("$.evidenceRationale")
                    .isEqualTo("Reasoning for Detailed")
                    .jsonPath("$.subject.login")
                    .isEqualTo("alice")
                    .jsonPath("$.feedback.length()")
                    .isEqualTo(1)
                    .jsonPath("$.feedback[0].deliveryState")
                    .isEqualTo("SUPPRESSED")
                    .jsonPath("$.feedback[0].suppressionReason")
                    .isEqualTo("ARTIFACT_CLOSED")
                    .jsonPath("$.feedback[0].role")
                    .isEqualTo("PRIMARY");
        }

        @Test
        @WithAdminUser
        void shouldCountEachProblemOfAResultWhenListingResults() {
            AgentJob run = persistJob(workspace);
            UUID twoProblems =
                    insertResult(practiceA, run, alice, "Two problems", "NOT_MET", "MAJOR", 7L, Instant.now());
            addProblem(twoProblems, 0, "MAJOR", "src/Recorder.java", 212);
            addProblem(twoProblems, 1, "MINOR", "src/Player.java", 40);
            UUID strength = insertResult(practiceB, run, bob, "A strength", "MET", null, 7L, Instant.now());

            getOk(RESULTS + "?agentJobId={id}", workspace.getWorkspaceSlug(), run.getId())
                    .jsonPath("$.content[?(@.id == '%s')].problemCount".formatted(twoProblems))
                    .isEqualTo(2)
                    .jsonPath("$.content[?(@.id == '%s')].problemCount".formatted(strength))
                    .isEqualTo(0)
                    .jsonPath("$.content[0].recurrenceKey")
                    .doesNotExist();
        }

        @Test
        @WithAdminUser
        void shouldShowTheProblemsExampleCoverageAndProvenanceWhenOpeningAResult() {
            UUID id = UUID.randomUUID();
            String own = diffCitation("README.md", 3);
            String problemPlace = diffCitation("src/Recorder.java", 212);
            practiceResultRepository.insertIfAbsent(
                    id,
                    "occurrence-" + id,
                    job.getId(),
                    workspace.getId(),
                    practiceA.getId(),
                    null,
                    ArtifactKinds.PULL_REQUEST.value(),
                    7L,
                    alice.getId(),
                    "Errors are swallowed",
                    "NOT_MET",
                    "MAJOR",
                    "{\"citations\":[" + own + "," + problemPlace + "]}",
                    "One catch block drops errors.",
                    "scm.pull_request:7:AUTHOR",
                    "{\"citation\":" + diffCitation("src/Uploader.java", 9) + ",\"note\":\"The uploader rethrows.\"}",
                    "{\"source\":\"claimed\",\"complete\":true,\"selected\":3,\"checked\":3,\"omitted\":[]}",
                    "{\"jobId\":\"" + job.getId() + "\",\"attempt\":2,\"practiceRevisionId\":null,"
                            + "\"program\":{\"kind\":\"CUSTOM\",\"digest\":\"" + "a".repeat(64) + "\"},"
                            + "\"capturedRevision\":\"" + "b".repeat(40) + "\"}",
                    Instant.now(),
                    "LIVE");
            UUID problem = UUID.randomUUID();
            observationRepository.insert(
                    problem,
                    workspace.getId(),
                    id,
                    0,
                    "recorder",
                    "MAJOR",
                    "The recorder swallows its error",
                    "The catch block drops the error the recorder reports.",
                    "[" + problemPlace + "]",
                    "{\"type\":\"worsened\",\"head\":0,\"base\":0,\"explanation\":\"The catch now drops" + " more.\"}",
                    "Log the error and rethrow it.",
                    "recurrence-recorder");

            getOk(RESULTS + "/{id}", workspace.getWorkspaceSlug(), id)
                    .jsonPath("$.evidence.citations.length()")
                    .isEqualTo(1)
                    .jsonPath("$.evidence.citations[0].path")
                    .isEqualTo("README.md")
                    .jsonPath("$.observations.length()")
                    .isEqualTo(1)
                    .jsonPath("$.observations[0].observationId")
                    .isEqualTo(problem.toString())
                    .jsonPath("$.observations[0].position")
                    .isEqualTo(0)
                    .jsonPath("$.observations[0].severity")
                    .isEqualTo("MAJOR")
                    .jsonPath("$.observations[0].summary")
                    .isEqualTo("The recorder swallows its error")
                    .jsonPath("$.observations[0].citations[0].path")
                    .isEqualTo("src/Recorder.java")
                    .jsonPath("$.observations[0].basis.type")
                    .isEqualTo("worsened")
                    .jsonPath("$.observations[0].nextStep")
                    .isEqualTo("Log the error and rethrow it.")
                    .jsonPath("$.example.note")
                    .isEqualTo("The uploader rethrows.")
                    .jsonPath("$.example.citation.path")
                    .isEqualTo("src/Uploader.java")
                    .jsonPath("$.coverage.source")
                    .isEqualTo("claimed")
                    .jsonPath("$.coverage.checked")
                    .isEqualTo(3)
                    .jsonPath("$.provenance.jobId")
                    .isEqualTo(job.getId().toString())
                    .jsonPath("$.provenance.attempt")
                    .isEqualTo(2)
                    .jsonPath("$.provenance.programKind")
                    .isEqualTo("CUSTOM")
                    .jsonPath("$.provenance.programDigest")
                    .isEqualTo("a".repeat(64))
                    .jsonPath("$.provenance.capturedRevision")
                    .isEqualTo("b".repeat(40))
                    .jsonPath("$.recurrenceKey")
                    .doesNotExist();
        }

        @Test
        @WithAdminUser
        void shouldShowNoProblemExampleCoverageOrProvenanceWhenAResultRecordedNone() {
            UUID id = insertResult(practiceA, job, alice, "Historical strength", "MET", null, 7L, Instant.now());

            getOk(RESULTS + "/{id}", workspace.getWorkspaceSlug(), id)
                    .jsonPath("$.evidenceHidden")
                    .isEqualTo(false)
                    .jsonPath("$.observations")
                    .isEmpty()
                    .jsonPath("$.evidence.citations[0].path")
                    .isEqualTo("src/Main.java")
                    .jsonPath("$.example")
                    .doesNotExist()
                    .jsonPath("$.coverage")
                    .doesNotExist()
                    .jsonPath("$.provenance")
                    .doesNotExist();
        }
    }

    @Nested
    @DisplayName("Observations")
    class Observations {

        @Test
        @WithAdminUser
        void shouldListEachObservationNewestResultFirstWithItsPlaceResultAndDeveloper() {
            PracticeGroup group = persistGroup(workspace, "reliability", "Reliability");
            practiceA.setGroup(group);
            practiceRepository.save(practiceA);
            AgentJob run = persistJob(workspace);
            Instant now = Instant.now();
            UUID older = insertResult(
                    practiceA, run, alice, "Errors are swallowed", "NOT_MET", "MAJOR", 7L, now.minusSeconds(60));
            addProblem(older, 1, "MINOR", "src/Player.java", 40);
            addProblem(older, 0, "MAJOR", "src/Recorder.java", 212);
            UUID newer = insertResult(practiceB, run, bob, "No tests", "NOT_MET", "MINOR", 7L, now);
            addProblem(newer, 0, "MINOR", "src/Cache.java", 9);
            insertResult(practiceB, run, alice, "A strength", "MET", null, 7L, now);

            getOk(OBSERVATIONS + "?agentJobId={id}", workspace.getWorkspaceSlug(), run.getId())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(3)
                    .jsonPath("$.content[*].path")
                    .isEqualTo(List.of("src/Cache.java", "src/Recorder.java", "src/Player.java"))
                    .jsonPath("$.content[1].id")
                    .isNotEmpty()
                    .jsonPath("$.content[1].practiceResultId")
                    .isEqualTo(older.toString())
                    .jsonPath("$.content[1].agentJobId")
                    .isEqualTo(run.getId().toString())
                    .jsonPath("$.content[1].practiceSlug")
                    .isEqualTo("pr-description-quality")
                    .jsonPath("$.content[1].group.slug")
                    .isEqualTo("reliability")
                    .jsonPath("$.content[1].severity")
                    .isEqualTo("MAJOR")
                    .jsonPath("$.content[1].summary")
                    .isEqualTo("Problem at src/Recorder.java")
                    .jsonPath("$.content[1].sourceKind")
                    .isEqualTo("scm.pull-request.diff")
                    .jsonPath("$.content[1].side")
                    .isEqualTo("NEW")
                    .jsonPath("$.content[1].startLine")
                    .isEqualTo(212)
                    .jsonPath("$.content[1].endLine")
                    .isEqualTo(212)
                    .jsonPath("$.content[1].evidenceHidden")
                    .isEqualTo(false)
                    .jsonPath("$.content[1].reviewedWork.kind")
                    .isEqualTo("scm.pull_request")
                    .jsonPath("$.content[1].subject.login")
                    .isEqualTo("alice")
                    .jsonPath("$.content[0].subject.login")
                    .isEqualTo("bob")
                    .jsonPath("$.content[0].group")
                    .doesNotExist();
        }

        @Test
        @WithAdminUser
        void shouldKeepTheFirstCitationsSourceAndSideForCodeSlackAndDocumentPlaces() {
            UUID result = insertProblem(practiceA, job, alice, "Located problems", "MAJOR");
            List<String> citations = List.of(
                    "[" + diffCitation("src/Before.java", 8).replace("\"NEW\"", "\"OLD\"") + "]", """
                    [{"sourceKind":"slack.conversation.thread","artifactPath":"context/conversation_thread.json",
                      "path":"thread","startLine":3,"endLine":3,"quote":"Please explain the change."}]
                    """, """
                    [{"sourceKind":"docs.document.core","artifactPath":"context/document.json",
                      "path":"document","startLine":6,"endLine":6,"quote":"Document the decision."}]
                    """, "[]");
            for (int position = 0; position < citations.size(); position++) {
                assertThat(observationRepository.insert(
                                UUID.randomUUID(),
                                workspace.getId(),
                                result,
                                position,
                                null,
                                "MINOR",
                                "A located problem",
                                "The practice is not met here.",
                                citations.get(position),
                                null,
                                null,
                                null))
                        .isOne();
            }

            getOk(OBSERVATIONS + "?practiceResultId={id}", workspace.getWorkspaceSlug(), result)
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(4)
                    .jsonPath("$.content[0].sourceKind")
                    .isEqualTo("scm.pull-request.diff")
                    .jsonPath("$.content[0].side")
                    .isEqualTo("OLD")
                    .jsonPath("$.content[1].sourceKind")
                    .isEqualTo("slack.conversation.thread")
                    .jsonPath("$.content[1].side")
                    .doesNotExist()
                    .jsonPath("$.content[2].sourceKind")
                    .isEqualTo("docs.document.core")
                    .jsonPath("$.content[2].side")
                    .doesNotExist()
                    .jsonPath("$.content[3].sourceKind")
                    .doesNotExist()
                    .jsonPath("$.content[3].side")
                    .doesNotExist()
                    .jsonPath("$.content[3].path")
                    .doesNotExist();
        }

        @Test
        @WithAdminUser
        void shouldShowTheParentResultsCorrectionAndCurrentnessOnItsObservations() {
            PracticeRevision revision = practiceRevisionRepository.save(new PracticeRevision(practiceA, 1));
            practiceA.setCurrentRevision(revision);
            practiceRepository.save(practiceA);
            UUID result = insertProblem(practiceA, job, alice, "A problem", "MAJOR");
            jdbcTemplate.update(
                    "UPDATE practice_result SET practice_revision_id = ? WHERE id = ?", revision.getId(), result);
            addProblem(result, 0, "MAJOR", "src/Problem.java", 1);
            String slug = workspace.getWorkspaceSlug();
            String list = OBSERVATIONS + "?practiceResultId={id}";
            getOk(list, slug, result)
                    .jsonPath("$.content[0].claimCurrentness")
                    .isEqualTo("CURRENT")
                    .jsonPath("$.content[0].invalidatedAt")
                    .doesNotExist()
                    .jsonPath("$.content[0].disputedAt")
                    .doesNotExist();

            patchValidity(workspace, result, false, "Wrong location")
                    .expectStatus()
                    .isOk()
                    .expectBody(Void.class);
            getOk(list, slug, result).jsonPath("$.content[0].invalidatedAt").isNotEmpty();
            patchValidity(workspace, result, true, "The location was correct")
                    .expectStatus()
                    .isOk()
                    .expectBody(Void.class);
            getOk(list, slug, result).jsonPath("$.content[0].invalidatedAt").doesNotExist();

            Feedback feedback =
                    persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "Check this place.");
            bind(feedback, result);
            reactionRepository.save(Reaction.builder()
                    .feedback(feedback)
                    .reactorUserId(alice.getId())
                    .resolution(FeedbackResolution.DISPUTED)
                    .explanation("This is expected.")
                    .createdAt(Instant.now())
                    .build());
            getOk(list, slug, result).jsonPath("$.content[0].disputedAt").isNotEmpty();

            jdbcTemplate.update("UPDATE practice_result SET superseded_at = now() WHERE id = ?", result);
            getOk(list, slug, result).jsonPath("$.content[0].claimCurrentness").isEqualTo("STALE");
        }

        @Test
        @WithAdminUser
        void shouldFilterBySeverityPracticePersonResultAndRange() {
            AgentJob run = persistJob(workspace);
            Instant now = Instant.now();
            UUID aliceResult = insertResult(practiceA, run, alice, "Alice", "NOT_MET", "MAJOR", 7L, now);
            addProblem(aliceResult, 0, "MAJOR", "src/Major.java", 1);
            addProblem(aliceResult, 1, "MINOR", "src/Minor.java", 2);
            UUID bobResult =
                    insertResult(practiceB, run, bob, "Bob", "NOT_MET", "CRITICAL", 7L, now.minusSeconds(7200));
            addProblem(bobResult, 0, "CRITICAL", "src/Critical.java", 3);
            String slug = workspace.getWorkspaceSlug();

            // A MINOR observation of a MAJOR result is listed: the filter weighs the observation, not its
            // result.
            getOk(OBSERVATIONS + "?agentJobId={id}&severity=MINOR&severity=CRITICAL", slug, run.getId())
                    .jsonPath("$.content[*].path")
                    .isEqualTo(List.of("src/Minor.java", "src/Critical.java"));
            getOk(OBSERVATIONS + "?agentJobId={id}&practiceSlug=test-coverage", slug, run.getId())
                    .jsonPath("$.content[*].path")
                    .isEqualTo(List.of("src/Critical.java"));
            getOk(OBSERVATIONS + "?agentJobId={id}&subjectUserId={user}", slug, run.getId(), alice.getId())
                    .jsonPath("$.content[*].path")
                    .isEqualTo(List.of("src/Major.java", "src/Minor.java"));
            getOk(OBSERVATIONS + "?practiceResultId={id}", slug, bobResult)
                    .jsonPath("$.content[*].path")
                    .isEqualTo(List.of("src/Critical.java"));
            getOk(OBSERVATIONS + "?agentJobId={id}&from={from}", slug, run.getId(), now.minusSeconds(60))
                    .jsonPath("$.content[*].path")
                    .isEqualTo(List.of("src/Major.java", "src/Minor.java"));
        }

        @Test
        @WithAdminUser
        void shouldPageThroughObservationsInOrder() {
            AgentJob run = persistJob(workspace);
            UUID result = insertResult(practiceA, run, alice, "Three", "NOT_MET", "MAJOR", 7L, Instant.now());
            addProblem(result, 0, "MAJOR", "src/A.java", 1);
            addProblem(result, 1, "MINOR", "src/B.java", 2);
            addProblem(result, 2, "MINOR", "src/C.java", 3);

            getOk(OBSERVATIONS + "?agentJobId={id}&page=1&size=2", workspace.getWorkspaceSlug(), run.getId())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(3)
                    .jsonPath("$.page.totalPages")
                    .isEqualTo(2)
                    .jsonPath("$.content[*].path")
                    .isEqualTo(List.of("src/C.java"));
        }

        @Test
        @WithAdminUser
        void shouldListAnObservationWithoutItsSummaryOrPlaceWhenItsResultDetailHidesTheEvidence() {
            // A run of another workspace authorizes no evidence here: the result detail shows no
            // observations.
            UUID hidden = insertProblem(practiceA, otherJob, alice, "Hidden", "MAJOR");
            addProblem(hidden, 0, "MINOR", "src/Hidden.java", 1);

            getOk(RESULTS + "/{id}", workspace.getWorkspaceSlug(), hidden)
                    .jsonPath("$.evidenceHidden")
                    .isEqualTo(true)
                    .jsonPath("$.observations")
                    .isEmpty();
            getOk(OBSERVATIONS + "?practiceResultId={id}", workspace.getWorkspaceSlug(), hidden)
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].practiceResultId")
                    .isEqualTo(hidden.toString())
                    .jsonPath("$.content[0].severity")
                    .isEqualTo("MINOR")
                    .jsonPath("$.content[0].evidenceHidden")
                    .isEqualTo(true)
                    .jsonPath("$.content[0].summary")
                    .doesNotExist()
                    .jsonPath("$.content[0].sourceKind")
                    .doesNotExist()
                    .jsonPath("$.content[0].side")
                    .doesNotExist()
                    .jsonPath("$.content[0].path")
                    .doesNotExist()
                    .jsonPath("$.content[0].startLine")
                    .doesNotExist()
                    .jsonPath("$.content[0].endLine")
                    .doesNotExist();
        }

        @Test
        @WithAdminUser
        void shouldNotListAnotherWorkspacesObservations() {
            UUID theirs = insertProblem(otherPractice, otherJob, bob, "Theirs", "MAJOR");
            assertThat(observationRepository.insert(
                            UUID.randomUUID(),
                            otherWorkspace.getId(),
                            theirs,
                            0,
                            null,
                            "MAJOR",
                            "Their problem",
                            "Not for this workspace.",
                            "[" + diffCitation("src/Theirs.java", 1) + "]",
                            null,
                            null,
                            null))
                    .isOne();

            getOk(OBSERVATIONS + "?practiceResultId={id}", workspace.getWorkspaceSlug(), theirs)
                    .jsonPath("$.content")
                    .isEmpty()
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(0);
        }

        @Test
        @WithUser
        void shouldForbidAWorkspaceMember() {
            get(OBSERVATIONS, workspace.getWorkspaceSlug())
                    .expectStatus()
                    .isForbidden()
                    .expectBody(Void.class);
        }
    }

    @Nested
    @DisplayName("Artifact context")
    class ArtifactContext {

        @Test
        @WithAdminUser
        void usesTheTargetRecordedWhenTheReviewWasSubmitted() {
            long artifactId = 7L;
            job.setIntegrationKind(IntegrationKind.GITHUB);
            job.setMetadata(OBJECT_MAPPER.valueToTree(Map.of(
                    "pull_request_id",
                    artifactId,
                    "pr_number",
                    42,
                    "title",
                    "Make review output visible",
                    "repository_full_name",
                    "review-output-org/review-ui",
                    "pr_url",
                    "https://github.com/review-output-org/review-ui/pull/42")));
            agentJobRepository.save(job);
            UUID practiceResultId = insertResult(
                    practiceA, job, alice, "Resolved artifact", "NOT_MET", "MAJOR", artifactId, Instant.now());
            Feedback feedback = persistUnit(
                    workspace,
                    job,
                    alice,
                    0,
                    FeedbackDeliveryState.DELIVERED,
                    null,
                    "Body",
                    Instant.now(),
                    ArtifactKinds.PULL_REQUEST,
                    artifactId);

            expectResolvedPullRequestArtifact(
                    RESULTS + "?artifactKind=scm.pull_request&artifactId={id}",
                    "$.content[0].reviewedWork",
                    workspace.getWorkspaceSlug(),
                    artifactId);
            expectResolvedPullRequestArtifact(
                    RESULTS + "/{id}", "$.reviewedWork", workspace.getWorkspaceSlug(), practiceResultId);
            expectResolvedPullRequestArtifact(
                    FEEDBACK + "?artifactKind=scm.pull_request&artifactId={id}",
                    "$.content[0].reviewedWork",
                    workspace.getWorkspaceSlug(),
                    artifactId);
            expectResolvedPullRequestArtifact(
                    FEEDBACK + "/{id}", "$.reviewedWork", workspace.getWorkspaceSlug(), feedback.getId());
        }

        @Test
        @WithAdminUser
        void doesNotExposeArtifactsFromAnotherWorkspace() {
            otherJob.setIntegrationKind(IntegrationKind.GITHUB);
            otherJob.setMetadata(OBJECT_MAPPER.valueToTree(Map.of(
                    "pull_request_id",
                    812,
                    "title",
                    "Private target from another workspace",
                    "pr_url",
                    "https://github.com/other-org/private/pull/1")));
            agentJobRepository.save(otherJob);
            UUID practiceResultId = insertResult(
                    practiceA, otherJob, alice, "Foreign artifact reference", "NOT_MET", "MAJOR", 812L, Instant.now());

            getOk(RESULTS + "/{id}", workspace.getWorkspaceSlug(), practiceResultId)
                    .jsonPath("$.reviewedWork.kind")
                    .isEqualTo("scm.pull_request")
                    .jsonPath("$.reviewedWork.id")
                    .isEqualTo("812")
                    .jsonPath("$.reviewedWork.label")
                    .isEqualTo("Pull or merge request")
                    // The other workspace's run named the work; none of what it named may come back here.
                    .jsonPath("$.reviewedWork.title")
                    .doesNotExist()
                    .jsonPath("$.reviewedWork.provider")
                    .doesNotExist()
                    .jsonPath("$.reviewedWork.container")
                    .doesNotExist()
                    .jsonPath("$.reviewedWork.url")
                    .doesNotExist()
                    // The result resolves, but nothing about the other workspace's evidence may come
                    // with it: the citations and their captured content are the payload a leak would
                    // actually expose, and the artifact fields alone never covered them.
                    .jsonPath("$.evidence")
                    .doesNotExist();
        }

        @Test
        @WithAdminUser
        void returnsFallbackForALegacyConversationWithoutTargetMetadata() {
            job.setJobType(AgentJobType.CONVERSATION_REVIEW);
            agentJobRepository.save(job);
            Feedback feedback = persistUnit(
                    workspace,
                    job,
                    alice,
                    0,
                    FeedbackDeliveryState.PREPARED,
                    null,
                    null,
                    Instant.now(),
                    ArtifactKinds.CONVERSATION_THREAD,
                    812L);

            getOk(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), feedback.getId())
                    .jsonPath("$.reviewedWork.kind")
                    .isEqualTo("chat.conversation_thread")
                    .jsonPath("$.reviewedWork.id")
                    .isEqualTo("812")
                    .jsonPath("$.reviewedWork.label")
                    .isEqualTo("Conversation")
                    .jsonPath("$.reviewedWork.provider")
                    .doesNotExist()
                    .jsonPath("$.reviewedWork.url")
                    .doesNotExist();
        }
    }

    @Nested
    @DisplayName("Feedback ledger")
    class Ledger {

        // The group filter keeps the feedback bound to an observation of a practice in the group, and
        // intersects with
        // the practice filter.
        @Test
        @WithAdminUser
        void shouldListTheFeedbackOfTheGroupsPracticesWhenFilteredByGroup() {
            PracticeGroup group = persistGroup(workspace, "communication", "Communication");
            practiceA.setGroup(group);
            practiceRepository.save(practiceA);
            Feedback inGroup = persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "In group");
            bind(inGroup, insertProblem(practiceA, job, alice, "Grouped", "MAJOR"));
            Feedback ungrouped =
                    persistUnit(workspace, job, bob, 1, FeedbackDeliveryState.DELIVERED, null, "Ungrouped");
            bind(ungrouped, insertProblem(practiceB, job, bob, "Not grouped", "MAJOR"));
            persistUnit(workspace, job, alice, 2, FeedbackDeliveryState.DELIVERED, null, "Unbound");

            getOk(FEEDBACK + "?groupSlug=communication", workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].id")
                    .isEqualTo(inGroup.getId().toString());
            expectFeedbackTotal("?groupSlug=communication&practiceSlug=" + practiceB.getSlug(), 0);
            expectFeedbackTotal("?groupSlug=unknown", 0);
            expectFeedbackTotal("", 3);
        }

        /** Only the feedback written from the observation, and none from another workspace's binding to it. */
        @Test
        @WithAdminUser
        void shouldListTheFeedbackWrittenFromOneResultWhenFilteredByIt() {
            UUID observed = insertProblem(practiceA, job, alice, "Recorded", "MAJOR");
            UUID sibling = insertProblem(practiceB, job, alice, "Sibling", "MAJOR");
            Feedback fromIt = persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "From it");
            bind(fromIt, observed);
            bind(persistUnit(workspace, job, alice, 1, FeedbackDeliveryState.DELIVERED, null, "Sibling"), sibling);
            bind(
                    persistUnit(otherWorkspace, otherJob, bob, 0, FeedbackDeliveryState.DELIVERED, null, "Foreign"),
                    observed);
            UUID foreignResult =
                    insertResult(otherPractice, otherJob, bob, "Foreign", "NOT_MET", "MAJOR", 7L, Instant.now());
            bind(
                    persistUnit(workspace, job, alice, 2, FeedbackDeliveryState.DELIVERED, null, "Cross-bound"),
                    foreignResult);

            getOk(FEEDBACK + "?practiceResultId=" + observed, workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].id")
                    .isEqualTo(fromIt.getId().toString());
            expectFeedbackTotal("?practiceResultId=" + foreignResult, 0);
            expectFeedbackTotal("", 3);
        }

        /** Each overview feedback count opens the feedback list, filtered to its one delivery state, on the same rows. */
        @Test
        @WithAdminUser
        void shouldListAsMuchFeedbackAsTheOverviewCountsWhenFilteredToTheSameStateAndRange() {
            Instant inside = Instant.parse("2026-02-03T12:00:00Z");
            persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.PARTIALLY_DELIVERED, null, "Half", inside);
            persistUnit(
                    workspace,
                    job,
                    alice,
                    1,
                    FeedbackDeliveryState.PARTIALLY_DELIVERED,
                    FeedbackSuppressionReason.VOLUME_CAPPED,
                    "Half, rest suppressed",
                    inside);
            persistUnit(workspace, job, alice, 2, FeedbackDeliveryState.PARTIALLY_FAILED, null, "Half failed", inside);
            persistUnit(workspace, job, alice, 3, FeedbackDeliveryState.PREPARED, null, "Ready", inside);
            // On the exclusive upper bound and just before the lower one: counted by neither.
            persistUnit(
                    workspace,
                    job,
                    alice,
                    4,
                    FeedbackDeliveryState.PARTIALLY_DELIVERED,
                    null,
                    "Late",
                    Instant.parse("2026-02-08T00:00:00Z"));
            persistUnit(
                    workspace,
                    job,
                    alice,
                    5,
                    FeedbackDeliveryState.PARTIALLY_FAILED,
                    null,
                    "Early",
                    Instant.parse("2026-01-31T23:59:59Z"));
            String range = "from=2026-02-01T00:00:00Z&to=2026-02-08T00:00:00Z";

            getOk("/workspaces/{slug}/practices/reviews/overview?" + range, workspace.getWorkspaceSlug())
                    .jsonPath("$.feedback.partiallyDelivered")
                    .isEqualTo(2)
                    .jsonPath("$.feedback.partiallyFailed")
                    .isEqualTo(1)
                    .jsonPath("$.feedback.prepared")
                    .isEqualTo(1)
                    .jsonPath("$.feedback.suppressed")
                    .isEqualTo(0)
                    .jsonPath("$.feedback.failed")
                    .isEqualTo(0);
            expectFeedbackTotal("?deliveryState=PARTIALLY_DELIVERED&" + range, 2);
            expectFeedbackTotal("?deliveryState=PARTIALLY_FAILED&" + range, 1);
            expectFeedbackTotal("?deliveryState=PREPARED&" + range, 1);
            expectFeedbackTotal("?deliveryState=SUPPRESSED&" + range, 0);
            expectFeedbackTotal("?deliveryState=FAILED&" + range, 0);
        }

        /**
         * A practice's overview feedback counts open the feedback list filtered to that practice, the same range and
         * one delivery state, on the same rows.
         */
        @Test
        @WithAdminUser
        void shouldListAsMuchFeedbackAsEachPracticeCountsWhenFilteredToThePracticeStateAndRange() {
            Instant inside = Instant.parse("2026-02-03T12:00:00Z");
            UUID firstA = insertProblem(practiceA, job, alice, "First A", "MAJOR");
            UUID secondA = insertProblem(practiceA, job, alice, "Second A", "MAJOR");
            UUID onlyB = insertProblem(practiceB, job, bob, "Only B", "MAJOR");
            Feedback fusedA =
                    persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.PARTIALLY_DELIVERED, null, "A", inside);
            bind(fusedA, firstA);
            bind(fusedA, secondA);
            Feedback acrossBoth =
                    persistUnit(workspace, job, alice, 1, FeedbackDeliveryState.DELIVERED, null, "A and B", inside);
            bind(acrossBoth, firstA);
            bind(acrossBoth, onlyB);
            bind(persistUnit(workspace, job, bob, 2, FeedbackDeliveryState.PREPARED, null, "B", inside), onlyB);
            persistUnit(workspace, job, bob, 3, FeedbackDeliveryState.SUPPRESSED, null, "Unbound", inside);
            bind(
                    persistUnit(
                            workspace,
                            job,
                            alice,
                            4,
                            FeedbackDeliveryState.DELIVERED,
                            null,
                            "Late",
                            Instant.parse("2026-02-08T00:00:00Z")),
                    firstA);
            String range = "from=2026-02-01T00:00:00Z&to=2026-02-08T00:00:00Z";

            JsonNode practices = OBJECT_MAPPER
                    .readTree(Objects.requireNonNull(getOk(
                                    "/workspaces/{slug}/practices/reviews/overview?" + range,
                                    workspace.getWorkspaceSlug())
                            .returnResult()
                            .getResponseBody()))
                    .get("practices");
            Map<String, JsonNode> feedbackBySlug = new HashMap<>();
            practices.forEach(
                    practice -> feedbackBySlug.put(practice.get("practiceSlug").asString(), practice.get("feedback")));
            JsonNode countsA = Objects.requireNonNull(feedbackBySlug.get(practiceA.getSlug()));
            JsonNode countsB = Objects.requireNonNull(feedbackBySlug.get(practiceB.getSlug()));
            assertThat(countsA.path("partiallyDelivered").asLong()).isEqualTo(1);
            assertThat(countsA.path("delivered").asLong()).isEqualTo(1);
            assertThat(countsB.path("delivered").asLong()).isEqualTo(1);
            assertThat(countsB.path("prepared").asLong()).isEqualTo(1);
            feedbackBySlug.forEach((slug, counts) -> {
                for (FeedbackDeliveryState state : FeedbackDeliveryState.values()) {
                    expectFeedbackTotal(
                            "?practiceSlug=" + slug + "&deliveryState=" + state + "&" + range,
                            counts.path(camelCase(state.name())).asInt());
                }
            });
        }

        @Test
        @WithAdminUser
        void shouldListFeedbackOldestFirstWhenSortedOldest() {
            Instant at = Instant.parse("2026-02-03T12:00:00Z");
            Feedback middle = persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.PREPARED, null, "Middle", at);
            Feedback oldest = persistUnit(
                    workspace, job, alice, 1, FeedbackDeliveryState.PREPARED, null, "Oldest", at.minusSeconds(60));
            Feedback newest = persistUnit(
                    workspace, job, alice, 2, FeedbackDeliveryState.PREPARED, null, "Newest", at.plusSeconds(60));

            getOk(FEEDBACK, workspace.getWorkspaceSlug())
                    .jsonPath("$.content[*].id")
                    .isEqualTo(List.of(
                            newest.getId().toString(),
                            middle.getId().toString(),
                            oldest.getId().toString()));
            getOk(FEEDBACK + "?sort=OLDEST", workspace.getWorkspaceSlug())
                    .jsonPath("$.content[*].id")
                    .isEqualTo(List.of(
                            oldest.getId().toString(),
                            middle.getId().toString(),
                            newest.getId().toString()));
        }

        private static String camelCase(String constant) {
            String[] words = constant.toLowerCase(Locale.ROOT).split("_", -1);
            StringBuilder result = new StringBuilder(words[0]);
            for (int i = 1; i < words.length; i++) {
                result.append(words[i].substring(0, 1).toUpperCase(Locale.ROOT)).append(words[i].substring(1));
            }
            return result.toString();
        }

        private void expectFeedbackTotal(String query, int total) {
            getOk(FEEDBACK + query, workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(total);
        }

        @Test
        @WithAdminUser
        void listsEveryStateNotJustDelivered() {
            persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "Delivered body");
            persistUnit(workspace, job, alice, 1, FeedbackDeliveryState.SUPERSEDED, null, "Superseded body");
            persistUnit(workspace, job, alice, 3000, FeedbackDeliveryState.PREPARED, null, null);
            persistUnit(
                    workspace,
                    job,
                    bob,
                    5000,
                    FeedbackDeliveryState.SUPPRESSED,
                    FeedbackSuppressionReason.ARTIFACT_MERGED,
                    "Withheld body");
            persistUnit(workspace, job, bob, 4000, FeedbackDeliveryState.FAILED, null, "Failed body");

            getOk(FEEDBACK, workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(5)
                    .jsonPath("$.content[?(@.deliveryState == 'DELIVERED')]")
                    .isNotEmpty()
                    .jsonPath("$.content[?(@.deliveryState == 'SUPERSEDED')]")
                    .isNotEmpty()
                    .jsonPath("$.content[?(@.deliveryState == 'PREPARED')]")
                    .isNotEmpty()
                    .jsonPath("$.content[?(@.deliveryState == 'SUPPRESSED')]")
                    .isNotEmpty()
                    .jsonPath("$.content[?(@.deliveryState == 'FAILED')]")
                    .isNotEmpty();
        }

        @Test
        @WithAdminUser
        void excludesOtherWorkspaces() {
            persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "Mine");
            persistUnit(otherWorkspace, otherJob, bob, 0, FeedbackDeliveryState.DELIVERED, null, "Theirs");

            getOk(FEEDBACK, workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].bodyPreview")
                    .isEqualTo("Mine");
        }

        @Test
        @WithAdminUser
        void detailOfAnotherWorkspaceIsNotFound() {
            Feedback theirs =
                    persistUnit(otherWorkspace, otherJob, bob, 0, FeedbackDeliveryState.DELIVERED, null, "Theirs");

            get(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), theirs.getId())
                    .expectStatus()
                    .isNotFound()
                    .expectBody(Void.class);
        }

        @Test
        @WithAdminUser
        void detailIncludesOnlyThePolicyTraceForThatFeedback() {
            Feedback unit = persistUnit(
                    workspace,
                    job,
                    alice,
                    0,
                    FeedbackDeliveryState.SUPPRESSED,
                    FeedbackSuppressionReason.RECIPIENT_OPTED_OUT,
                    "Withheld");
            deliveryPolicyEvaluationRepository.save(
                    policyEvaluation(job, unit.getId(), DeliveryPolicySurface.ARTIFACT));
            deliveryPolicyEvaluationRepository.save(policyEvaluation(job, null, DeliveryPolicySurface.CONVERSATION));

            getOk(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), unit.getId())
                    .jsonPath("$.deliveryPolicy.length()")
                    .isEqualTo(1)
                    .jsonPath("$.deliveryPolicy[0].reviewId")
                    .isEqualTo(job.getId().toString())
                    .jsonPath("$.deliveryPolicy[0].surface")
                    .isEqualTo("ARTIFACT")
                    .jsonPath("$.deliveryPolicy[0].decisiveReason")
                    .isEqualTo("RECIPIENT_OPTED_OUT")
                    .jsonPath("$.deliveryPolicy[0].checks[0].check")
                    .isEqualTo("RECIPIENT_CONSENT")
                    .jsonPath("$.deliveryPolicy[0].checks[0].status")
                    .isEqualTo("DENIED");
        }

        @Test
        @WithAdminUser
        void listsTheChecksAReviewRanForItselfUnderTheReview() {
            Feedback unit = persistUnit(
                    workspace,
                    job,
                    alice,
                    0,
                    FeedbackDeliveryState.SUPPRESSED,
                    FeedbackSuppressionReason.RECIPIENT_OPTED_OUT,
                    "Withheld");
            deliveryPolicyEvaluationRepository.save(
                    policyEvaluation(job, unit.getId(), DeliveryPolicySurface.ARTIFACT));
            deliveryPolicyEvaluationRepository.save(policyEvaluation(job, null, DeliveryPolicySurface.CONVERSATION));

            getOk(
                            "/workspaces/{slug}/practices/reviews/runs/{id}/delivery-checks",
                            workspace.getWorkspaceSlug(),
                            job.getId())
                    .jsonPath("$.length()")
                    .isEqualTo(1)
                    .jsonPath("$[0].surface")
                    .isEqualTo("CONVERSATION");
            getOk(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), unit.getId())
                    .jsonPath("$.deliveryPolicy.length()")
                    .isEqualTo(1)
                    .jsonPath("$.deliveryPolicy[0].surface")
                    .isEqualTo("ARTIFACT");
        }

        @Test
        @WithAdminUser
        void filtersByStateReasonAndRun() {
            persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "Delivered");
            persistUnit(
                    workspace,
                    job,
                    alice,
                    2000,
                    FeedbackDeliveryState.SUPPRESSED,
                    FeedbackSuppressionReason.VOLUME_CAPPED,
                    null);
            persistUnit(
                    workspace,
                    job,
                    alice,
                    2001,
                    FeedbackDeliveryState.SUPPRESSED,
                    FeedbackSuppressionReason.COMPOSER_DEDUPED,
                    null);
            AgentJob second = persistJob(workspace);
            persistUnit(workspace, second, bob, 0, FeedbackDeliveryState.DELIVERED, null, "Other run");

            getOk(FEEDBACK + "?deliveryState=SUPPRESSED", workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(2);

            getOk(FEEDBACK + "?suppressionReason=VOLUME_CAPPED", workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1);

            getOk(FEEDBACK + "?agentJobId={id}", workspace.getWorkspaceSlug(), second.getId())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].bodyPreview")
                    .isEqualTo("Other run");
        }

        /**
         * The overview's withheld feedback by reason opens the list filtered to the same state, reasons and range, so
         * each count by reason must match the list's total under that filter, and only the range's, workspace's
         * withheld rows count.
         */
        @Test
        @WithAdminUser
        void shouldCountWithheldFeedbackByReasonAsTheListFiltersIt() {
            Instant inside = Instant.parse("2026-02-03T12:00:00Z");
            FeedbackSuppressionReason capped = FeedbackSuppressionReason.VOLUME_CAPPED;
            FeedbackSuppressionReason deduped = FeedbackSuppressionReason.COMPOSER_DEDUPED;
            persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.SUPPRESSED, capped, null, inside);
            persistUnit(workspace, job, alice, 1, FeedbackDeliveryState.SUPPRESSED, capped, null, inside);
            persistUnit(workspace, job, alice, 2, FeedbackDeliveryState.SUPPRESSED, deduped, null, inside);
            persistUnit(workspace, job, alice, 3, FeedbackDeliveryState.DELIVERED, null, "Delivered", inside);
            // Withheld without a recorded reason: one count with no reason, in the same read.
            persistUnit(workspace, job, alice, 6, FeedbackDeliveryState.SUPPRESSED, null, null, inside);
            // Part of it was delivered: it carries a reason, but it is not withheld.
            persistUnit(workspace, job, alice, 4, FeedbackDeliveryState.PARTIALLY_DELIVERED, capped, "Half", inside);
            // Outside the range and in another workspace: counted by neither.
            persistUnit(
                    workspace,
                    job,
                    alice,
                    5,
                    FeedbackDeliveryState.SUPPRESSED,
                    deduped,
                    null,
                    Instant.parse("2026-02-08T00:00:00Z"));
            persistUnit(otherWorkspace, otherJob, bob, 0, FeedbackDeliveryState.SUPPRESSED, capped, null, inside);
            String filter = "deliveryState=SUPPRESSED&from=2026-02-01T00:00:00Z&to=2026-02-08T00:00:00Z";

            getOk(FEEDBACK + "/reasons?" + filter, workspace.getWorkspaceSlug())
                    .jsonPath("$.length()")
                    .isEqualTo(3)
                    .jsonPath("$[2].reason")
                    .doesNotExist()
                    .jsonPath("$[2].count")
                    .isEqualTo(1)
                    .jsonPath("$[?(@.reason == 'VOLUME_CAPPED')].count")
                    .isEqualTo(List.of(2))
                    .jsonPath("$[?(@.reason == 'COMPOSER_DEDUPED')].count")
                    .isEqualTo(List.of(1))
                    .jsonPath("$[0].bodyPreview")
                    .doesNotExist();
            expectFeedbackTotal("?suppressionReason=VOLUME_CAPPED&" + filter, 2);
            expectFeedbackTotal("?suppressionReason=COMPOSER_DEDUPED&" + filter, 1);
        }

        @Test
        @WithAdminUser
        void rejectsAnInvalidFilterOnTheCountsByReason() {
            get(FEEDBACK + "/reasons?from=2026-01-20T00:00:00Z&to=2026-01-10T00:00:00Z", workspace.getWorkspaceSlug())
                    .expectStatus()
                    .isBadRequest();
        }

        @Test
        @WithAdminUser
        void filtersByChannel() {
            persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "In context");
            feedbackRepository.save(Feedback.builder()
                    .agentJobId(job.getId())
                    .workspaceId(workspace.getId())
                    .recipientUserId(alice.getId())
                    .aboutUserId(alice.getId())
                    .channel(FeedbackChannel.IN_CHAT)
                    .position(3000)
                    .deliveryState(FeedbackDeliveryState.PREPARED)
                    .source(FeedbackSource.AGENT)
                    .createdAt(Instant.now())
                    .build());

            getOk(FEEDBACK + "?channel=IN_CHAT", workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].deliveryState")
                    .isEqualTo("PREPARED")
                    .jsonPath("$.content[0].bodyPreview")
                    .doesNotExist();
        }

        @Test
        @WithAdminUser
        void filtersByArtifact() {
            persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "Pull request");
            feedbackRepository.save(Feedback.builder()
                    .agentJobId(job.getId())
                    .workspaceId(workspace.getId())
                    .artifactKind(ArtifactKinds.ISSUE)
                    .artifactId(99L)
                    .recipientUserId(alice.getId())
                    .aboutUserId(alice.getId())
                    .channel(FeedbackChannel.IN_CONTEXT)
                    .position(1)
                    .deliveryState(FeedbackDeliveryState.DELIVERED)
                    .body("Issue")
                    .source(FeedbackSource.AGENT)
                    .createdAt(Instant.now())
                    .deliveredAt(Instant.now())
                    .build());
            feedbackRepository.save(Feedback.builder()
                    .agentJobId(job.getId())
                    .workspaceId(workspace.getId())
                    .artifactKind(ArtifactKinds.ISSUE)
                    .artifactId(100L)
                    .recipientUserId(alice.getId())
                    .aboutUserId(alice.getId())
                    .channel(FeedbackChannel.IN_CONTEXT)
                    .position(2)
                    .deliveryState(FeedbackDeliveryState.DELIVERED)
                    .body("Other issue")
                    .source(FeedbackSource.AGENT)
                    .createdAt(Instant.now())
                    .deliveredAt(Instant.now())
                    .build());

            getOk(FEEDBACK + "?artifactKind=scm.issue", workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(2);

            getOk(FEEDBACK + "?artifactKind=scm.issue&artifactId=99", workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].bodyPreview")
                    .isEqualTo("Issue");

            get(FEEDBACK + "?artifactId=99", workspace.getWorkspaceSlug())
                    .expectStatus()
                    .isBadRequest()
                    .expectBody(Void.class);
        }

        @Test
        @WithAdminUser
        void filtersByRecipientAndCreatedAtWindow() {
            Instant from = Instant.parse("2026-01-10T00:00:00Z");
            Instant to = Instant.parse("2026-01-20T00:00:00Z");
            persistUnit(
                    workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "Before", from.minusSeconds(1));
            persistUnit(workspace, job, alice, 1, FeedbackDeliveryState.DELIVERED, null, "Inside", from);
            persistUnit(workspace, job, bob, 2, FeedbackDeliveryState.DELIVERED, null, "Other recipient", from);
            persistUnit(workspace, job, alice, 3, FeedbackDeliveryState.DELIVERED, null, "At end", to);

            getOk(
                            FEEDBACK + "?recipientUserId={recipient}&from={from}&to={to}",
                            workspace.getWorkspaceSlug(),
                            alice.getId(),
                            from,
                            to)
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].bodyPreview")
                    .isEqualTo("Inside");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "?from=2026-01-20T00:00:00Z&to=2026-01-10T00:00:00Z",
                    "?recipientUserId=-1",
                    "?size=100000",
                    "?page=-1",
                })
        @WithAdminUser
        void rejectsInvalidFeedbackFiltersAndPagination(String query) {
            get(FEEDBACK + query, workspace.getWorkspaceSlug())
                    .expectStatus()
                    .isBadRequest()
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody()
                    .jsonPath("$.status")
                    .isEqualTo(400);
        }

        @Test
        @WithAdminUser
        void truncatesBodyOnTheListOnly() {
            @Nullable String body = "x".repeat(FeedbackRepository.BODY_PREVIEW_LENGTH + 200);
            Feedback unit = persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, body);

            getOk(FEEDBACK, workspace.getWorkspaceSlug())
                    .jsonPath("$.content[0].bodyTruncated")
                    .isEqualTo(true)
                    .jsonPath("$.content[0].bodyPreview")
                    .isEqualTo(body.substring(0, FeedbackRepository.BODY_PREVIEW_LENGTH));

            getOk(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), unit.getId())
                    .jsonPath("$.body")
                    .isEqualTo(body);
        }

        /**
         * In a course deployment the workspace admin is the instructor, and an in-app body is the only
         * feedback text whose audience is the developer alone. Run against the real projection, because the
         * withholding lives in the SQL: a mapper-level assertion would pass on a query that selected the body.
         */
        @Test
        @WithAdminUser
        @DisplayName("an operator read cannot return an in-app body, on either route")
        void withholdsAnInAppBodyFromEveryOperatorRoute() {
            String inAppBody = "### You keep shipping untested changes\n\n"
                    + "y".repeat(FeedbackRepository.BODY_PREVIEW_LENGTH + 200);
            Feedback inApp = feedbackRepository.save(Feedback.builder()
                    .agentJobId(job.getId())
                    .workspaceId(workspace.getId())
                    .recipientUserId(alice.getId())
                    .aboutUserId(alice.getId())
                    .channel(FeedbackChannel.IN_APP)
                    .position(7000)
                    .deliveryState(FeedbackDeliveryState.DELIVERED)
                    .body(inAppBody)
                    .source(FeedbackSource.AGENT)
                    .createdAt(Instant.now())
                    .deliveredAt(Instant.now())
                    .build());
            String inContextBody = "z".repeat(FeedbackRepository.BODY_PREVIEW_LENGTH + 200);
            persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, inContextBody);

            getOk(FEEDBACK + "?channel=IN_APP", workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    // Everything needed to audit the pipeline still travels — only the words do not.
                    .jsonPath("$.content[0].deliveryState")
                    .isEqualTo("DELIVERED")
                    .jsonPath("$.content[0].recipient.login")
                    .isEqualTo("alice")
                    .jsonPath("$.content[0].bodyPreview")
                    .doesNotExist()
                    .jsonPath("$.content[0].bodyTruncated")
                    .isEqualTo(false);

            getOk(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), inApp.getId())
                    .jsonPath("$.channel")
                    .isEqualTo("IN_APP")
                    .jsonPath("$.body")
                    .doesNotExist();

            // The positive control: the same projection on a lane the developer can already be read on
            // still previews and still flags truncation, so the assertions above are about the channel.
            getOk(FEEDBACK + "?channel=IN_CONTEXT", workspace.getWorkspaceSlug())
                    .jsonPath("$.content[0].bodyPreview")
                    .isEqualTo(inContextBody.substring(0, FeedbackRepository.BODY_PREVIEW_LENGTH))
                    .jsonPath("$.content[0].bodyTruncated")
                    .isEqualTo(true);
        }

        @Test
        @WithAdminUser
        void detailOfAWithheldUnitCarriesTheComposedBody() {
            PracticeGroup group = persistGroup(workspace, "communication", "Communication");
            practiceA.setGroup(group);
            practiceRepository.save(practiceA);
            UUID practiceResultId = insertProblem(practiceA, job, alice, "Would have flagged", "MAJOR");
            Feedback unit = persistUnit(
                    workspace,
                    job,
                    alice,
                    5000,
                    FeedbackDeliveryState.SUPPRESSED,
                    FeedbackSuppressionReason.ARTIFACT_CLOSED,
                    "## What I would have posted\n\nSomething useful.");
            bind(unit, practiceResultId);

            getOk(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), unit.getId())
                    .jsonPath("$.deliveryState")
                    .isEqualTo("SUPPRESSED")
                    .jsonPath("$.suppressionReason")
                    .isEqualTo("ARTIFACT_CLOSED")
                    .jsonPath("$.body")
                    .isEqualTo("## What I would have posted\n\nSomething useful.")
                    .jsonPath("$.deliveredAt")
                    .doesNotExist()
                    .jsonPath("$.placements.length()")
                    .isEqualTo(0)
                    .jsonPath("$.practiceResults.length()")
                    .isEqualTo(1)
                    .jsonPath("$.practiceResults[0].summary")
                    .isEqualTo("Would have flagged")
                    .jsonPath("$.practiceResults[0].practiceSlug")
                    .isEqualTo("pr-description-quality")
                    .jsonPath("$.practiceResults[0].group.slug")
                    .isEqualTo("communication")
                    .jsonPath("$.recipient.login")
                    .isEqualTo("alice");
        }

        @Test
        @WithAdminUser
        void detailReturnsResultsInRenderOrder() {
            Feedback unit = persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "Body");
            UUID second = insertProblem(practiceA, job, alice, "Second", "MINOR");
            UUID first = insertProblem(practiceA, job, alice, "First", "CRITICAL");
            feedbackEvidenceRepository.insertIfAbsent(unit.getId(), second, null, "PRIMARY", 1);
            feedbackEvidenceRepository.insertIfAbsent(unit.getId(), first, null, "PRIMARY", 0);

            getOk(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), unit.getId())
                    .jsonPath("$.practiceResults[0].summary")
                    .isEqualTo("First")
                    .jsonPath("$.practiceResults[1].summary")
                    .isEqualTo("Second");
        }

        @Test
        @WithAdminUser
        void excludesCrossWorkspaceResultBindings() {
            Feedback unit = persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "Body");
            UUID foreignResult = insertProblem(otherPractice, otherJob, bob, "Foreign", "MAJOR");
            bind(unit, foreignResult);

            getOk(FEEDBACK, workspace.getWorkspaceSlug())
                    .jsonPath("$.content[0].practiceResultCount")
                    .isEqualTo(0);

            getOk(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), unit.getId())
                    .jsonPath("$.practiceResults.length()")
                    .isEqualTo(0);
        }

        @Test
        @WithAdminUser
        void returnsPlacementsInDisplayOrderWithTheirLocators() {
            Feedback unit = persistUnit(workspace, job, alice, 0, FeedbackDeliveryState.DELIVERED, null, "Body");
            Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
            UUID chatMessageId = UUID.randomUUID();
            feedbackPlacementRepository.save(FeedbackPlacement.builder()
                    .feedback(unit)
                    .placementType(PlacementType.INLINE)
                    .anchorKind(PlacementAnchorKind.LINE)
                    .anchorPath("src/B.java")
                    .anchorStartLine(20)
                    .anchorEndLine(20)
                    .anchorSide(PlacementAnchorSide.NEW)
                    .postedCommentRef("note-b")
                    .createdAt(createdAt)
                    .build());
            feedbackPlacementRepository.save(FeedbackPlacement.builder()
                    .feedback(unit)
                    .placementType(PlacementType.INLINE)
                    .anchorKind(PlacementAnchorKind.LINE)
                    .anchorPath("src/A.java")
                    .anchorStartLine(10)
                    .anchorEndLine(10)
                    .anchorSide(PlacementAnchorSide.NEW)
                    .postedCommentRef("note-a")
                    .createdAt(createdAt)
                    .build());
            feedbackPlacementRepository.save(FeedbackPlacement.builder()
                    .feedback(unit)
                    .placementType(PlacementType.SUMMARY)
                    .postedCommentRef("comment-1")
                    .postedCommentUrl("https://github.com/acme/repo/pull/1#issuecomment-1")
                    .createdAt(createdAt.plusSeconds(2))
                    .build());
            feedbackPlacementRepository.save(FeedbackPlacement.builder()
                    .feedback(unit)
                    .placementType(PlacementType.CONVERSATION_TURN)
                    .chatMessageId(chatMessageId)
                    .createdAt(createdAt.plusSeconds(1))
                    .build());

            getOk(FEEDBACK + "/{id}", workspace.getWorkspaceSlug(), unit.getId())
                    .jsonPath("$.placements[0].placementType")
                    .isEqualTo("SUMMARY")
                    .jsonPath("$.placements[0].postedCommentUrl")
                    .isEqualTo("https://github.com/acme/repo/pull/1#issuecomment-1")
                    .jsonPath("$.placements[1].anchorPath")
                    .isEqualTo("src/A.java")
                    .jsonPath("$.placements[2].anchorPath")
                    .isEqualTo("src/B.java")
                    .jsonPath("$.placements[3].placementType")
                    .isEqualTo("CONVERSATION_TURN")
                    .jsonPath("$.placements[3].chatMessageId")
                    .isEqualTo(chatMessageId.toString());
        }

        @Test
        @WithAdminUser
        void pagesConsistentlyUnderAFilter() {
            for (int i = 0; i < 3; i++) {
                persistUnit(workspace, job, alice, i, FeedbackDeliveryState.DELIVERED, null, "Delivered " + i);
            }
            persistUnit(
                    workspace,
                    job,
                    bob,
                    2000,
                    FeedbackDeliveryState.SUPPRESSED,
                    FeedbackSuppressionReason.VOLUME_CAPPED,
                    null);

            getOk(FEEDBACK + "?deliveryState=DELIVERED&size=2&page=1", workspace.getWorkspaceSlug())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(3)
                    .jsonPath("$.content.length()")
                    .isEqualTo(1)
                    .jsonPath("$.page.number")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].deliveryState")
                    .isEqualTo("DELIVERED");
        }
    }

    @Nested
    @DisplayName("Result validity")
    class ResultValidity {

        @Test
        @WithAdminUser
        void shouldInvalidateOnlyTheSelectedResultWithItsActorAndReason() {
            UUID wrong = insertProblem(practiceA, job, alice, "Closed issue #1 already", "MINOR");
            UUID sibling = insertProblem(practiceB, job, alice, "Sibling claim", "MINOR");

            patchValidity(workspace, wrong, false, "  Issue #1 was still open  ")
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.summary")
                    .isEqualTo("Closed issue #1 already")
                    .jsonPath("$.invalidations.length()")
                    .isEqualTo(1)
                    .jsonPath("$.invalidations[0].reason")
                    .isEqualTo("Issue #1 was still open")
                    .jsonPath("$.invalidations[0].invalidatedBy")
                    .isEqualTo("admin")
                    .jsonPath("$.invalidations[0].restoredAt")
                    .doesNotExist();

            PracticeResultInvalidation recorded =
                    invalidationRepository.findActive(workspace.getId(), wrong).orElseThrow();
            assertThat(recorded.getInvalidatedByAccountId()).isNotNull();
            assertThat(invalidationRepository.findHistory(workspace.getId(), sibling))
                    .isEmpty();
            assertThat(practiceResultRepository
                            .findByIdAndWorkspaceId(wrong, workspace.getId())
                            .orElseThrow()
                            .getSummary())
                    .isEqualTo("Closed issue #1 already");
            getOk(RESULTS + "?agentJobId=" + job.getId(), workspace.getWorkspaceSlug())
                    .jsonPath("$.content[?(@.id == '%s')].invalidatedAt".formatted(wrong))
                    .isNotEmpty()
                    .jsonPath("$.content[?(@.id == '%s')].invalidatedAt".formatted(sibling))
                    .doesNotExist();
        }

        @Test
        @WithAdminUser
        void shouldStopFeedbackNobodyHasReceivedAndKeepTheDeliveredRecord() {
            UUID wrong = insertProblem(practiceA, job, alice, "Wrong claim", "MINOR");
            UUID other = insertProblem(practiceB, job, alice, "Other claim", "MINOR");
            Feedback proposal =
                    persistUnit(workspace, job, alice, 1, FeedbackDeliveryState.AWAITING_APPROVAL, null, "Proposal");
            Feedback prepared = persistUnit(workspace, job, alice, 2, FeedbackDeliveryState.PREPARED, null, "Prepared");
            Feedback delivered =
                    persistUnit(workspace, job, alice, 3, FeedbackDeliveryState.DELIVERED, null, "Delivered");
            Feedback unrelated =
                    persistUnit(workspace, job, alice, 4, FeedbackDeliveryState.PREPARED, null, "Unrelated");
            bind(proposal, wrong);
            bind(prepared, wrong);
            bind(delivered, wrong);
            bind(unrelated, other);

            patchValidity(workspace, wrong, false, "Wrong when made")
                    .expectStatus()
                    .isOk()
                    .expectBody(Void.class);

            for (Feedback stopped : List.of(proposal, prepared)) {
                Feedback reread = feedbackRepository
                        .findByIdAndWorkspaceId(stopped.getId(), workspace.getId())
                        .orElseThrow();
                assertThat(reread.getDeliveryState()).isEqualTo(FeedbackDeliveryState.SUPPRESSED);
                assertThat(reread.getSuppressionReason()).isEqualTo(FeedbackSuppressionReason.OBSERVATION_INVALIDATED);
            }
            assertThat(feedbackRepository
                            .findByIdAndWorkspaceId(delivered.getId(), workspace.getId())
                            .orElseThrow()
                            .getDeliveryState())
                    .isEqualTo(FeedbackDeliveryState.DELIVERED);
            assertThat(feedbackRepository
                            .findByIdAndWorkspaceId(unrelated.getId(), workspace.getId())
                            .orElseThrow()
                            .getDeliveryState())
                    .isEqualTo(FeedbackDeliveryState.PREPARED);
        }

        @Test
        @WithAdminUser
        void shouldRefuseRepeatedTransitionsAndKeepEveryCorrectionInTheHistory() {
            UUID practiceResult = insertProblem(practiceA, job, alice, "Contested claim", "MINOR");

            patchValidity(workspace, practiceResult, false, "First look")
                    .expectStatus()
                    .isOk()
                    .expectBody(Void.class);
            patchValidity(workspace, practiceResult, false, "Again")
                    .expectStatus()
                    .isEqualTo(409)
                    .expectBody(Void.class);
            patchValidity(workspace, practiceResult, true, "The claim was right after all")
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.invalidations[0].restorationReason")
                    .isEqualTo("The claim was right after all")
                    .jsonPath("$.invalidations[0].restoredBy")
                    .isEqualTo("admin");
            patchValidity(workspace, practiceResult, true, "Again")
                    .expectStatus()
                    .isEqualTo(409)
                    .expectBody(Void.class);
            patchValidity(workspace, practiceResult, false, "Second look")
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.invalidations.length()")
                    .isEqualTo(2)
                    .jsonPath("$.invalidations[0].reason")
                    .isEqualTo("Second look")
                    .jsonPath("$.invalidations[1].reason")
                    .isEqualTo("First look");
        }

        @Test
        @WithAdminUser
        void shouldNotFindOrChangeAnotherWorkspacesResult() {
            UUID foreign = insertProblem(otherPractice, otherJob, alice, "Other tenant claim", "MINOR");

            patchValidity(workspace, foreign, false, "Not yours")
                    .expectStatus()
                    .isNotFound()
                    .expectBody(Void.class);

            assertThat(invalidationRepository.findHistory(otherWorkspace.getId(), foreign))
                    .isEmpty();
        }

        @Test
        @WithAdminUser
        void shouldRequireAReason() {
            UUID practiceResult = insertProblem(practiceA, job, alice, "Claim", "MINOR");

            patchValidity(workspace, practiceResult, false, "   ")
                    .expectStatus()
                    .isBadRequest()
                    .expectBody(Void.class);

            assertThat(invalidationRepository.findHistory(workspace.getId(), practiceResult))
                    .isEmpty();
        }

        @Test
        @WithUser
        void shouldForbidAWorkspaceMember() {
            UUID practiceResult = insertProblem(practiceA, job, alice, "Claim", "MINOR");

            patchValidity(workspace, practiceResult, false, "Members cannot correct")
                    .expectStatus()
                    .isForbidden()
                    .expectBody(Void.class);
        }
    }

    @Nested
    @DisplayName("Feedback location")
    class Locations {

        private static final String LOCATIONS = FEEDBACK + "/locations";

        private int positions = 0;

        private Feedback unit(
                Workspace ws, AgentJob agentJob, User recipient, FeedbackChannel channel, FeedbackDeliveryState state) {
            return feedbackRepository.save(Feedback.builder()
                    .agentJobId(agentJob.getId())
                    .workspaceId(ws.getId())
                    .artifactKind(ArtifactKinds.PULL_REQUEST)
                    .artifactId(7L)
                    .recipientUserId(recipient.getId())
                    .aboutUserId(recipient.getId())
                    .channel(channel)
                    .position(positions++)
                    .deliveryState(state)
                    .source(FeedbackSource.AGENT)
                    .createdAt(Instant.now())
                    .build());
        }

        /** Links {@code unit} to an assistant turn of a thread on {@code surface}, as a delivered conversation does. */
        private void raiseIn(Feedback unit, Workspace ws, User recipient, ThreadSurface surface) {
            ChatThread thread = new ChatThread();
            thread.setId(UUID.randomUUID());
            thread.setTitle(surface + " thread");
            thread.setUser(recipient);
            thread.setWorkspace(ws);
            thread.setSurface(surface);
            chatThreadRepository.save(thread);
            ChatMessage turn = new ChatMessage();
            turn.setId(UUID.randomUUID());
            turn.setThread(thread);
            turn.setRole(ChatMessage.Role.ASSISTANT);
            turn.setStatus(ChatMessage.Status.completed);
            turn.setParts(OBJECT_MAPPER.valueToTree(List.of(Map.of("type", "text", "text", "A turn"))));
            chatMessageRepository.saveAndFlush(turn);
            feedbackPlacementRepository.save(FeedbackPlacement.builder()
                    .feedback(unit)
                    .placementType(PlacementType.CONVERSATION_TURN)
                    .chatMessageId(turn.getId())
                    .createdAt(Instant.now())
                    .build());
        }

        @Test
        @WithAdminUser
        void shouldNameFilterAndCountFeedbackByLocationWhenEveryChannelHasFeedback() {
            job.setIntegrationKind(IntegrationKind.GITHUB);
            agentJobRepository.save(job);
            AgentJob gitlabJob = persistJob(workspace);
            gitlabJob.setIntegrationKind(IntegrationKind.GITLAB);
            agentJobRepository.save(gitlabJob);

            Feedback onGitHub =
                    unit(workspace, job, alice, FeedbackChannel.IN_CONTEXT, FeedbackDeliveryState.DELIVERED);
            unit(workspace, job, alice, FeedbackChannel.IN_CONTEXT, FeedbackDeliveryState.FAILED);
            Feedback onGitLab =
                    unit(workspace, gitlabJob, alice, FeedbackChannel.IN_CONTEXT, FeedbackDeliveryState.DELIVERED);
            Feedback inApp = unit(workspace, job, alice, FeedbackChannel.IN_CHAT, FeedbackDeliveryState.DELIVERED);
            raiseIn(inApp, workspace, alice, ThreadSurface.WEB);
            Feedback inSlack = unit(workspace, job, alice, FeedbackChannel.IN_CHAT, FeedbackDeliveryState.DELIVERED);
            raiseIn(inSlack, workspace, alice, ThreadSurface.SLACK_DM);
            unit(workspace, job, alice, FeedbackChannel.IN_CHAT, FeedbackDeliveryState.PREPARED);
            unit(workspace, job, alice, FeedbackChannel.IN_APP, FeedbackDeliveryState.SUPPRESSED);
            // Bob's feedback and another workspace's stay out of Alice's counts.
            unit(workspace, job, bob, FeedbackChannel.IN_CONTEXT, FeedbackDeliveryState.DELIVERED);
            unit(otherWorkspace, otherJob, alice, FeedbackChannel.IN_CONTEXT, FeedbackDeliveryState.DELIVERED);

            String slug = workspace.getWorkspaceSlug();
            getOk(FEEDBACK + "/{id}", slug, onGitHub.getId())
                    .jsonPath("$.location")
                    .isEqualTo("GITHUB");
            getOk(FEEDBACK + "/{id}", slug, onGitLab.getId())
                    .jsonPath("$.location")
                    .isEqualTo("GITLAB");
            getOk(FEEDBACK + "/{id}", slug, inSlack.getId())
                    .jsonPath("$.location")
                    .isEqualTo("SLACK");

            getOk(FEEDBACK + "?location=GITHUB&deliveryState=DELIVERED", slug)
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(2)
                    .jsonPath("$.content[0].location")
                    .isEqualTo("GITHUB")
                    .jsonPath("$.content[1].location")
                    .isEqualTo("GITHUB");
            getOk(FEEDBACK + "?location=HEPH&location=SLACK&recipientUserId={id}", slug, alice.getId())
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(2);
            getOk(FEEDBACK + "?location=HEPH", slug)
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].id")
                    .isEqualTo(inApp.getId().toString())
                    .jsonPath("$.content[0].location")
                    .isEqualTo("HEPH");
            getOk(FEEDBACK + "?location=NEXT_CONVERSATION", slug)
                    .jsonPath("$.page.totalElements")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].deliveryState")
                    .isEqualTo("PREPARED");

            getOk(LOCATIONS + "?recipientUserId={id}", slug, alice.getId())
                    .jsonPath("$.length()")
                    .isEqualTo(6)
                    .jsonPath("$[0].location")
                    .isEqualTo("GITHUB")
                    .jsonPath("$[0].feedback.delivered")
                    .isEqualTo(1)
                    .jsonPath("$[0].feedback.failed")
                    .isEqualTo(1)
                    .jsonPath("$[1].location")
                    .isEqualTo("GITLAB")
                    .jsonPath("$[1].feedback.delivered")
                    .isEqualTo(1)
                    .jsonPath("$[2].location")
                    .isEqualTo("HEPH")
                    .jsonPath("$[3].location")
                    .isEqualTo("SLACK")
                    .jsonPath("$[4].location")
                    .isEqualTo("NEXT_CONVERSATION")
                    .jsonPath("$[4].feedback.prepared")
                    .isEqualTo(1)
                    .jsonPath("$[5].location")
                    .isEqualTo("PRACTICE_PAGES")
                    .jsonPath("$[5].feedback.suppressed")
                    .isEqualTo(1);
        }

        @Test
        @WithAdminUser
        void shouldNameOtherWorkWhenTheReviewNamesNoProvider() {
            unit(workspace, job, alice, FeedbackChannel.IN_CONTEXT, FeedbackDeliveryState.DELIVERED);

            getOk(FEEDBACK, workspace.getWorkspaceSlug())
                    .jsonPath("$.content[0].location")
                    .isEqualTo("OTHER_WORK");
        }

        @Test
        @WithUser
        void shouldForbidTheCountsWhenTheCallerIsAMember() {
            get(LOCATIONS, workspace.getWorkspaceSlug())
                    .expectStatus()
                    .isForbidden()
                    .expectBody(Void.class);
        }
    }

    @Nested
    @DisplayName("Feedback withdrawal")
    class FeedbackWithdrawals {

        private static final String WITHDRAWAL = FEEDBACK + "/{id}/withdrawal";

        private WebTestClient.ResponseSpec patchWithdrawal(
                Workspace ws, UUID feedbackId, boolean withdrawn, String reason) {
            return webTestClient
                    .patch()
                    .uri(WITHDRAWAL, ws.getWorkspaceSlug(), feedbackId)
                    .headers(TestAuthUtils.withCurrentUser())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("withdrawn", withdrawn, "reason", reason))
                    .exchange();
        }

        private Feedback inApp(Workspace ws, AgentJob agentJob, int position, FeedbackDeliveryState state) {
            return feedbackRepository.save(Feedback.builder()
                    .agentJobId(agentJob.getId())
                    .workspaceId(ws.getId())
                    .recipientUserId(alice.getId())
                    .aboutUserId(alice.getId())
                    .channel(FeedbackChannel.IN_APP)
                    .position(position)
                    .deliveryState(state)
                    .suppressionReason(
                            state == FeedbackDeliveryState.SUPPRESSED
                                    ? FeedbackSuppressionReason.RECIPIENT_OPTED_OUT
                                    : null)
                    .body("Wrong words")
                    .source(FeedbackSource.AGENT)
                    .createdAt(Instant.now())
                    .deliveredAt(state == FeedbackDeliveryState.DELIVERED ? Instant.now() : null)
                    .build());
        }

        @Test
        @WithAdminUser
        void shouldWithdrawAndRestoreIdempotentlyKeepingEveryRoundAndTheFeedbackRow() {
            Feedback card = inApp(workspace, job, 8000, FeedbackDeliveryState.DELIVERED);

            patchWithdrawal(workspace, card.getId(), true, "  About older issues  ")
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.withdrawals.length()")
                    .isEqualTo(1)
                    .jsonPath("$.withdrawals[0].reason")
                    .isEqualTo("About older issues")
                    .jsonPath("$.withdrawals[0].withdrawnBy")
                    .isEqualTo("admin")
                    .jsonPath("$.body")
                    .doesNotExist();
            patchWithdrawal(workspace, card.getId(), true, "Again")
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.withdrawals.length()")
                    .isEqualTo(1)
                    .jsonPath("$.withdrawals[0].reason")
                    .isEqualTo("About older issues");
            patchWithdrawal(workspace, card.getId(), false, "It was right")
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.withdrawals[0].restorationReason")
                    .isEqualTo("It was right")
                    .jsonPath("$.withdrawals[0].restoredBy")
                    .isEqualTo("admin");
            patchWithdrawal(workspace, card.getId(), false, "Again")
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.withdrawals[0].restorationReason")
                    .isEqualTo("It was right");
            patchWithdrawal(workspace, card.getId(), true, "Second look")
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.withdrawals.length()")
                    .isEqualTo(2)
                    .jsonPath("$.withdrawals[0].reason")
                    .isEqualTo("Second look")
                    .jsonPath("$.withdrawals[1].reason")
                    .isEqualTo("About older issues")
                    .jsonPath("$.withdrawals[1].restorationReason")
                    .isEqualTo("It was right");

            Feedback reread = feedbackRepository
                    .findByIdAndWorkspaceId(card.getId(), workspace.getId())
                    .orElseThrow();
            assertThat(reread.getDeliveryState()).isEqualTo(FeedbackDeliveryState.DELIVERED);
            assertThat(reread.getBody()).isEqualTo("Wrong words");
        }

        @Test
        @WithAdminUser
        void shouldRefuseAnotherChannelAndAStateThatIsNotWaitingOrShown() {
            Feedback onTheWork =
                    persistUnit(workspace, job, alice, 8100, FeedbackDeliveryState.DELIVERED, null, "Note");
            Feedback suppressed = inApp(workspace, job, 8101, FeedbackDeliveryState.SUPPRESSED);

            for (Feedback refused : List.of(onTheWork, suppressed)) {
                patchWithdrawal(workspace, refused.getId(), true, "Wrong")
                        .expectStatus()
                        .isEqualTo(409)
                        .expectBody(Void.class);
                assertThat(withdrawalRepository.findHistory(workspace.getId(), refused.getId()))
                        .isEmpty();
            }
        }

        @Test
        @WithAdminUser
        void shouldNotFindOrChangeAnotherWorkspacesFeedback() {
            Feedback foreign = inApp(otherWorkspace, otherJob, 8200, FeedbackDeliveryState.DELIVERED);

            patchWithdrawal(workspace, foreign.getId(), true, "Not yours")
                    .expectStatus()
                    .isNotFound()
                    .expectBody(Void.class);

            assertThat(withdrawalRepository.findHistory(otherWorkspace.getId(), foreign.getId()))
                    .isEmpty();
        }

        @Test
        @WithAdminUser
        void shouldRequireAReason() {
            Feedback card = inApp(workspace, job, 8300, FeedbackDeliveryState.PREPARED);

            patchWithdrawal(workspace, card.getId(), true, "   ")
                    .expectStatus()
                    .isBadRequest()
                    .expectBody(Void.class);

            assertThat(withdrawalRepository.findHistory(workspace.getId(), card.getId()))
                    .isEmpty();
        }

        @Test
        @WithUser
        void shouldForbidAWorkspaceMember() {
            Feedback card = inApp(workspace, job, 8400, FeedbackDeliveryState.DELIVERED);

            patchWithdrawal(workspace, card.getId(), true, "Members cannot withdraw")
                    .expectStatus()
                    .isForbidden()
                    .expectBody(Void.class);

            assertThat(withdrawalRepository.findHistory(workspace.getId(), card.getId()))
                    .isEmpty();
        }
    }

    private DeliveryPolicyEvaluation policyEvaluation(
            AgentJob sourceJob, @Nullable UUID feedbackId, DeliveryPolicySurface surface) {
        return DeliveryPolicyEvaluation.builder()
                .workspaceId(sourceJob.getWorkspace().getId())
                .agentJobId(sourceJob.getId())
                .feedbackId(feedbackId)
                .admittedRevision(0L)
                .evaluatedRevision(1L)
                .resolverVersion("v1")
                .surface(surface)
                .stage(DeliveryPolicyStage.EGRESS)
                .allowed(false)
                .decisiveReason(FeedbackSuppressionReason.RECIPIENT_OPTED_OUT)
                .checks(OBJECT_MAPPER.readTree("[{\"check\":\"RECIPIENT_CONSENT\",\"status\":\"DENIED\"}]"))
                .facts(OBJECT_MAPPER.createObjectNode())
                .evaluatedAt(Instant.now())
                .build();
    }
}
