package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedback;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackIds;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCandidate;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeedbackDraftBuilderTest extends BaseUnitTest {
    @Test
    void shouldAllocateAStableSummaryCopyFromAnAdmittedBasis() {
        var job = RuntimeReviewTestData.job();
        UUID resultId = UUID.randomUUID();
        var candidate = mock(ResultCandidate.class);
        when(candidate.developerId()).thenReturn(5L);
        var piece = new ComposedFeedback(
                FeedbackDestination.ON_WORK, List.of(resultId), "Use clear names.", List.of(), null);
        var drafts = FeedbackDraftBuilder.build(
                job,
                Set.of(resultId),
                List.of(new AdmittedResultSnapshot(resultId, candidate, List.of())),
                List.of(piece),
                Map.of());
        var draft = drafts.getFirst();
        assertThat(draft.id()).isEqualTo(FeedbackIds.onWork(1, job.getReviewId(), 5));
        assertThat(draft.basis().getFirst().resultId()).isEqualTo(resultId);
        assertThat(draft.copies().getFirst().kind()).isEqualTo(FeedbackCopyKind.SUMMARY);
        assertThat(draft.copies().getFirst().body()).isNull();
        assertThat(draft.body()).isEqualTo("Use clear names.");
        assertThat(draft.copies().getFirst().writeKey())
                .isEqualTo(draft.copies().getFirst().id().toString());
    }

    @Test
    void shouldRejectAComposerResultThatWasNotInTheFencedBasis() {
        var piece = new ComposedFeedback(
                FeedbackDestination.CONVERSATION, List.of(UUID.randomUUID()), "A private brief", List.of(), null);
        assertThatThrownBy(() -> FeedbackDraftBuilder.build(
                        RuntimeReviewTestData.job(), Set.of(), List.of(), List.of(piece), Map.of()))
                .isInstanceOf(ResultAdmissionService.AdmissionConflictException.class);
    }

    @Test
    void shouldRejectDeveloperlessResultsAsFeedback() {
        UUID id = UUID.randomUUID();
        var candidate = mock(ResultCandidate.class);
        var piece =
                new ComposedFeedback(FeedbackDestination.CONVERSATION, List.of(id), "A private brief", List.of(), null);
        assertThatThrownBy(() -> FeedbackDraftBuilder.build(
                        RuntimeReviewTestData.job(),
                        Set.of(id),
                        List.of(new AdmittedResultSnapshot(id, candidate, List.of())),
                        List.of(piece),
                        Map.of()))
                .isInstanceOf(ResultAdmissionService.AdmissionConflictException.class);
    }
}
