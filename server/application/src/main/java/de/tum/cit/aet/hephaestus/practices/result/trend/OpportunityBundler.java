package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.result.ResultView;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OpportunityBundler {
    private OpportunityBundler() {}

    static Bundles bundle(List<ResultView> results, Instant cutoff, int bundleSize) {
        var all = opportunities(results, cutoff);
        var decided = all.stream().filter(EvidenceOpportunity::decided).toList();
        var current = tagged(decided.stream().limit(bundleSize).toList(), TrendBundle.CURRENT);
        var previous =
                tagged(decided.stream().skip(bundleSize).limit(bundleSize).toList(), TrendBundle.PREVIOUS);
        Map<Long, TrendBundle> bundles = new LinkedHashMap<>();
        current.forEach(o -> bundles.put(o.key(), TrendBundle.CURRENT));
        previous.forEach(o -> bundles.put(o.key(), TrendBundle.PREVIOUS));
        var trail = all.stream()
                .map(o -> o.withBundle(bundles.getOrDefault(o.key(), TrendBundle.OLDER)))
                .sorted(Comparator.comparing(EvidenceOpportunity::occurredAt))
                .toList();
        return new Bundles(current, previous, trail);
    }

    static List<EvidenceOpportunity> opportunities(List<ResultView> results, Instant cutoff) {
        return opportunities(results, cutoff, false);
    }

    static List<EvidenceOpportunity> opportunities(List<ResultView> results, Instant cutoff, boolean selfSelected) {
        Map<Long, List<ResultView>> byItem = new LinkedHashMap<>();
        results.stream()
                .filter(v -> v.result().getEndedAt() == null && v.result().getEvidenceVersion() == 1)
                .filter(v -> (v.review().getOrigin() == ReviewOrigin.REQUEST
                                || v.review().getOrigin() == ReviewOrigin.BACKFILL)
                        == selfSelected)
                .filter(v -> !v.result().getRecordedAt().isBefore(cutoff))
                .forEach(v -> byItem.computeIfAbsent(v.result().getItemId(), id -> new ArrayList<>())
                        .add(v));
        return byItem.entrySet().stream()
                .map(e -> {
                    var latest = e.getValue().stream()
                            .max(Comparator.comparing(v -> v.result().getRecordedAt()))
                            .orElseThrow();
                    var outcomes = e.getValue().stream()
                            .map(v -> OutcomeVector.of(v.result().getOutcome()))
                            .reduce(OutcomeVector.EMPTY, OutcomeVector::plus);
                    return new EvidenceOpportunity(
                            new ItemKind(latest.item().kind()),
                            e.getKey(),
                            latest.review().getId(),
                            latest.result().getRecordedAt(),
                            outcomes,
                            TrendBundle.OLDER);
                })
                .sorted(Comparator.comparing(EvidenceOpportunity::occurredAt)
                        .thenComparingLong(EvidenceOpportunity::itemId)
                        .reversed())
                .toList();
    }

    static List<EvidenceOpportunity> cappedTrail(List<EvidenceOpportunity> trail, int bundleSize) {
        int cap = 2 * bundleSize + 4, decided = 0;
        for (int i = trail.size() - 1; i >= 0; i--) {
            if (trail.get(i).decided()) decided++;
            if (decided == cap) return trail.subList(i, trail.size());
        }
        return trail;
    }

    private static List<EvidenceOpportunity> tagged(List<EvidenceOpportunity> opportunities, TrendBundle bundle) {
        return opportunities.stream().map(o -> o.withBundle(bundle)).toList();
    }

    record Bundles(
            List<EvidenceOpportunity> current, List<EvidenceOpportunity> previous, List<EvidenceOpportunity> trail) {
        int opportunitiesUntilComparable(int bundleSize, int minimumBundleSize) {
            return Math.max(0, bundleSize - current.size()) + Math.max(0, minimumBundleSize - previous.size());
        }
    }
}
