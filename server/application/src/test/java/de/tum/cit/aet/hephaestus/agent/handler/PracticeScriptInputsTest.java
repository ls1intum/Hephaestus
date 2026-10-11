package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.conversation.ConversationSourceLiveness;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.integration.scm.ReviewTargetQuery;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationInvalidationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** What the server stages beside each practice script: its cause and its previous claim. */
@ExtendWith(MockitoExtension.class)
class PracticeScriptInputsTest extends BaseUnitTest {

    private static final long WORKSPACE = 1L;
    private static final long PULL_REQUEST = 70L;
    private static final long AUTHOR = 11L;
    private static final Instant ADMITTED = Instant.parse("2026-02-03T04:05:06.123456Z");
    private static final String PREVIOUS_HEAD = "a".repeat(40);

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Mock
    private ObservationRepository observations;

    @Mock
    private EvidenceAuthorization evidenceAuthorization;

    @Mock
    private ObservationInvalidationRepository invalidations;

    @Mock
    private ConversationSourceLiveness conversations;

    @Mock
    private AgentJobRepository jobs;

    @Mock
    private ReviewTargetQuery reviewTargets;

    @Mock
    private UserRepository users;

    private PracticeScriptInputs inputs;

    @BeforeEach
    void setUp() {
        // The visibility rules are the real ones: currentness, invalidation and evidence authorization.
        inputs = new PracticeScriptInputs(
                mapper,
                observations,
                new ObservationVisibilityPolicy(evidenceAuthorization, invalidations),
                conversations,
                jobs,
                reviewTargets,
                users);
        lenient()
                .when(reviewTargets.findPullRequest(PULL_REQUEST))
                .thenReturn(Optional.of(new ReviewTargetQuery.Target(5L, "example/shop", 7, AUTHOR, false)));
        lenient()
                .when(observations.findStandingForWork(WORKSPACE, ArtifactKinds.PULL_REQUEST, PULL_REQUEST, AUTHOR))
                .thenReturn(List.of());
    }

    private static Practice practice(String slug, SignalName... signals) {
        Practice practice = new Practice();
        practice.setSlug(slug);
        PracticeTestEvidence.configure(practice, signals);
        practice.setReviewWhen(Map.of("draftStatus", Set.of("NOT_DRAFT")));
        PracticeRevision revision = new PracticeRevision();
        ReflectionTestUtils.setField(revision, "id", Integer.toUnsignedLong(slug.hashCode()) + 1);
        ReflectionTestUtils.setField(revision, "reviewRuleFingerprint", "v5:" + slug);
        practice.setCurrentRevision(revision);
        ReflectionTestUtils.setField(practice, "id", Integer.toUnsignedLong(slug.hashCode()) + 2);
        return practice;
    }

