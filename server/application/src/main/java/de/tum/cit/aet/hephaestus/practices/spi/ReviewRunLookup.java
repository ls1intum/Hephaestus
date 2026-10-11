package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOutcomeLookup.ReviewRunState;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * What a review run knows about itself, read without knowing about agent-job persistence: the work it reviewed
 * and the facts it records. {@link ReviewRunNarrativeLookup} does the same for what a run wrote about each
 * observation. Both lookups answer by review id, and a run this workspace does not own is absent.
 */
public interface ReviewRunLookup {

    /** The work each run reviewed, named by what the mirror calls it now while the mirror still holds it. */
    Map<UUID, Target> findTargets(long workspaceId, Collection<UUID> jobIds);

    Map<UUID, ReviewRunFacts> findFacts(long workspaceId, Collection<UUID> jobIds);

    /**
     * @param container   where the work sits, by name: a pull request's or issue's repository, a document's
     *                    collection. A conversation's channel is its label, so it is {@code channelName}
     */
    record Target(
            @NonNull ArtifactKind type,
            @Nullable Long id,
            @Nullable IntegrationKind provider,
            @Nullable Integer number,
            @NonNull String title,
            @Nullable String container,
            @Nullable String channelName,
            @Nullable String url) {
        public Target withTitle(String title) {
            return new Target(type, id, provider, number, title, container, channelName, url);
        }
    }

    /**
     * @param target the work the run reviewed, as {@link #findTargets} names it
     * @param practicesEvaluated how many practices the run measured, or null when it wrote no coverage ledger
     * @param feedbackUrl the run's summary comment on the work's own page, or null when it posted none or the
     *     provider returned no address on that work
     * @param createdAt when the run was asked for
     * @param model the language model the run used, or null when it recorded none
     * @param completedAt when the run finished, or null while it runs
     * @param phase where the run is or how it ended, finer than {@code status}: a timeout and a cancellation apart
     * @param startedAt when a worker picked the run up, or null while it waits
     * @param holdReason why a queued run waits, where an admin can undo it, such as {@code BUDGET}; null otherwise
     * @param conclusion why a completed run recorded what it did; null until the run completed
     */
    record ReviewRunFacts(
            Target target,
            ReviewRunState status,
            TriggerMode triggerMode,
            @Nullable Integer practicesEvaluated,
            @Nullable String feedbackUrl,
            Instant createdAt,
            @Nullable String model,
            @Nullable Instant completedAt,
            ReviewRunPhase phase,
            @Nullable Instant startedAt,
            @Nullable String holdReason,
            @Nullable ReviewRunConclusion conclusion) {}

    /**
     * Why a completed run recorded the observations it did. Status alone cannot tell a run that assessed the work
     * and recorded nothing from one that assessed nothing at all.
     */
    enum ReviewRunConclusion {
        /** The model ran against sufficient evidence; no observation means it recorded none. */
        REVIEWED,
        /** Required evidence could not be read, so no model ran and no practice was assessed. */
        INSUFFICIENT_EVIDENCE,
        /** A completed review had already answered every ready practice on the same code, so no model ran. */
        COALESCED,
        /** A newer admitted review of the same work replaced it while it waited, so no model ran. */
        SUPERSEDED,
    }

    /** Where a run is, or how it ended. */
    enum ReviewRunPhase {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        TIMED_OUT,
        CANCELLED,
    }
}
