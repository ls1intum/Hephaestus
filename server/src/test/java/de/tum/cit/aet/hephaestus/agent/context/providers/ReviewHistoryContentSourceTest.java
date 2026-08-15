package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceContribution;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The workspace-context source: what earlier reviews recorded, and what was already said.
 *
 * <p>The two guarantees under test are the ones the rest of the design leans on. A review must always
 * receive the history, so that a sequence of single-event reviews can add up to more than a sequence.
 * And a person's first-ever review must receive it <em>present and empty</em>, because "the record was
 * read and held nothing" is a fact a review may reason from while "the record was never staged" is not.
 */
@Tag("unit")
class ReviewHistoryContentSourceTest {

    private static final long WORKSPACE_ID = 7L;
    private static final long PR_ID = 42L;
    private static final long AUTHOR_ID = 99L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ObservationRepository observationRepository;
    private FeedbackRepository feedbackRepository;
    private ObservationVisibilityPolicy visibilityPolicy;
    private PullRequestRepository pullRequestRepository;
    private IssueRepository issueRepository;
    private ReviewHistoryContentSource provider;

    @BeforeEach
    void setUp() {
        observationRepository = mock(ObservationRepository.class);
        feedbackRepository = mock(FeedbackRepository.class);
        visibilityPolicy = mock(ObservationVisibilityPolicy.class);
        pullRequestRepository = mock(PullRequestRepository.class);
        issueRepository = mock(IssueRepository.class);
        provider = new ReviewHistoryContentSource(
            observationRepository,
            feedbackRepository,
            visibilityPolicy,
            pullRequestRepository,
            issueRepository,
            objectMapper
        );
        when(observationRepository.findRecentByDeveloperAndWorkspace(any(), any(), any(), any())).thenReturn(List.of());
        when(feedbackRepository.findRecentDeliveredForRecipient(any(), any(), any(), any())).thenReturn(List.of());
        when(visibilityPolicy.permitsAll(anyLong(), any(), any())).thenAnswer(invocation -> {
            Collection<Observation> batch = invocation.getArgument(1);
            return batch.stream().map(Observation::getId).collect(Collectors.toSet());
        });
        when(pullRequestRepository.findByIdWithAuthorAndRepository(eq(PR_ID))).thenReturn(
            Optional.of(pullRequestBy(AUTHOR_ID))
        );
    }

    /**
     * Staged by every review without any practice declaring it — as every source now is. The history was
     * the first source that had to escape the per-practice union, because a review's record of the person
     * must not depend on 37 practice authors remembering to ask for it. That escape hatch is gone with
     * the union it escaped.
     */
    @Test
    void answersForBothHistoryKindsWithoutAnyPracticeDeclaringThem() {
        assertThat(provider.sourceKinds()).containsExactlyInAnyOrder(
            ReviewHistoryContentSource.OBSERVATION_HISTORY,
            ReviewHistoryContentSource.FEEDBACK_HISTORY
        );
    }

    /**
     * The shape production asks for. {@code WorkspaceContextBuilder} captures each source kind on its
     * own — one {@code capture} call per kind, so that one failing collector costs only its own source —
     * and then rejects a contribution that reports anything about a kind it did not ask about. A
     * collector that answers for both halves whichever half was requested therefore fails every review,
     * not just the half it overreached on.
     */
    @Nested
    class WhenOnlyOneHalfIsAskedFor {

        @Test
        void observationHistoryAloneAnswersForObservationHistoryOnly() {
            var captured = provider.capture(prRequest(), Set.of(ReviewHistoryContentSource.OBSERVATION_HISTORY));

            assertThat(captured.files()).containsOnlyKeys("inputs/history/observations.json");
            assertThat(captured.completeness()).containsOnlyKeys(ReviewHistoryContentSource.OBSERVATION_HISTORY);
            assertThat(captured.contentStates()).containsOnlyKeys(ReviewHistoryContentSource.OBSERVATION_HISTORY);
        }

        @Test
        void feedbackHistoryAloneAnswersForFeedbackHistoryOnly() {
            var captured = provider.capture(prRequest(), Set.of(ReviewHistoryContentSource.FEEDBACK_HISTORY));

            assertThat(captured.files()).containsOnlyKeys("inputs/history/feedback.json");
            assertThat(captured.completeness()).containsOnlyKeys(ReviewHistoryContentSource.FEEDBACK_HISTORY);
            assertThat(captured.contentStates()).containsOnlyKeys(ReviewHistoryContentSource.FEEDBACK_HISTORY);
        }