    private AgentJob job(TriggerMode mode, ObservationOrigin origin, SignalName signal, String... rechecked) {
        AgentJob job = new AgentJob();
        job.setId(UUID.randomUUID());
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE);
        job.setWorkspace(workspace);
        job.setPracticeTriggerMode(mode);
        job.setCreatedAt(ADMITTED);
        ObjectNode metadata = mapper.createObjectNode();
        metadata.put(ReviewOutputService.ORIGIN_METADATA_KEY, origin.name());
        metadata.put("pull_request_id", PULL_REQUEST);
        metadata.put(PracticeCatalogInjector.SIGNAL_METADATA_KEY, signal.value());
        metadata.putObject(PracticeCatalogInjector.REVIEW_STATE_METADATA_KEY)
                .put("draftStatus", "NOT_DRAFT")
                .put("state", "OPEN");
        if (rechecked.length > 0) {
            var slugs = metadata.putArray(AgentJob.RECHECKED_PRACTICES_METADATA_KEY);
            for (String slug : rechecked) slugs.add(slug);
        }
        job.setMetadata(metadata);
        return job;
    }

    private JsonNode staged(Map<String, byte[]> files, String slug, String suffix) {
        byte[] bytes = files.get(SandboxLayout.PRACTICE_SCRIPT_PREFIX + "practices/" + slug + suffix);
        assertThat(bytes).as("%s%s is staged", slug, suffix).isNotNull();
        return mapper.readTree(bytes);
    }

    private JsonNode cause(Map<String, byte[]> files, String slug) {
        return staged(files, slug, SandboxLayout.PRACTICE_SCRIPT_CAUSE_SUFFIX);
    }

    private JsonNode previous(Map<String, byte[]> files, String slug) {
        return staged(files, slug, SandboxLayout.PRACTICE_SCRIPT_PREVIOUS_SUFFIX);
    }

    @Test
    void shouldStageTheBoundMomentWithItsSignalStateAndAdmission() {
        Practice bound = practice("cites-tests", ScmSignals.PULL_REQUEST_SYNCHRONIZED);

        Map<String, byte[]> files = inputs.stage(
                job(TriggerMode.AUTO, ObservationOrigin.LIVE, ScmSignals.PULL_REQUEST_SYNCHRONIZED),
                ArtifactKinds.PULL_REQUEST,
                List.of(bound));

        assertThat(cause(files, "cites-tests")).isEqualTo(mapper.readTree("""
                        {"kind": "scm.pull_request", "subject": {"role": "AUTHOR"}, "cause": {
                          "type": "moment", "signal": "scm.pull_request.synchronized", "origin": "LIVE",
                          "state": {"draftStatus": "NOT_DRAFT", "state": "OPEN"},
                          "admittedAt": "2026-02-03T04:05:06.123456Z"}}
                        """));
        assertThat(previous(files, "cites-tests").isNull()).isTrue();
    }

    @Test
    void shouldStageARecheckForARecheckedPracticeTheMomentDoesNotBringIn() {
        Practice rechecked = practice("describes-change", ScmSignals.PULL_REQUEST_OPENED);
        // Rechecked as well as bound: the moment brings it in, so it is not a recheck.
        Practice boundAndRechecked = practice("cites-tests", ScmSignals.PULL_REQUEST_SYNCHRONIZED);

        Map<String, byte[]> files = inputs.stage(
                job(
                        TriggerMode.AUTO,
                        ObservationOrigin.LIVE,
                        ScmSignals.PULL_REQUEST_SYNCHRONIZED,
                        "describes-change",
                        "cites-tests"),
                ArtifactKinds.PULL_REQUEST,
                List.of(rechecked, boundAndRechecked));

        JsonNode recheck = cause(files, "describes-change").path("cause");
        assertThat(recheck.path("type").asString()).isEqualTo("recheck");
        assertThat(recheck.path("signal").asString()).isEqualTo("scm.pull_request.synchronized");
        assertThat(cause(files, "cites-tests").path("cause").path("type").asString())
                .isEqualTo("moment");
    }

    @Test
    void shouldStageARequestByHandWithoutASignal() {
        Map<String, byte[]> files = inputs.stage(
                job(TriggerMode.MANUAL, ObservationOrigin.MANUAL, ScmSignals.PULL_REQUEST_MANUAL_REVIEW),
                ArtifactKinds.PULL_REQUEST,
                List.of(practice("merges-after-approval", ScmSignals.PULL_REQUEST_MERGED)));

        JsonNode requested = cause(files, "merges-after-approval").path("cause");
        assertThat(requested.path("type").asString()).isEqualTo("requested");
        assertThat(requested.path("origin").asString()).isEqualTo("MANUAL");
        assertThat(requested.has("signal")).isFalse();
    }

    @Test
    void shouldStageTheReviewAndReviewerOfAReviewerMoment() {
        Practice reviewerPractice = practice("explains-requests", ScmSignals.PULL_REQUEST_REVIEWED);
        reviewerPractice.setSubject(ActorRole.REVIEWER);
        AgentJob job = job(TriggerMode.AUTO, ObservationOrigin.LIVE, ScmSignals.PULL_REQUEST_REVIEWED);
        ObjectNode metadata = assertInstanceOf(ObjectNode.class, job.getMetadata());
        metadata.put("review_id", 99L);
        metadata.put("about_user_id", 42L);
        metadata.put("subject_role", "REVIEWER");
        User reviewer = new User();
        reviewer.setLogin("ada");
        when(users.findById(42L)).thenReturn(Optional.of(reviewer));
        when(observations.findStandingForWork(WORKSPACE, ArtifactKinds.PULL_REQUEST, PULL_REQUEST, 42L))
                .thenReturn(List.of());

        JsonNode staged =
                cause(inputs.stage(job, ArtifactKinds.PULL_REQUEST, List.of(reviewerPractice)), "explains-requests");

        assertThat(staged.path("subject").path("role").asString()).isEqualTo("REVIEWER");
        assertThat(staged.path("cause").path("review"))
                .isEqualTo(mapper.readTree("{\"id\": 99, \"reviewer\": \"ada\"}"));
    }

    /** The current claim, made by a synchronized review of {@link #PREVIOUS_HEAD}. */
    private Observation claim(Practice practice, Outcome outcome, AgentJob run) {
        ObjectNode evidence = mapper.createObjectNode();
        evidence.putArray("citations")
                .add(mapper.readTree("""
                        {"sourceKind": "scm.pull-request.diff", "artifactPath": "context/change.json",
                         "path": "src/cart.ts", "startLine": 4, "endLine": 5, "side": "NEW",
                         "quote": "count += 1;"}
                        """))
                .add(mapper.readTree("{\"sourceKind\": \"scm.pull-request.core\", \"path\": \"broken\"}"));
        return Observation.builder()
                .id(UUID.randomUUID())
                .workspaceId(WORKSPACE)
                .practice(practice)
                .practiceRevision(practice.getCurrentRevision())
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(PULL_REQUEST)
                .aboutUserId(AUTHOR)
                .agentJobId(run.getId())
                .observedAt(Instant.parse("2026-02-01T00:00:00Z"))
                .summary("model words that never reach the script")
                .evidenceRationale("model words that never reach the script")
                .outcome(outcome)
                .severity(outcome == Outcome.NOT_MET ? Severity.MINOR : null)
                .evidence(evidence)
                .build();
    }

    private AgentJob previousRun() {
        AgentJob run = job(TriggerMode.AUTO, ObservationOrigin.LIVE, ScmSignals.PULL_REQUEST_OPENED);
        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.putObject("reviewedWork").put("head", PREVIOUS_HEAD);
        run.setEvidenceSnapshot(snapshot);
        when(jobs.findByIdAndWorkspaceId(run.getId(), WORKSPACE)).thenReturn(Optional.of(run));
        return run;
    }

    @Test
    void shouldStageTheCurrentClaimWithoutModelTextOrDelivery() {
        Practice practice =
                practice("cites-tests", ScmSignals.PULL_REQUEST_OPENED, ScmSignals.PULL_REQUEST_SYNCHRONIZED);
        Observation current = claim(practice, Outcome.NOT_MET, previousRun());
        when(observations.findStandingForWork(WORKSPACE, ArtifactKinds.PULL_REQUEST, PULL_REQUEST, AUTHOR))
                .thenReturn(List.of(current));
        when(invalidations.findActiveObservationIds(eq(WORKSPACE), anyCollection()))
                .thenReturn(Set.of());
        when(evidenceAuthorization.permitsAll(
                        eq(WORKSPACE), anyCollection(), eq(SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW)))
                .thenReturn(Set.of(current.getId()));

        Map<String, byte[]> files = inputs.stage(
                job(TriggerMode.AUTO, ObservationOrigin.LIVE, ScmSignals.PULL_REQUEST_SYNCHRONIZED),
                ArtifactKinds.PULL_REQUEST,
                List.of(practice));

        assertThat(previous(files, "cites-tests")).isEqualTo(mapper.readTree("""
                        {"reviewedAt": "2026-02-01T00:00:00Z", "cause": "moment",
                         "signal": "scm.pull_request.opened", "outcome": "NOT_MET", "severity": "MINOR",
                         "citations": [{"sourceKind": "scm.pull-request.diff", "path": "src/cart.ts",
                                        "startLine": 4, "endLine": 5, "side": "NEW"}],
                         "head": "%s"}
                        """.formatted(PREVIOUS_HEAD)));
    }

    @Test
    void shouldStageNoPreviousClaimWhenTheClaimIsInvalidatedOrNotAuthorized() {
        Practice invalidated = practice("cites-tests", ScmSignals.PULL_REQUEST_OPENED);
        Practice unauthorized = practice("describes-change", ScmSignals.PULL_REQUEST_OPENED);
        AgentJob run = new AgentJob();
        run.setId(UUID.randomUUID());
        Observation marked = claim(invalidated, Outcome.NOT_MET, run);
        Observation withdrawnSource = claim(unauthorized, Outcome.MET, run);
        when(observations.findStandingForWork(WORKSPACE, ArtifactKinds.PULL_REQUEST, PULL_REQUEST, AUTHOR))
                .thenReturn(List.of(marked, withdrawnSource));
        // An admin marked one incorrect; the evidence of the other is no longer authorized for review.
        when(invalidations.findActiveObservationIds(eq(WORKSPACE), anyCollection()))
                .thenReturn(Set.of(marked.getId()));
        when(evidenceAuthorization.permitsAll(anyLong(), anyCollection(), any()))
                .thenReturn(Set.of());

        Map<String, byte[]> files = inputs.stage(
                job(TriggerMode.AUTO, ObservationOrigin.LIVE, ScmSignals.PULL_REQUEST_OPENED),
                ArtifactKinds.PULL_REQUEST,
                List.of(invalidated, unauthorized));

        assertThat(previous(files, "cites-tests").isNull()).isTrue();
        assertThat(previous(files, "describes-change").isNull()).isTrue();
    }

    @Test
    void shouldStageNoPreviousClaimOfAConversationWhoseChannelNoLongerConsents() {
        ArtifactKind conversation = ArtifactKinds.CONVERSATION_THREAD;
        Practice practice =
                practice("answers-questions", PracticeTestEvidence.defaultSignal(ArtifactKinds.CONVERSATION_THREAD));
        AgentJob job = job(
                TriggerMode.AUTO,
                ObservationOrigin.LIVE,
                PracticeTestEvidence.defaultSignal(ArtifactKinds.CONVERSATION_THREAD));
        ObjectNode metadata = assertInstanceOf(ObjectNode.class, job.getMetadata());
        metadata.put("slack_thread_id", 8L);
        metadata.put("about_user_id", AUTHOR);
        metadata.remove(PracticeCatalogInjector.REVIEW_STATE_METADATA_KEY);
        Observation current = Observation.builder()
                .id(UUID.randomUUID())
                .workspaceId(WORKSPACE)
                .practice(practice)
                .practiceRevision(practice.getCurrentRevision())
                .artifactKind(conversation)
                .artifactId(8L)
                .aboutUserId(AUTHOR)
                .agentJobId(UUID.randomUUID())
                .observedAt(Instant.parse("2026-02-01T00:00:00Z"))
                .summary("s")
                .outcome(Outcome.MET)
                .build();
        when(observations.findStandingForWork(WORKSPACE, conversation, 8L, AUTHOR))
                .thenReturn(List.of(current));
        when(invalidations.findActiveObservationIds(eq(WORKSPACE), anyCollection()))
                .thenReturn(Set.of());
        when(evidenceAuthorization.permitsAll(anyLong(), anyCollection(), any()))
                .thenReturn(Set.of(current.getId()));
        when(conversations.activeThreadIds(WORKSPACE, List.of(8L))).thenReturn(Set.of());

        Map<String, byte[]> files = inputs.stage(job, conversation, List.of(practice));

        assertThat(cause(files, "answers-questions").path("cause").path("state"))
                .isEqualTo(mapper.createObjectNode());
        assertThat(previous(files, "answers-questions").isNull()).isTrue();
    }
}
