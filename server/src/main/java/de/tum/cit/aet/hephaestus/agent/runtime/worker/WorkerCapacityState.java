package de.tum.cit.aet.hephaestus.agent.runtime.worker;

import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.CapacityReport;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-worker in-flight counts for review jobs and mentor sessions. Read by the capacity
 * reporter, drain coordinator, and {@code AgentJobExecutor}; resolved maxes come from
 * {@link WorkerProperties}.
 */
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
        decrementIfPositive(inFlightReview, "review");
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
        decrementIfPositive(inFlightMentor, "mentor");
    }

    public CapacityReport snapshot() {
        int review = inFlightReview.get();
        int mentor = inFlightMentor.get();
        return new CapacityReport(
            reviewMax,
            mentorMax,
            review,
            mentor,
            Math.max(0, reviewMax - review),
            Math.max(0, mentorMax - mentor)
        );
    }

    public int reviewMax() {
        return reviewMax;
    }

    public int mentorMax() {
        return mentorMax;
    }

    /**
     * Idempotent: returns when the counter is already at 0 instead of throwing, because release
     * paths fire from defensive code (e.g. {@code AgentJobExecutor.releaseCapacity}) that can't
     * tolerate an exception, and the early-shutdown / double-release window is real but harmless.
     */
    private static void decrementIfPositive(AtomicInteger counter, String label) {
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
