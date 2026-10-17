package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;

final class ReviewBatchOrigins {
    private ReviewBatchOrigins() {}

    static ReviewOrigin origin(ReviewBatch batch) {
        return batch.getOrigin() == ReviewBatchOrigin.SCHEDULE ? ReviewOrigin.SCHEDULE : ReviewOrigin.BACKFILL;
    }
}
