package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;
import java.util.Set;

/** Versioned semantics for one logical evidence source. */
public record ArtifactSourceContract(
    SourceKind kind,
    String displayName,
    String description,
    String selectionScope,
    Set<String> artifactTypes,
    SourceAuthority authority,
    CaptureTimeBasis captureTimeBasis,
    FreshnessPolicy freshnessPolicy,
    CompletenessPolicy completenessPolicy,
    PrivacyClass privacyClass,
    Set<SourceAbsenceState> supportedAbsenceStates,
    RetentionPolicy retentionPolicy,
    ErasurePolicy erasurePolicy,
    Set<String> useDecisionIds
) {
    public ArtifactSourceContract {
        Objects.requireNonNull(kind, "kind");
        displayName = requireText(displayName, "displayName", kind);
        description = requireText(description, "description", kind);
        selectionScope = requireText(selectionScope, "selectionScope", kind);
        artifactTypes = Set.copyOf(Objects.requireNonNull(artifactTypes, "artifactTypes"));
        if (artifactTypes.isEmpty() || artifactTypes.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("artifactTypes must contain non-blank values: " + kind);
        }
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(captureTimeBasis, "captureTimeBasis");
        Objects.requireNonNull(freshnessPolicy, "freshnessPolicy");
        Objects.requireNonNull(completenessPolicy, "completenessPolicy");
        Objects.requireNonNull(privacyClass, "privacyClass");
        supportedAbsenceStates = Set.copyOf(Objects.requireNonNull(supportedAbsenceStates, "supportedAbsenceStates"));
        if (supportedAbsenceStates.isEmpty()) {
            throw new IllegalArgumentException("supportedAbsenceStates must not be empty: " + kind);
        }
        Objects.requireNonNull(retentionPolicy, "retentionPolicy");
        Objects.requireNonNull(erasurePolicy, "erasurePolicy");
        useDecisionIds = Set.copyOf(Objects.requireNonNull(useDecisionIds, "useDecisionIds"));
        if (useDecisionIds.isEmpty() || useDecisionIds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("useDecisionIds must contain non-blank values: " + kind);
        }
    }

    public boolean appliesTo(String artifactType) {
        return artifactTypes.contains(artifactType);
    }

    private static String requireText(String value, String field, SourceKind kind) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank: " + kind);
        }
        return value;
    }
}
