package de.tum.cit.aet.hephaestus.practices.review.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class ReviewBatchOriginsTest extends BaseUnitTest {
    @Test
    void backfillIsAnOriginWithoutAnEvent() {
        assertThat(ReviewBatchOrigins.origin(
                        ReviewBatch.builder().origin(ReviewBatchOrigin.BACKFILL).build()))
                .isEqualTo(ReviewOrigin.BACKFILL);
    }

    @Test
    void scheduleIsAnOriginWithoutAnEvent() {
        assertThat(ReviewBatchOrigins.origin(
                        ReviewBatch.builder().origin(ReviewBatchOrigin.SCHEDULE).build()))
                .isEqualTo(ReviewOrigin.SCHEDULE);
    }
}
