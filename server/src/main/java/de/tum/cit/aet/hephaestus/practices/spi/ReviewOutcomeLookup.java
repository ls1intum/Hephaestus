package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * What a review run decided, for a surface that has only its id.
 *
 * <p>A port for the same reason {@link ReviewRunTargetLookup} is one: the practices module owns the
 * question and must not import the orchestrator that answers it. The orchestrator implements this
 * against {@code agent_job}, parsing the readiness report beside the code that writes it.
 */
public interface ReviewOutcomeLookup {
    /** @return outcomes by review id; a run this workspace does not own is simply absent */
    Map<UUID, ReviewOutcome> findByIds(long workspaceId, Collection<UUID> reviewIds);

    /**
     * @param insufficientEvidence the run completed without any model executing because the evidence it
     *                             needed was not readable. Distinct from failure: nothing broke
     * @param readinessByPracticeSlug what the run decided about each practice it <em>considered</em>. A
     *                             practice absent here was never considered, which is a different fact
     *                             from having been considered and refused, and the only record of it
     */
    record ReviewOutcome(
        @NonNull ReviewRunState state,
        boolean insufficientEvidence,
        @Nullable Instant decidedAt,
        @NonNull Map<String, PracticeReadinessOutcome> readinessByPracticeSlug
    ) {}

    /**
     * @param blockers already-rendered phrases naming what could not be read, so no consumer has to
     *                 learn the evidence vocabulary to explain a refusal
     */
    record PracticeReadinessOutcome(boolean ready, @NonNull List<String> blockers) {}

    /**
     * Deliberately coarser than the orchestrator's job status: a trace has nothing different to say
     * about a run that timed out and one that was cancelled, and a distinction nobody renders drifts.
     */
    enum ReviewRunState {
        IN_PROGRESS,
        COMPLETED,
        FAILED,
    }
}