        /** An unasked-for half must not cost the read that answers it. */
        @Test
        void theUnaskedHalfIsNotQueried() {
            provider.capture(prRequest(), Set.of(ReviewHistoryContentSource.OBSERVATION_HISTORY));

            verify(feedbackRepository, never()).findRecentDeliveredForRecipient(any(), any(), any(), any());
        }
    }

    /**
     * The item-4 guarantee applied to history: a person with no record gets the files anyway.
     *
     * <p>The content state must still read EMPTY. Deriving it from the staged file list — which is what
     * the manifest does by default — would answer NON_EMPTY here and tell the review that a first-time
     * contributor has a history.
     */
    @Test
    void aFirstEverReviewGetsAPresentAndEmptyHistory() {
        var observationsCapture = captureObservationHistory();
        var feedbackCapture = captureFeedbackHistory();

        JsonNode observations = read(observationsCapture.files().get("inputs/history/observations.json"));
        JsonNode feedback = read(feedbackCapture.files().get("inputs/history/feedback.json"));
        assertThat(observations.get("observations")).isEmpty();
        assertThat(feedback.get("feedback")).isEmpty();
        assertThat(observationsCapture.contentStates()).containsEntry(
            ReviewHistoryContentSource.OBSERVATION_HISTORY,
            SourceContentState.EMPTY
        );
        assertThat(feedbackCapture.contentStates()).containsEntry(
            ReviewHistoryContentSource.FEEDBACK_HISTORY,
            SourceContentState.EMPTY
        );
    }

    @Test
    void stagesEarlierObservationsWithTheRecurrenceKeyThatLinksThem() {
        when(observationRepository.findRecentByDeveloperAndWorkspace(any(), any(), any(), any())).thenReturn(
            List.of(observation("swallows-errors", "rec-1", "Caught and ignored"))
        );

        var captured = captureObservationHistory();

        JsonNode entry = read(captured.files().get("inputs/history/observations.json")).get("observations").get(0);
        assertThat(entry.get("practiceSlug").asString()).isEqualTo("swallows-errors");
        assertThat(entry.get("recurrenceKey").asString()).isEqualTo("rec-1");
        assertThat(entry.get("title").asString()).isEqualTo("Caught and ignored");
        assertThat(captured.contentStates()).containsEntry(
            ReviewHistoryContentSource.OBSERVATION_HISTORY,
            SourceContentState.NON_EMPTY
        );
    }

    @Test
    void stagesFeedbackThatWasAlreadyDeliveredWithItsChannel() {
        when(feedbackRepository.findRecentDeliveredForRecipient(any(), any(), any(), any())).thenReturn(
            List.of(
                Feedback.builder()
                    .channel(FeedbackChannel.IN_CONTEXT)
                    .body("Consider handling this error rather than logging it.")
                    .deliveredAt(Instant.parse("2026-07-01T09:00:00Z"))
                    .build()
            )
        );

        var captured = captureFeedbackHistory();

        JsonNode entry = read(captured.files().get("inputs/history/feedback.json")).get("feedback").get(0);
        assertThat(entry.get("channel").asString()).isEqualTo("IN_CONTEXT");
        assertThat(entry.get("body").asString()).contains("rather than logging it");
        assertThat(captured.contentStates()).containsEntry(
            ReviewHistoryContentSource.FEEDBACK_HISTORY,
            SourceContentState.NON_EMPTY
        );
    }

    /**
     * A window over a growing record can show that something recurred and can never show that something
     * never happened, so COMPLETE is not a state this source is allowed to report.
     */
    @Test
    void neverReportsCompleteBecauseTheWindowIsBounded() {
        assertThat(captureObservationHistory().completeness().values()).containsOnly(SourceCompleteness.PARTIAL);
        assertThat(captureFeedbackHistory().completeness().values()).containsOnly(SourceCompleteness.PARTIAL);
    }

