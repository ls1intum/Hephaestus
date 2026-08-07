package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;
import java.util.Set;

/** Versioned semantics for one logical evidence source. */
public record ArtifactSourceContract(
    SourceKind kind,
    String displayName,
    String description,
    String selectionScope,
    Set<String> artifactKinds,
    SourceAuthority authority,
    IdentityPolicy identityPolicy,
    CompletenessPolicy completenessPolicy,
    RequiredCaptureQuality requiredQuality,
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
        artifactKinds = Set.copyOf(Objects.requireNonNull(artifactKinds, "artifactKinds"));
        if (artifactKinds.isEmpty() || artifactKinds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("artifactKinds must contain non-blank values: " + kind);
        }
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(identityPolicy, "identityPolicy");
        Objects.requireNonNull(completenessPolicy, "completenessPolicy");
        // Only a source read straight from upstream, or derived from one without discarding anything,
        // can be anchored to an identity that cannot change under it. A mirror reflects upstream state
        // that moves independently, so calling its capture pinned would report a copy that has since
        // drifted as demonstrably current.
        if (
            identityPolicy.mode() == IdentityMode.PINNED_IDENTITY &&
            authority != SourceAuthority.UPSTREAM_SNAPSHOT &&
            authority != SourceAuthority.DETERMINISTIC_DERIVATION
        ) {
            throw new IllegalArgumentException("Only an upstream or lossless source can pin an identity: " + kind);
        }
        // A lossy derivation is a bounded summary of its subject, not the subject. Reporting one as
        // COMPLETE would answer "is all of it here?" with a yes that is true of the summary and false
        // of what a practice author asked about.
        if (authority == SourceAuthority.LOSSY_DERIVATION && completenessPolicy.supportsComplete()) {
            throw new IllegalArgumentException("A lossy derivation cannot report COMPLETE: " + kind);
        }
        Objects.requireNonNull(requiredQuality, "requiredQuality");
        // Demanding a quality the source can never report is a refusal written as a requirement: every
        // review that required the source would be refused, permanently and for a reason no operator
        // could act on. Caught here so the contract cannot state it, rather than per practice.
        if (requiredQuality.demandsComplete() && !completenessPolicy.supportsComplete()) {
            throw new IllegalArgumentException("A source that cannot report COMPLETE cannot demand it: " + kind);
        }
        // A source that can never be validly empty already fails capture when it is, so demanding
        // non-emptiness of it says nothing and hides which sources the demand is really about.
        if (requiredQuality.demandsContent() && !completenessPolicy.supportsEmpty()) {
            throw new IllegalArgumentException(
                "A source that cannot be validly empty cannot demand non-emptiness: " + kind
            );
        }
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

    public boolean appliesTo(String artifactKind) {
        return artifactKinds.contains(artifactKind);
    }

    private static String requireText(String value, String field, SourceKind kind) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank: " + kind);
        }
        return value;
    }
}
