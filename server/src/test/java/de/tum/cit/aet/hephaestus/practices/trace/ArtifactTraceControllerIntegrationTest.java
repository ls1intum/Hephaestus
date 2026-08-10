package de.tum.cit.aet.hephaestus.practices.trace;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignal;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignalRepository;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalState;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * The trace view answers "why didn't anything happen to my merge request?" to a workspace member.
 *
 * <p>Two properties matter more than the rendering. The ledger is the tenancy boundary — an artifact
 * with no recorded signal in this workspace is a 404 whatever the mirror holds — and a practice that is
 * quiet is present in the answer with its reason rather than absent from it.
 */
class ArtifactTraceControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TRACE = "/workspaces/{slug}/practices/trace/{kind}/{id}";
    private static final String LIST = "/workspaces/{slug}/practices/trace";
    private static final long ARTIFACT_ID = 482L;
    private static final Instant READY_AT = Instant.parse("2026-08-07T14:02:00Z");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ArtifactSignalRepository signalRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    private Workspace workspace;
    private Workspace otherWorkspace;
    private User author;

    @BeforeEach
    void setUpWorkspaces() {
        User owner = persistUser("trace-owner");
        workspace = createWorkspace("trace-ws", "Trace WS", "trace-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        author = persistUser("alice");
        User member = persistUser("testuser");
        User workspaceAdmin = persistUser("mentor");
        ensureWorkspaceMembership(workspace, author, WorkspaceMembership.WorkspaceRole.MEMBER);
        ensureWorkspaceMembership(workspace, member, WorkspaceMembership.WorkspaceRole.MEMBER);
        ensureWorkspaceMembership(workspace, workspaceAdmin, WorkspaceMembership.WorkspaceRole.ADMIN);

        User otherOwner = persistUser("other-owner");
        otherWorkspace = createWorkspace("other-trace-ws", "Other", "other-trace-org", AccountType.ORG, otherOwner);
    }

    @Nested
    @DisplayName("Access control")
    class AccessControl {

        @Test
        void refusesAnAnonymousCaller() {
            recordSignal(workspace, ScmSignals.PULL_REQUEST_READY, SignalState.RECORDED, null, null);

            webTestClient
                .get()
                .uri(TRACE, workspace.getWorkspaceSlug(), ArtifactKinds.PULL_REQUEST.value(), ARTIFACT_ID)
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @WithUser
        void admitsAnOrdinaryWorkspaceMember() {
            recordSignal(workspace, ScmSignals.PULL_REQUEST_READY, SignalState.RECORDED, null, null);

            get(TRACE, workspace.getWorkspaceSlug(), ArtifactKinds.PULL_REQUEST.value(), ARTIFACT_ID)
                .expectStatus()
                .isOk();
        }

        /**
         * The ledger, not the mirror, decides what a workspace may see. Another tenant's artifact id has
         * no row here, so it is indistinguishable from work nothing was ever recorded about — which is
         * both the safe answer and the true one.
         */
        @Test
        @WithUser
        void hidesAnArtifactOnlyAnotherWorkspaceRecorded() {
            recordSignal(otherWorkspace, ScmSignals.PULL_REQUEST_READY, SignalState.RECORDED, null, null);

            get(TRACE, workspace.getWorkspaceSlug(), ArtifactKinds.PULL_REQUEST.value(), ARTIFACT_ID)
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithUser
        void refusesAnUnknownArtifactKind() {
            get(TRACE, workspace.getWorkspaceSlug(), "NotAKind", ARTIFACT_ID).expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("The answer")
    class Answers {

        /**
         * Four practices, four different answers, on a workspace with no SCM connection registered — the
         * state a half-onboarded workspace is really in.
         *
         * <p>{@code dormant} watches something no connected integration raises and that has never arrived,
         * so it is reported as waiting. {@code not-admitted} watches the very signal in the ledger, so it
         * is <em>not</em> reported as waiting even though coverage would say so: the recorded occurrence
         * refutes the claim.
         */
        @Test
        @WithMentorUser
        void reportsEveryPracticeIncludingTheQuietOnes() {
            Practice reviewed = persistPractice("reviewed", "Reviewed practice", PracticeReviewTier.ENGAGE);
            persistPractice("silenced", "Silenced practice", PracticeReviewTier.OFF);
            persistPractice("not-admitted", "Not admitted practice", PracticeReviewTier.ENGAGE);
            persistPractice("dormant", "Dormant practice", PracticeReviewTier.ENGAGE, ScmSignals.PULL_REQUEST_MERGED);
            AgentJob job = persistJob();
            recordSignal(workspace, ScmSignals.PULL_REQUEST_READY, SignalState.TRIGGERED, null, job.getId());
            insertObservation(reviewed, job);

            get(TRACE, workspace.getWorkspaceSlug(), ArtifactKinds.PULL_REQUEST.value(), ARTIFACT_ID)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.artifactId")
                .isEqualTo(ARTIFACT_ID)
                .jsonPath("$.signals.length()")
                .isEqualTo(1)
                .jsonPath("$.signals[0].signal")
                .isEqualTo(ScmSignals.PULL_REQUEST_READY.value())
                .jsonPath("$.signals[0].displayName")
                .isEqualTo("Marked ready for review")
                .jsonPath("$.practices.length()")
                .isEqualTo(4)
                .jsonPath("$.practices[?(@.practiceSlug=='reviewed')].outcome")
                .isEqualTo("REVIEWED")
                .jsonPath("$.practices[?(@.practiceSlug=='reviewed')].observationCount")
                .isEqualTo(1)
                .jsonPath("$.practices[?(@.practiceSlug=='silenced')].outcome")
                .isEqualTo("TURNED_OFF")
                .jsonPath("$.practices[?(@.practiceSlug=='not-admitted')].outcome")
                .isEqualTo("SKIPPED")
                .jsonPath("$.practices[?(@.practiceSlug=='dormant')].outcome")
                .isEqualTo("DORMANT")
                .jsonPath("$.practices[?(@.practiceSlug=='dormant')].explanation")
                .value(
                    org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString(
                            "No connected integration raises scm.pull_request.merged; connect GITHUB or GITLAB"
                        )
                    )
                );
        }

        @Test
        @WithMentorUser
        void explainsARefusedSignalWithTheActionThatWouldLiftIt() {
            persistPractice("waiting", "Waiting practice", PracticeReviewTier.ENGAGE);
            recordSignal(
                workspace,
                ScmSignals.PULL_REQUEST_READY,
                SignalState.PENDING,
                SignalStateReason.BUDGET_EXHAUSTED,
                null
            );

            get(TRACE, workspace.getWorkspaceSlug(), ArtifactKinds.PULL_REQUEST.value(), ARTIFACT_ID)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.practices[0].outcome")
                .isEqualTo("PENDING")
                .jsonPath("$.practices[0].explanation")
                .value(String.class, org.hamcrest.Matchers.containsString("budget refills"))
                .jsonPath("$.signals[0].stateReason")
                .isEqualTo("BUDGET_EXHAUSTED");
        }

        @Test
        @WithMentorUser
        void answersNothingForAnArtifactNobodyRecordedAnythingAbout() {
            persistPractice("waiting", "Waiting practice", PracticeReviewTier.ENGAGE);

            get(TRACE, workspace.getWorkspaceSlug(), ArtifactKinds.PULL_REQUEST.value(), ARTIFACT_ID)
                .expectStatus()
                .isNotFound();
        }
    }

    @Nested
    @DisplayName("The index")
    class Index {

        @Test
        @WithUser
        void listsWorkThatWasNeverReviewed() {
            recordSignal(workspace, ScmSignals.PULL_REQUEST_READY, SignalState.RECORDED, null, null);
            recordSignal(otherWorkspace, ScmSignals.PULL_REQUEST_MERGED, SignalState.RECORDED, null, null);

            get(LIST, workspace.getWorkspaceSlug())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(1)
                .jsonPath("$.content[0].artifactId")
                .isEqualTo(ARTIFACT_ID)
                .jsonPath("$.content[0].artifactKind")
                .isEqualTo(ArtifactKinds.PULL_REQUEST.value())
                .jsonPath("$.content[0].signalCount")
                .isEqualTo(1)
                .jsonPath("$.content[0].reviewedSignalCount")
                .isEqualTo(0);
        }

        @Test
        @WithUser
        void filtersByKind() {
            recordSignal(workspace, ScmSignals.PULL_REQUEST_READY, SignalState.RECORDED, null, null);

            get(LIST + "?artifactKind={kind}", workspace.getWorkspaceSlug(), ArtifactKinds.ISSUE.value())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(0);
        }
    }

    private WebTestClient.ResponseSpec get(String uri, Object... vars) {
        return webTestClient.get().uri(uri, vars).headers(TestAuthUtils.withCurrentUser()).exchange();
    }

    private Practice persistPractice(String slug, String name, PracticeReviewTier tier) {
        return persistPractice(slug, name, tier, ScmSignals.PULL_REQUEST_READY);
    }

    private Practice persistPractice(String slug, String name, PracticeReviewTier tier, SignalName signal) {
        Practice practice = new Practice();
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        practice.setWorkspace(workspace);
        practice.setSlug(slug);
        practice.setName(name);
        practice.setCriteria("Criteria for " + slug);
        practice.setBindings(PracticeTestEvidence.bindings(signal));
        practice.setReviewTier(tier);
        return practiceRepository.save(practice);
    }

    private AgentJob persistJob() {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        // A finished run: an unfinished one is reported as still running, which is a different answer.
        job.setStatus(AgentJobStatus.COMPLETED);
        job.setCompletedAt(READY_AT);
        return agentJobRepository.save(job);
    }

    private ArtifactSignal recordSignal(
        Workspace ws,
        SignalName signal,
        SignalState state,
        SignalStateReason reason,
        UUID jobId
    ) {
        ArtifactSignal row = new ArtifactSignal();
        row.setId(UUID.randomUUID());
        row.setWorkspace(ws);
        row.setArtifactKind(ArtifactKinds.PULL_REQUEST.value());
        row.setArtifactId(ARTIFACT_ID);
        row.setSignalName(signal.value());
        row.setRevision("sha~deadbeef");
        row.setOccurredAt(READY_AT);
        row.setDiscoveredVia(DiscoveredVia.EVENT);
        row.setState(state);
        row.setStateReason(reason);
        row.setJobId(jobId);
        row.setStateChangedAt(READY_AT);
        return signalRepository.save(row);
    }

    private void insertObservation(Practice practice, AgentJob job) {
        UUID id = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            id,
            "occurrence-" + id,
            job.getId(),
            practice.getId(),
            null,
            ArtifactKinds.PULL_REQUEST.value(),
            ARTIFACT_ID,
            author.getId(),
            "Something was observed",
            "PRESENT",
            "GOOD",
            "INFO",
            0.9f,
            "{\"citations\":[]}",
            "Because the diff says so",
            "recurrence-1",
            READY_AT,
            "LIVE"
        );
    }
}