    /** An observation the visibility policy refuses is not staged, exactly as on every other read of it. */
    @Test
    void withholdsAnObservationTheVisibilityPolicyRefuses() {
        when(observationRepository.findRecentByDeveloperAndWorkspace(any(), any(), any(), any())).thenReturn(
            List.of(observation("swallows-errors", "rec-1", "Caught and ignored"))
        );
        // doReturn, not when(...): re-stubbing through when() would call the mock, running the
        // setUp answer against the matchers' null placeholders.
        doReturn(Set.of()).when(visibilityPolicy).permitsAll(anyLong(), any(), any());

        var captured = captureObservationHistory();

        assertThat(read(captured.files().get("inputs/history/observations.json")).get("observations")).isEmpty();
        assertThat(captured.contentStates()).containsEntry(
            ReviewHistoryContentSource.OBSERVATION_HISTORY,
            SourceContentState.EMPTY
        );
    }

    /**
     * An unresolvable subject is not an empty history.
     *
     * <p>Reporting EMPTY here would let a review conclude "this has never come up before" from a lookup
     * that never ran — the one way this source could manufacture the absence it exists to make checkable.
     */
    @Test
    void reportsUnavailableRatherThanEmptyWhenTheSubjectCannotBeResolved() {
        when(pullRequestRepository.findByIdWithAuthorAndRepository(eq(PR_ID))).thenReturn(Optional.empty());

        assertUnavailable(captureObservationHistory(), ReviewHistoryContentSource.OBSERVATION_HISTORY);
        assertUnavailable(captureFeedbackHistory(), ReviewHistoryContentSource.FEEDBACK_HISTORY);
        verifyNoInteractions(observationRepository, feedbackRepository);
    }

    private static void assertUnavailable(EvidenceContribution captured, SourceKind kind) {
        assertThat(captured.files()).isEmpty();
        assertThat(captured.stateOverrides()).containsOnlyKeys(kind);
        assertThat(captured.stateOverrides()).hasEntrySatisfying(kind, state ->
            assertThat(state).isInstanceOfSatisfying(SourceCaptureState.Unavailable.class, unavailable ->
                assertThat(unavailable.reasonCode()).isEqualTo(SourceAbsenceReason.NOT_FOUND)
            )
        );
    }

    /** History is about the person; the mentor chat has its own context sources and its own consent gate. */
    @Test
    void doesNotSupportTheMentorChat() {
        assertThat(
            provider.supports(new ContextRequest.MentorChatRequest(WORKSPACE_ID, AUTHOR_ID, UUID.randomUUID()))
        ).isFalse();
        assertThat(provider.supports(prRequest())).isTrue();
    }

    /**
     * One kind per call, which is the only shape {@code WorkspaceContextBuilder} produces. Asking for
     * both at once is a shape production never makes, and a collector tested that way can report a kind
     * it was not asked about — a contribution the builder rejects, failing the whole review.
     */
    private EvidenceContribution captureObservationHistory() {
        return provider.capture(prRequest(), Set.of(ReviewHistoryContentSource.OBSERVATION_HISTORY));
    }

    private EvidenceContribution captureFeedbackHistory() {
        return provider.capture(prRequest(), Set.of(ReviewHistoryContentSource.FEEDBACK_HISTORY));
    }

    private JsonNode read(byte[] bytes) {
        return objectMapper.readTree(bytes);
    }

    private ContextRequest.PracticeReviewRequest prRequest() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("pull_request_id", PR_ID);
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setMetadata(metadata);
        return new ContextRequest.PracticeReviewRequest(job);
    }

    private static PullRequest pullRequestBy(long authorId) {
        User author = new User();
        author.setId(authorId);
        PullRequest pullRequest = new PullRequest();
        pullRequest.setAuthor(author);
        return pullRequest;
    }

    private static Observation observation(String practiceSlug, String recurrenceKey, String title) {
        Practice practice = new Practice();
        practice.setSlug(practiceSlug);
        return Observation.builder()
            .id(UUID.randomUUID())
            .practice(practice)
            .recurrenceKey(recurrenceKey)
            .title(title)
            .presence(Presence.PRESENT)
            .assessment(Assessment.BAD)
            .observedAt(Instant.parse("2026-07-01T09:00:00Z"))
            .reasoning("The catch block logs and continues.")
            .build();
    }
}
