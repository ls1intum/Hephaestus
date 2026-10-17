package de.tum.cit.aet.hephaestus.agent.runtime.worker;

import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.CapacityReport;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorkerCapacityState {

    private final AtomicInteger inFlightReview = new AtomicInteger();
    private final AtomicInteger inFlightMentor = new AtomicInteger();
    private final int reviewMax;
    private final int mentorMax;

    public WorkerCapacityState(WorkerProperties properties) {
        this.reviewMax = properties.capacity().resolveReviewMax();
        this.mentorMax = properties.capacity().resolveMentorMax();
    }

    public void claimReview() {
        inFlightReview.incrementAndGet();
    }

    public void releaseReview() {
        decrementIfPositive(inFlightReview);
    }

    /** Atomic; returns {@code false} when at capacity (caller rejects the session). */
    public boolean tryClaimMentor() {
        while (true) {
            int current = inFlightMentor.get();
            if (current >= mentorMax) {
                return false;
            }
            if (inFlightMentor.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    public void releaseMentor() {
        decrementIfPositive(inFlightMentor);
    }

    public CapacityReport snapshot() {
        int review = inFlightReview.get();
        int mentor = inFlightMentor.get();
        return new CapacityReport(
                reviewMax, mentorMax, review, mentor, Math.max(0, reviewMax - review), Math.max(0, mentorMax - mentor));
    }

    public int reviewMax() {
        return reviewMax;
    }

    public int mentorMax() {
        return mentorMax;
    }

    // Capacity release is idempotent and must not throw during shutdown.
    private static void decrementIfPositive(AtomicInteger counter) {
        while (true) {
            int current = counter.get();
            if (current <= 0) {
                return;
            }
            if (counter.compareAndSet(current, current - 1)) {
                return;
            }
        }
    }
}
