package de.tum.cit.aet.hephaestus.practices.review;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReviewOrder(Instant orderedAt, Instant recordedAt, UUID reviewId) implements Comparable<ReviewOrder> {
    public ReviewOrder {
        Objects.requireNonNull(orderedAt);
        Objects.requireNonNull(recordedAt);
        Objects.requireNonNull(reviewId);
    }

    @Override
    public int compareTo(ReviewOrder other) {
        int byOrder = orderedAt.compareTo(other.orderedAt);
        if (byOrder != 0) {
            return byOrder;
        }
        int byRecording = recordedAt.compareTo(other.recordedAt);
        return byRecording != 0 ? byRecording : reviewId.toString().compareTo(other.reviewId.toString());
    }
}
