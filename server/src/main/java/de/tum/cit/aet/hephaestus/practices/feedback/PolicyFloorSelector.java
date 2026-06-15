package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The per-run volume cap (ADR 0021, C3): keep every blocking finding (CRITICAL/MAJOR) plus the top-K
 * non-blocking (MINOR/INFO) ones; the rest are "dropped" — surfaced to no one this run so the student is not
 * flooded into inaction. Pure and deterministic. The ledger records each dropped finding as SUPPRESSED so an
 * eval excludes it (a dropped finding is model-correct, just policy-withheld — NOT a model miss). Applies the
 * same keep-blocking-plus-top-K policy as {@code DeliveryComposer}'s improvement-tail cap.
 */
public final class PolicyFloorSelector {

    private PolicyFloorSelector() {}

    /** kept = surfaced this run; dropped = withheld by the volume cap (recorded SUPPRESSED, not delivered). */
    public record Partition(List<PracticeFinding> kept, List<PracticeFinding> dropped) {}

    /**
     * Partition problem findings (NOT_OBSERVED) into kept vs dropped. {@code topK} bounds the non-blocking
     * tail; {@code topK <= 0} disables capping (everything kept).
     */
    public static Partition partition(List<PracticeFinding> problemFindings, int topK) {
        List<PracticeFinding> kept = new ArrayList<>();
        List<PracticeFinding> dropped = new ArrayList<>();
        List<PracticeFinding> nonBlocking = new ArrayList<>();
        for (PracticeFinding f : problemFindings) {
            if (isBlocking(f.getSeverity())) {
                kept.add(f);
            } else {
                nonBlocking.add(f);
            }
        }
        if (topK <= 0) {
            kept.addAll(nonBlocking);
            return new Partition(kept, dropped);
        }
        nonBlocking.sort(
            Comparator.comparingInt((PracticeFinding f) -> f.getSeverity().ordinal())
                .thenComparing(Comparator.comparing(PolicyFloorSelector::confidence).reversed())
                .thenComparing(f -> f.getId().toString())
        );
        for (int i = 0; i < nonBlocking.size(); i++) {
            (i < topK ? kept : dropped).add(nonBlocking.get(i));
        }
        return new Partition(kept, dropped);
    }

    private static boolean isBlocking(Severity s) {
        return s == Severity.CRITICAL || s == Severity.MAJOR;
    }

    private static float confidence(PracticeFinding f) {
        return f.getConfidence() == null ? 0f : f.getConfidence();
    }
}
