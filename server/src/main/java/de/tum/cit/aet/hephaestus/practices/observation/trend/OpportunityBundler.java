package de.tum.cit.aet.hephaestus.practices.observation.trend;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Converts raw findings into opportunity-indexed, non-overlapping comparison bundles. */
final class OpportunityBundler {

    private OpportunityBundler() {}

    static Bundles bundle(String practiceSlug, List<Observation> observations, Instant cutoff, int bundleSize) {
        Map<ArtifactKey, List<Observation>> byArtifact = new LinkedHashMap<>();
        observations
            .stream()
            .filter(observation -> !observation.getObservedAt().isBefore(cutoff))
            .forEach(observation ->
                byArtifact
                    .computeIfAbsent(
                        new ArtifactKey(observation.getArtifactKind(), observation.getArtifactId()),
                        ignored -> new ArrayList<>()
                    )
                    .add(observation)
            );

        List<EvidenceOpportunity> all = byArtifact
            .entrySet()
            .stream()
            .map(entry -> latestRunOpportunity(practiceSlug, entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(EvidenceOpportunity::occurredAt).reversed())
            .toList();
        List<EvidenceOpportunity> applicable = all.stream().filter(EvidenceOpportunity::applicable).toList();
        List<EvidenceOpportunity> current = tagged(applicable.stream().limit(bundleSize).toList(), TrendBundle.CURRENT);
        List<EvidenceOpportunity> previous = tagged(
            applicable.stream().skip(bundleSize).limit(bundleSize).toList(),
            TrendBundle.PREVIOUS
        );
        Map<ArtifactKey, TrendBundle> bundleByArtifact = new LinkedHashMap<>();
        current.forEach(opportunity -> bundleByArtifact.put(ArtifactKey.of(opportunity), TrendBundle.CURRENT));
        previous.forEach(opportunity -> bundleByArtifact.put(ArtifactKey.of(opportunity), TrendBundle.PREVIOUS));
        List<EvidenceOpportunity> trail = all
            .stream()
            .map(opportunity ->
                opportunity.withBundle(bundleByArtifact.getOrDefault(ArtifactKey.of(opportunity), TrendBundle.OLDER))
            )
            .sorted(Comparator.comparing(EvidenceOpportunity::occurredAt))
            .toList();
        return new Bundles(current, previous, trail);
    }

    private static EvidenceOpportunity latestRunOpportunity(
        String practiceSlug,
        ArtifactKey artifact,
        List<Observation> observations
    ) {
        UUID latestJob = observations
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    Observation::getAgentJobId,
                    Observation::getObservedAt,
                    (left, right) -> left.isAfter(right) ? left : right
                )
            )
            .entrySet()
            .stream()
            .max(Map.Entry.comparingByValue())
            .orElseThrow()
            .getKey();
        List<Observation> latest = observations
            .stream()
            .filter(row -> latestJob.equals(row.getAgentJobId()))
            .toList();
        OutcomeVector outcomes = latest
            .stream()
            .map(row -> OutcomeVector.of(row.getPresence(), row.getAssessment()))
            .reduce(OutcomeVector.EMPTY, OutcomeVector::plus);
        Instant occurredAt = latest.stream().map(Observation::getObservedAt).max(Instant::compareTo).orElseThrow();
        return new EvidenceOpportunity(
            practiceSlug,
            artifact.type(),
            artifact.id(),
            occurredAt,
            outcomes,
            TrendBundle.OLDER
        );
    }

    private static List<EvidenceOpportunity> tagged(List<EvidenceOpportunity> opportunities, TrendBundle bundle) {
        return opportunities
            .stream()
            .map(opportunity -> opportunity.withBundle(bundle))
            .toList();
    }

    record Bundles(
        List<EvidenceOpportunity> current,
        List<EvidenceOpportunity> previous,
        List<EvidenceOpportunity> trail
    ) {
        int opportunitiesUntilComparable(int minimumBundleSize) {
            return Math.max(0, minimumBundleSize - previous.size());
        }
    }

    private record ArtifactKey(ArtifactKind type, long id) {
        static ArtifactKey of(EvidenceOpportunity opportunity) {
            return new ArtifactKey(opportunity.artifactKind(), opportunity.artifactId());
        }
    }
}
