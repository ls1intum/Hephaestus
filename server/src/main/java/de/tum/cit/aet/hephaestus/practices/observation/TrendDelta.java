package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The cross-run change at a review target (ADR 0021, A1) — for each stable {@code recurrence_key} locus,
 * how it moved between the prior review run and the current one. This is the measurement primitive the
 * research question ("do practices change over time?") reads, and the substrate the delivery layer renders
 * as a progress-delta footer (B3) and "Resolved since last review" lines (B1), and that the re-review
 * notification (A4) consults to decide whether anything actually changed.
 *
 * <p>Carries NO rendered prose — rendering belongs to {@code DeliveryComposer}. A locus is identified by
 * its {@code recurrence_key} (the (practice, target, subject, file) locus), which is
 * stable across the non-deterministic detector, so "the same concern recurring" is observable even when the
 * LLM re-words its title every run.
 */
public record TrendDelta(
    WorkArtifact artifactType,
    @Nullable Long artifactId,
    UUID currentRunJobId,
    UUID priorRunJobId,
    Instant currentRunAt,
    Instant priorRunAt,
    List<LocusTransition> transitions
) {
    public TrendDelta {
        transitions = List.copyOf(transitions); // the measurement primitive is immutable
    }

    /** How a single locus moved between the prior run and the current one. */
    public enum TransitionStatus {
        /** Present this run, absent the prior run. */
        NEW,
        /** Present in both runs (still recurring — not necessarily unfixed; see {@link LocusTransition#currentAssessment}). */
        PERSISTED,
        /** Present the prior run, absent this run — the concern is gone (positive reinforcement of the act of fixing). */
        RESOLVED,
        /** Was a strength ({@code GOOD}) the prior run, now a problem ({@code BAD}) — a backslide (ADR 0022). */
        REGRESSED,
    }

    /**
     * One locus's movement. {@code title}/{@code currentSeverity}/{@code currentConfidence} come from the
     * CURRENT run for {@link TransitionStatus#NEW}/{@link TransitionStatus#PERSISTED}/{@link TransitionStatus#REGRESSED},
     * and from the PRIOR run for {@link TransitionStatus#RESOLVED} (the locus is absent now, so the prior
     * prose is what the student last saw). {@code currentAssessment} is null for RESOLVED; {@code priorAssessment}
     * is null for NEW.
     */
    public record LocusTransition(
        String findingFingerprint,
        TransitionStatus status,
        String practiceSlug,
        @Nullable String title,
        @Nullable Assessment priorAssessment,
        @Nullable Assessment currentAssessment,
        @Nullable Severity currentSeverity,
        @Nullable Float currentConfidence
    ) {}

    public int countNew() {
        return (int) transitions
            .stream()
            .filter(t -> t.status() == TransitionStatus.NEW)
            .count();
    }

    public int countPersisted() {
        return (int) transitions
            .stream()
            .filter(t -> t.status() == TransitionStatus.PERSISTED)
            .count();
    }

    public int countResolved() {
        return (int) transitions
            .stream()
            .filter(t -> t.status() == TransitionStatus.RESOLVED)
            .count();
    }

    public int countRegressed() {
        return (int) transitions
            .stream()
            .filter(t -> t.status() == TransitionStatus.REGRESSED)
            .count();
    }

    /** The resolved loci, for the "Resolved since last review ✓" lines (B1). */
    public List<LocusTransition> resolved() {
        return transitions
            .stream()
            .filter(t -> t.status() == TransitionStatus.RESOLVED)
            .toList();
    }

    /** The slipped-back loci, for the "Slipped back" lines (B1). */
    public List<LocusTransition> regressed() {
        return transitions
            .stream()
            .filter(t -> t.status() == TransitionStatus.REGRESSED)
            .toList();
    }

    public boolean isEmptyDelta() {
        return transitions.isEmpty();
    }

    /**
     * Whether the finding set actually moved (something appeared, was fixed, or backslid). A re-review that
     * only re-flags the exact same loci is NOT meaningful — A4 stays silent so an edit-in-place re-review
     * does not ping the author about nothing.
     */
    public boolean hasMeaningfulChange() {
        return countNew() + countResolved() + countRegressed() > 0;
    }
}
