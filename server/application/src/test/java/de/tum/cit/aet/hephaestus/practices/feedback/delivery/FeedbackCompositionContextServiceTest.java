package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackBasisRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackGate;
import de.tum.cit.aet.hephaestus.practices.feedback.PreviousPracticePageFeedback;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeVersion;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ProblemRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationDecision;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheck;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.MentorReadinessQuery;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.PracticePageDecision;
import de.tum.cit.aet.hephaestus.practices.spi.PracticePageSupport;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class FeedbackCompositionContextServiceTest {
    final ReviewService reviews = mock(ReviewService.class);
    final ResultRepository results = mock(ResultRepository.class);
    final PracticeRepository practices = mock(PracticeRepository.class);
    final PracticeVersionRepository versions = mock(PracticeVersionRepository.class);
    final PracticePageSupportReader support = mock(PracticePageSupportReader.class);
    final FeedbackGate gate = mock(FeedbackGate.class);
    final ItemCatalog catalog = mock(ItemCatalog.class);
    final FeedbackBasisRepository basis = mock(FeedbackBasisRepository.class);
    final ResultHistoryQuery history = mock(ResultHistoryQuery.class);
    final MentorReadinessQuery mentor = mock(MentorReadinessQuery.class);
    final Instant now = Instant.parse("2026-10-14T00:00:00Z");
    final UUID reviewId = UUID.randomUUID();
    final Result current = result(3, reviewId), historical = result(4, UUID.randomUUID());
    final ReviewSnapshot snapshot = mock(ReviewSnapshot.class);
    final FeedbackCompositionContextService service = new FeedbackCompositionContextService(
            reviews,
            results,
            practices,
            versions,
            support,
            new PracticePageFeedbackRouter(),
            gate,
            basis,
            mock(ProblemRepository.class),
            catalog,
            history,
            mentor,
            Clock.fixed(now, ZoneOffset.UTC));

    @BeforeEach
    void setup() {
        when(snapshot.id()).thenReturn(reviewId);
        when(snapshot.workspaceId()).thenReturn(1L);
        when(snapshot.kind()).thenReturn(ReviewKind.ITEM);
        when(snapshot.origin()).thenReturn(ReviewOrigin.EVENT);
        when(snapshot.item()).thenReturn(new ItemRef(1, 3, new ItemKind("scm.pull_request"), 42L, null, null));
        var pin = mock(PinnedPractice.class);
        when(pin.practiceId()).thenReturn(5L);
        when(pin.definition()).thenReturn(mock(PracticeDefinition.class));
        when(snapshot.practices()).thenReturn(List.of(pin));
        when(reviews.find(1, reviewId)).thenReturn(Optional.of(snapshot));
        when(reviews.find(1, historical.getReviewId())).thenReturn(Optional.of(snapshot));
        when(results.findByWorkspaceIdAndIdIn(1, List.of(current.getId()))).thenReturn(List.of(current));
        var practice = mock(Practice.class);
        when(practice.getWorkspaceId()).thenReturn(1L);
        when(practice.getSlug()).thenReturn("focused-tests");
        when(practices.findById(5L)).thenReturn(Optional.of(practice));
        var version = mock(PracticeVersion.class);
        when(version.getWorkspaceId()).thenReturn(1L);
        when(version.getDefinition()).thenReturn(new JsonMapper().readTree("{\"about\":\"AUTHOR\"}"));
        when(versions.findById(8L)).thenReturn(Optional.of(version));
        var check = mock(FeedbackCheck.class);
        when(check.allowed()).thenReturn(true);
        when(gate.evaluateComposition(any(), anyLong(), anyList(), any())).thenReturn(check);
        when(gate.mode(any())).thenReturn(FeedbackMode.AUTOMATIC);
        when(mentor.isReady(1)).thenReturn(true);
        when(support.select(1, 7, 5, now))
                .thenReturn(new PracticePageSupportReader.Selection(Optional.empty(), List.of(current, historical)));
    }

    private Result result(long itemId, UUID review) {
        return Result.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .reviewId(review)
                .practiceId(5)
                .practiceVersionId(8L)
                .itemId(itemId)
                .developerId(7L)
                .outcome(ResultOutcome.NOT_MET)
                .summary("Add a focused test.")
                .recordedAt(now.minusSeconds(60))
                .build();
    }

    private void authorizeHistory(Result row) {
        var entry = mock(ResultHistoryEntry.class);
        when(entry.resultId()).thenReturn(row.getId());
        when(entry.developerAccountId()).thenReturn(7L);
        when(entry.practiceId()).thenReturn(5L);
        when(entry.itemId()).thenReturn(row.getItemId());
        when(entry.itemKind()).thenReturn("scm.pull_request");
        when(entry.outcome()).thenReturn(ResultOutcome.NOT_MET);
        when(entry.origin()).thenReturn(ReviewOrigin.EVENT);
        when(entry.summary()).thenReturn(row.getSummary());
        when(entry.recordedAt()).thenReturn(row.getRecordedAt());
        when(entry.copyAuthorityComplete()).thenReturn(true);
        when(history.find(1, row.getId(), 7, SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
                .thenReturn(Optional.of(entry));
    }

    @Test
    void shouldExposeOnlyAuthorizedCurrentDistinctWorkAndRouteConversation() {
        authorizeHistory(current);
        authorizeHistory(historical);
        var read = service.read(1, reviewId, List.of(current.getId()));
        var page = Objects.requireNonNull(read.practicePageSupport());
        assertThat(page.state()).isEqualTo(PracticePageSupport.State.COMPLETE);
        assertThat(page.readAt()).isEqualTo(now);
        assertThat(page.practices().getFirst().decision()).isEqualTo(PracticePageDecision.ADMIT);
        assertThat(page.practices().getFirst().occurrences())
                .extracting(PracticePageSupport.Occurrence::resultId)
                .containsExactly(current.getId(), historical.getId());
        assertThat(Objects.requireNonNull(read.conversationEligibility())
                        .results()
                        .getFirst()
                        .decision())
                .isEqualTo(ConversationDecision.ADMIT);
    }

    @Test
    void shouldWithholdPatternWordsWhenOnlyOneItemRemainsAuthorized() {
        var refusal = mock(FeedbackCheck.class);
        when(gate.evaluateComposition(snapshot, 7, List.of(historical), FeedbackDestination.PRACTICE_PAGE))
                .thenReturn(refusal);
        var page = Objects.requireNonNull(
                service.read(1, reviewId, List.of(current.getId())).practicePageSupport());
        assertThat(page.practices().getFirst().decision()).isEqualTo(PracticePageDecision.UNCORROBORATED);
        assertThat(page.practices().getFirst().occurrences()).isEmpty();
        verifyNoInteractions(history);
    }

    @Test
    void shouldCopyNoPartialPatternWhenHistoryCustodyIsUnavailable() {
        authorizeHistory(current);
        var page = Objects.requireNonNull(
                service.read(1, reviewId, List.of(current.getId())).practicePageSupport());
        assertThat(page.state()).isEqualTo(PracticePageSupport.State.UNAVAILABLE);
        assertThat(page.refusal()).isEqualTo("HISTORY_UNAVAILABLE");
        assertThat(page.practices()).isEmpty();
    }

    @Test
    void shouldRequireOneAttributedDeveloperAndRejectForeignReviewIds() {
        current.setDeveloperId(null);
        assertThat(Objects.requireNonNull(service.read(1, reviewId, List.of(current.getId()))
                                .practicePageSupport())
                        .state())
                .isEqualTo(PracticePageSupport.State.UNAVAILABLE);
        current.setReviewId(UUID.randomUUID());
        assertThatThrownBy(() -> service.read(1, reviewId, List.of(current.getId())))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(history, mentor);
    }

    @Test
    void shouldDistinguishUnavailableMentorConfirmedOnWorkAndBackfill() {
        when(mentor.isReady(1)).thenReturn(false);
        assertThat(service.conversationDecision(snapshot, current)).isEqualTo(ConversationDecision.MENTOR_UNAVAILABLE);
        when(mentor.isReady(1)).thenReturn(true);
        when(basis.hasConfirmedOnWork(1, 7, current.getId())).thenReturn(true);
        assertThat(service.conversationDecision(snapshot, current))
                .isEqualTo(ConversationDecision.ALREADY_DELIVERED_ON_WORK);
        when(snapshot.origin()).thenReturn(ReviewOrigin.BACKFILL);
        assertThat(service.conversationDecision(snapshot, current)).isEqualTo(ConversationDecision.BACKFILL_QUIET);
    }

    @Test
    void shouldKeepFeedbackOffReviewerAndNaturalAnchorReasonsDistinct() {
        when(gate.mode(current)).thenReturn(FeedbackMode.OFF);
        assertThat(service.conversationDecision(snapshot, current))
                .isEqualTo(ConversationDecision.PRACTICE_REQUIRES_APPROVAL);
        when(gate.mode(current)).thenReturn(FeedbackMode.AUTOMATIC);
        var version = versions.findById(8L).orElseThrow();
        when(version.getDefinition()).thenReturn(new JsonMapper().readTree("{\"about\":\"REVIEWER\"}"));
        assertThat(service.conversationDecision(snapshot, current)).isEqualTo(ConversationDecision.REVIEWER_DEFERRED);
        when(version.getDefinition()).thenReturn(new JsonMapper().readTree("{\"about\":\"AUTHOR\"}"));
        var pin = snapshot.practices().getFirst();
        when(pin.definition().lineNotes()).thenReturn(true);
        var descriptor = mock(ItemDescriptor.class);
        when(descriptor.copyCapabilities()).thenReturn(Set.of(CopyCapability.LINE_NOTE));
        when(catalog.descriptorFor(snapshot.item().kind())).thenReturn(Optional.of(descriptor));
        current.setEvidence(new JsonMapper().readTree("""
            {"citations":[{"path":"src/Work.java","startLine":3,"side":"NEW",
              "verification":{"status":"VERIFIED"}}]}
            """));
        assertThat(service.conversationDecision(snapshot, current)).isEqualTo(ConversationDecision.HAS_INLINE_ANCHOR);
    }

    @Test
    void shouldKeepARecentlySurfacedPracticeQuietWithoutCopyingWords() {
        var previous =
                new PreviousPracticePageFeedback.Previous(UUID.randomUUID(), now.minusSeconds(3600), null, false);
        when(support.select(1, 7, 5, now))
                .thenReturn(
                        new PracticePageSupportReader.Selection(Optional.of(previous), List.of(current, historical)));
        var page = Objects.requireNonNull(
                service.read(1, reviewId, List.of(current.getId())).practicePageSupport());
        assertThat(page.practices().getFirst().decision()).isEqualTo(PracticePageDecision.RECENTLY_SURFACED);
        assertThat(page.practices().getFirst().occurrences()).isEmpty();
        verifyNoInteractions(history);
    }
}
