package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackQueryRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PreviousPracticePageFeedback;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

@Tag("unit")
class PracticePageSupportReaderTest {
    private final ResultRepository results = mock(ResultRepository.class);
    private final PreviousPracticePageFeedback previous = mock(PreviousPracticePageFeedback.class);
    private final PracticeRepository practices = mock(PracticeRepository.class);
    private final FeedbackQueryRepository queries = mock(FeedbackQueryRepository.class);
    private final Instant now = Instant.parse("2026-10-14T00:00:00Z");
    private final PracticePageSupportReader reader =
            new PracticePageSupportReader(results, Clock.fixed(now, ZoneOffset.UTC), previous, practices, queries);
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
    void shouldSelectOnlyNewSupportWithinTheLookbackAndPreviousCardWindow() {
        var recent = result(now.minus(Duration.ofDays(1)));
        var older = result(now.minus(Duration.ofDays(2)));
        var expired = result(now.minus(Duration.ofDays(91)));
        var foreignPractice =
                recent.toBuilder().id(UUID.randomUUID()).practiceId(6).build();
        var archived = recent.toBuilder()
                .id(UUID.randomUUID())
                .evidenceVersion((short) 0)
                .build();
        var met = recent.toBuilder()
                .id(UUID.randomUUID())
                .outcome(ResultOutcome.MET)
                .build();
        when(results.findCurrentForDeveloper(eq(1L), eq(7L), eq(ResultMarkKind.INCORRECT), any()))
                .thenReturn(new PageImpl<>(List.of(recent, older, expired, foreignPractice, archived, met)));
        var practice = new Practice();
        practice.setSlug("focused-tests");
        when(practices.findByIdAndWorkspaceId(5L, 1L)).thenReturn(Optional.of(practice));
        when(previous.find(1, 7, "focused-tests", now)).thenReturn(Optional.empty());
        assertThat(reader.select(1, 7, 5, now).evidence()).containsExactly(recent, older);
        var prior = new PreviousPracticePageFeedback.Previous(UUID.randomUUID(), older.getRecordedAt(), null, false);
        when(previous.find(1, 7, "focused-tests", now)).thenReturn(Optional.of(prior));
        assertThat(reader.select(1, 7, 5, now).evidence()).containsExactly(recent);
    }

    @Test
    void shouldHoldOnlySupportWhoseDistinctReviewsAreAllBackfill() {
        var first = result(now);
        var second = first.toBuilder().id(UUID.randomUUID()).build();
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
