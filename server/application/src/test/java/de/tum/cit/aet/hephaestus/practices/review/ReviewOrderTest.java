package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@NullMarked
@Tag("unit")
class ReviewOrderTest {

    private static final Instant AT = Instant.parse("2026-10-14T12:00:00Z");
    private static final UUID LOW = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
    private static final UUID HIGH = UUID.fromString("80000000-0000-0000-0000-000000000000");

    @Test
    void shouldCompareUuidTextWhenBothTimesMatch() {
        assertThat(new ReviewOrder(AT, AT, HIGH)).isGreaterThan(new ReviewOrder(AT, AT, LOW));
    }

    @Test
    void shouldUseReviewOrderBeforeRecordingTime() {
        assertThat(new ReviewOrder(AT.plusSeconds(1), AT, LOW))
                .isGreaterThan(new ReviewOrder(AT, AT.plusSeconds(2), HIGH));
    }

    @Test
    void shouldUseRecordingTimeBeforeReviewId() {
        assertThat(new ReviewOrder(AT, AT.plusSeconds(1), LOW)).isGreaterThan(new ReviewOrder(AT, AT, HIGH));
    }
}
