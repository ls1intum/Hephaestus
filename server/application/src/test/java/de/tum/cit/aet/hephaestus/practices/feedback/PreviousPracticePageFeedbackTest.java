package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.feedback.page.PracticePageFeedbackBasis;
import de.tum.cit.aet.hephaestus.practices.feedback.response.FeedbackResponseRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PreviousPracticePageFeedbackTest {

    @Test
    void shouldNeverExposeACardWithUnreadableBasis() {
        var feedback = mock(FeedbackRepository.class);
        var basis = mock(PracticePageFeedbackBasis.class);
        var id = UUID.randomUUID();
        when(feedback.findLatestPracticePage(1, 2, "testing"))
                .thenReturn(List.of(
                        Feedback.builder().id(id).createdAt(Instant.EPOCH).build()));
        assertThat(new PreviousPracticePageFeedback(
                                feedback,
                                basis,
                                mock(FeedbackResponseRepository.class),
                                mock(FeedbackVisibilityService.class))
                        .find(1, 2, "testing", Instant.EPOCH))
                .isEmpty();
    }

    @Test
    void shouldResumeTheEvidenceWindowAtTheClosureWithoutMovingItBackwards() {
        Instant prepared = Instant.parse("2026-01-01T00:00:00Z"),
                closed = prepared.plusSeconds(60),
                window = prepared.plusSeconds(120);
        var prior = new PreviousPracticePageFeedback.Previous(UUID.randomUUID(), prepared, closed, false);
        assertThat(prior.isOpen()).isFalse();
        assertThat(prior.nextEvidenceSince(prepared)).isEqualTo(closed);
        assertThat(prior.nextEvidenceSince(window)).isEqualTo(window);
    }
}
