package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record EvidenceContribution(
    Map<String, byte[]> files,
    Map<SourceKind, SourceCompleteness> completeness,
    Map<SourceKind, String> immutableIdentities,
    Map<SourceKind, Instant> observedAt,
    Map<SourceKind, Instant> sourceEffectiveAt,
    Map<SourceKind, SourceContentState> contentStates
) {
    public EvidenceContribution(Map<String, byte[]> files, Map<SourceKind, SourceCompleteness> completeness) {
        this(files, completeness, Map.of(), Map.of(), Map.of(), Map.of());
    }

    public EvidenceContribution(
        Map<String, byte[]> files,
        Map<SourceKind, SourceCompleteness> completeness,
        Map<SourceKind, String> immutableIdentities
    ) {
        this(files, completeness, immutableIdentities, Map.of(), Map.of(), Map.of());
    }

    public EvidenceContribution(
        Map<String, byte[]> files,
        Map<SourceKind, SourceCompleteness> completeness,
        Map<SourceKind, String> immutableIdentities,
        Map<SourceKind, Instant> observedAt,
        Map<SourceKind, Instant> sourceEffectiveAt
    ) {
        this(files, completeness, immutableIdentities, observedAt, sourceEffectiveAt, Map.of());
    }

    public EvidenceContribution {
        files = Map.copyOf(Objects.requireNonNull(files, "files"));
        completeness = Map.copyOf(Objects.requireNonNull(completeness, "completeness"));
        immutableIdentities = Map.copyOf(Objects.requireNonNull(immutableIdentities, "immutableIdentities"));
        observedAt = Map.copyOf(Objects.requireNonNull(observedAt, "observedAt"));
        sourceEffectiveAt = Map.copyOf(Objects.requireNonNull(sourceEffectiveAt, "sourceEffectiveAt"));
        contentStates = Map.copyOf(Objects.requireNonNull(contentStates, "contentStates"));
    }
}
