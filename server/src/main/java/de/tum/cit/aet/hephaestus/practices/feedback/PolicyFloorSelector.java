package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The per-run volume cap (ADR 0021, C3): keep every blocking finding (CRITICAL/MAJOR) plus the top-K
 * non-blocking (MINOR/INFO) ones; the rest are "dropped" — surfaced to no one this run so the student is not
 * flooded into inaction. Pure and deterministic. The ledger records each dropped finding as SUPPRESSED so an
 * eval excludes it (a dropped finding is model-correct, just policy-withheld — NOT a model miss). Mirrors the
 * same keep-blocking-plus-top-K ordering/cap parameters as {@code DeliveryComposer}'s improvement-tail cap
 * (it does not re-use the exact same input set — it caps the persisted Observations, not the MR-note tail).
 */
public final class PolicyFloorSelector {

    private PolicyFloorSelector() {}

    /** kept = surfaced this run; dropped = withheld by the volume cap (recorded SUPPRESSED, not delivered). */
    public record Partition(List<Observation> kept, List<Observation> dropped) {}

    /**
     * Partition problem observations (assessment = BAD) into kept vs dropped. {@code topK} bounds the
     * non-blocking tail; {@code topK <= 0} disables capping (everything kept).
     */
    public static Partition partition(List<Observation> problemFindings, int topK) {
        List<Observation> kept = new ArrayList<>();
        List<Observation> dropped = new ArrayList<>();
        List<Observation> nonBlocking = new ArrayList<>();
        for (Observation f : problemFindings) {
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
            Comparator.comparingInt((Observation f) -> f.getSeverity().ordinal())
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

    private static float confidence(Observation f) {
        return f.getConfidence() == null ? 0f : f.getConfidence();
    }
}
