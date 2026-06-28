package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Capability-gated SPI for posting inline findings (SCM diff notes, knowledge-base
 * document-anchor comments). Kinds that don't declare {@link Capability#INLINE_FINDINGS}
 * never resolve via this registry — Slack and similar messaging vendors are
 * compile-time excluded.
 */
public interface InlineFindingChannel {
    IntegrationKind kind();

    InlineResult postInlineFindings(FeedbackChannel.FeedbackTarget target, List<InlineFinding> findings);

    /**
     * Removes this run's previously-posted inline findings (matched by {@code marker}) WITHOUT posting new
     * ones — the clear half of clear-then-post, callable on a zero-note re-run so stale notes from an earlier
     * run never survive a review that now finds nothing inline (the empty-diff pathology where a re-reviewed
     * PR keeps line-numbered notes on code no longer in the diff).
     *
     * <p>Default is a no-op for a vendor that offers no reconciliation at all. Both shipped channels override
     * it: GitLab deletes every marker-bearing note; GitHub (whose threads cannot be deleted, only minimized)
     * minimizes each vanished thread as {@code OUTDATED}. "Append-only" therefore does NOT mean "no-op" here.
     */
    default void clearStaleFindings(FeedbackChannel.FeedbackTarget target, String marker) {
        // no-op — see Javadoc: only deletable-note vendors override.
    }

    /**
     * One finding to post inline. {@code recurrenceKey} carries the stable
     * {@link de.tum.cit.aet.hephaestus.practices.observation.ObservationFingerprint} identity so a delivery can be matched
     * back to its placement across re-runs; it is {@code null} when the caller has no key for the finding. The
     * 3-arg constructor is the pre-correlation compatibility shape and defaults the key to null.
     */
    record InlineFinding(FindingAnchor anchor, String body, String marker, @Nullable String recurrenceKey) {
        public InlineFinding(FindingAnchor anchor, String body, String marker) {
            this(anchor, body, marker, null);
        }
    }

    /**
     * Per-finding outcome of a delivery attempt, reported in {@link DeliveredSignal} so the placement layer can
     * persist {@code posted_state} / {@code external_ref} without re-deriving it.
     *
     * <ul>
     *   <li>{@code POSTED} — a new inline note/thread was created.
     *   <li>{@code FELL_BACK} — the anchor was out of the diff hunk, posted as a plain comment instead.
     *   <li>{@code PRESERVED_EXISTING} — an equivalent note already exists (e.g. a human-replied thread) and was
     *       intentionally left untouched rather than re-posted.
     *   <li>{@code FAILED} — the note could not be delivered (unsupported anchor, API error, rate limit).
     * </ul>
     */
    enum Disposition {
        POSTED,
        FELL_BACK,
        PRESERVED_EXISTING,
        FAILED,
    }

    /**
     * What actually happened to one finding, keyed by {@code recurrenceKey} so the caller can reconcile it
     * against the persisted placement. {@code externalRef} is the vendor note id and {@code threadExternalRef}
     * the enclosing discussion/thread id; both are {@code null} when no durable handle exists (e.g. a failure).
     */
    record DeliveredSignal(
        @Nullable String recurrenceKey,
        FindingAnchor anchor,
        Disposition disposition,
        @Nullable String externalRef,
        @Nullable String threadExternalRef
    ) {}

    /**
     * Aggregate delivery result. {@code signals} carries the per-finding {@link DeliveredSignal}s the placement
     * layer persists; a path with no per-finding outcomes (rate-limit short-circuit, empty input) reports via
     * {@link #counts}, which leaves {@code signals} empty.
     *
     * <p>Counting invariant (both shipped impls): {@code posted} counts every signal whose {@link Disposition}
     * is not {@code FAILED} (i.e. {@code POSTED}, {@code FELL_BACK}, {@code PRESERVED_EXISTING}); {@code failed}
     * counts {@code FAILED} signals. Every finding that produced a per-finding outcome contributes exactly one
     * signal, so {@code posted + failed == signals.size()} across the signalled findings. A finding the channel
     * skips outright (e.g. a blank body the GitHub impl drops without posting) yields no signal and no count, so
     * it is simply absent from both sides rather than violating the equality.
     */
    record InlineResult(int posted, int failed, List<DeliveredSignal> signals) {
        /** Count-only result with no per-finding signals (rate-limit short-circuit / empty input). */
        public static InlineResult counts(int posted, int failed) {
            return new InlineResult(posted, failed, List.of());
        }
    }
}
