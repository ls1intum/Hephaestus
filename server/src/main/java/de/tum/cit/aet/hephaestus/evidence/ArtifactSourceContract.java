package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;
import java.util.Set;

/** Contract for one independently missing, fresh, complete, private, and ablatable source kind. */
public record ArtifactSourceContract(
    SourceKind kind,
    Set<String> artifactTypes,
    SourceAuthority authority,
    CaptureTimeBasis captureTime,
    FreshnessPolicy freshnessPolicy,
    CompletenessPolicy completenessPolicy,
    PrivacyClass privacyClass,
    Set<MissingnessKind> supportedMissingness,
    String purpose,
    String retentionPolicy,
    String erasurePolicy,
    String useDecisionId
) {
    public ArtifactSourceContract {
        Objects.requireNonNull(kind, "kind");
        artifactTypes = Set.copyOf(Objects.requireNonNull(artifactTypes, "artifactTypes"));
        if (artifactTypes.isEmpty() || artifactTypes.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("artifactTypes must contain non-blank values: " + kind);
        }
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(captureTime, "captureTime");
        Objects.requireNonNull(freshnessPolicy, "freshnessPolicy");
        Objects.requireNonNull(completenessPolicy, "completenessPolicy");
        Objects.requireNonNull(privacyClass, "privacyClass");
        supportedMissingness = Set.copyOf(Objects.requireNonNull(supportedMissingness, "supportedMissingness"));
        if (supportedMissingness.isEmpty()) {
            throw new IllegalArgumentException("supportedMissingness must not be empty: " + kind);
        }
        purpose = requireText(purpose, "purpose", kind);
        retentionPolicy = requireText(retentionPolicy, "retentionPolicy", kind);
        erasurePolicy = requireText(erasurePolicy, "erasurePolicy", kind);
        useDecisionId = requireText(useDecisionId, "useDecisionId", kind);
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
