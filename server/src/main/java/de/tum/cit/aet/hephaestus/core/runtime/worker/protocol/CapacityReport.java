package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;

/**
 * Periodic worker → hub capacity announcement. {@code spareX} is precomputed
 * ({@code = max(0, xMax - inFlightX)}) so the dispatcher's selection path stays arithmetic-free.
 */
public record CapacityReport(
    int reviewMax,
    int mentorMax,
    int inFlightReview,
    int inFlightMentor,
    int spareReview,
    int spareMentor
) implements WorkerControlFrame {
    public CapacityReport {
        if (
            reviewMax < 0 ||
            mentorMax < 0 ||
            inFlightReview < 0 ||
            inFlightMentor < 0 ||
            spareReview < 0 ||
            spareMentor < 0
        ) {
            throw new IllegalArgumentException("CapacityReport fields must be non-negative");
        }
    }
}
