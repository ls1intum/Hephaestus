package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import static de.tum.cit.aet.hephaestus.practices.result.ResultTestFixtures.resultBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackQueryRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PreviousPracticePageFeedback;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PracticePageSupportReaderTest {
    private final PreviousPracticePageFeedback previous = mock(PreviousPracticePageFeedback.class);
    private final PracticeRepository practices = mock(PracticeRepository.class);
    private final FeedbackQueryRepository queries = mock(FeedbackQueryRepository.class);
    private final Instant now = Instant.parse("2026-10-14T00:00:00Z");
    private final PracticePageSupportReader reader = new PracticePageSupportReader(previous, practices, queries);
    private final UUID reviewId = UUID.randomUUID();

    private Result result(Instant recordedAt) {
        return Result.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .reviewId(reviewId)
                .practiceId(5)
                .developerId(7L)
                .recordedAt(recordedAt)
                .evidenceVersion((short) 1)
                .outcome(ResultOutcome.NOT_MET)
                .build();
    }

    @Test
    void shouldHoldOnlySupportWhoseDistinctReviewsAreAllBackfill() {
        var first = result(now);
        var second = resultBuilder(first).id(UUID.randomUUID()).build();
        when(queries.reviewOrigins(1, List.of(reviewId))).thenReturn(List.of(ReviewOrigin.BACKFILL));
        assertThat(reader.isBackfillOnly(1, List.of(first, second))).isTrue();
        when(queries.reviewOrigins(1, List.of(reviewId))).thenReturn(List.of(ReviewOrigin.REQUEST));
        assertThat(reader.isBackfillOnly(1, List.of(first, second))).isFalse();
        assertThat(reader.isBackfillOnly(1, List.of())).isFalse();
    }

    @Test
    void shouldRejectMissingOrForeignReviewAuthorityInsteadOfInventingAnOrigin() {
        var row = result(now);
        assertThatThrownBy(() -> reader.isBackfillOnly(1, List.of(row))).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> reader.isBackfillOnly(2, List.of(row))).isInstanceOf(IllegalArgumentException.class);
    }
}
