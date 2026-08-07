package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public final class PracticeDefinitionValidator {

    private static final Pattern DETECTOR_VOCAB = Pattern.compile("\\b(?:PRESENT|ABSENT|GOOD|BAD|NOT_APPLICABLE)\\b");

    private final ArtifactSourceCatalogRegistry sourceCatalogs;
    private final PracticeSignalOptions signalOptions;

    public PracticeDefinitionValidator(
        ArtifactSourceCatalogRegistry sourceCatalogs,
        PracticeSignalOptions signalOptions
    ) {
        this.sourceCatalogs = sourceCatalogs;
        this.signalOptions = signalOptions;
    }

    public void validate(PracticeDefinition definition) {
        boolean canRunAutomatedReview = definition
            .automatedReviewPolicy()
            .automatedReview()
            .canAttemptAutomatedReview();
        validateBindings(definition.bindings());
        if (!canRunAutomatedReview && definition.precomputeScript() != null) {
            throw new IllegalArgumentException("A practice Hephaestus cannot review cannot define a precompute script");
        }
        rejectDetectorVocabulary("Why it matters", definition.whyItMatters());
        rejectDetectorVocabulary("What good looks like", definition.whatGoodLooksLike());
        validateEvidence(definition.artifactKind(), definition);
    }

    /**
     * A practice may only bind to signals a registered domain declares.
     *
     * <p>This is the boot cross-check that keeps a derived artifact kind honest: the kind is read off a
     * signal's prefix, so a misspelled signal would otherwise invent a kind nothing can raise and the
     * practice would sit in the catalog looking configured and never fire.
     *
     * <p>A practice only a human reviews is checked the same way. It used to be forbidden from naming
     * any occasion at all; it cannot be now, because the occasion is where its artifact kind comes from
     * — and saying what a practice is about was never the same claim as asking Hephaestus to act on it.
     */
    private void validateBindings(List<PracticeBinding> bindings) {
        ArtifactKind artifactKind = PracticeBinding.artifactKindOf(bindings);
        Set<SignalName> declared = signalOptions.eligibleFor(artifactKind);
        if (declared.isEmpty()) {
            throw new IllegalArgumentException("No registered domain declares signals for " + artifactKind);
        }
        for (PracticeBinding binding : bindings) {
            for (SignalName signal : binding.signals()) {
                if (!declared.contains(signal)) {
                    throw new IllegalArgumentException("Choose signals declared for the selected work type");
                }
            }
        }
    }

    private static void rejectDetectorVocabulary(String field, @Nullable String value) {
        if (value != null && DETECTOR_VOCAB.matcher(value).find()) {
            throw new IllegalArgumentException(
                field + " is guidance for people and must not use detector result labels"
            );
        }
    }

    /**
     * A practice may only read evidence that could exist for the kind of thing it reviews.
     *
     * <p>The allow-list is the sources that declare they apply to this artifact kind, asked of the
     * catalog each time rather than stored as a named profile. What each source demands of its capture
     * is no longer checked here at all: it is stated once in the source contract, and the contract
     * refuses to state a demand the source can never satisfy.
     */
    private void validateEvidence(ArtifactKind artifactKind, PracticeDefinition definition) {
        var version = definition.automatedReviewPolicy().sourceContractVersion();
        Set<SourceKind> applicable = sourceCatalogs.requireSourcesFor(version, artifactKind.value());
        for (PracticeBinding binding : definition.bindings()) {
            for (PracticeEvidenceRequirement need : binding.needs()) {
                var contract = sourceCatalogs.requireSource(version, need.sourceKind());
                if (!applicable.contains(need.sourceKind())) {
                    throw new IllegalArgumentException("Evidence source is not available for the selected work type");
                }
                // An exhaustive claim over a source that can never report a complete capture is a
                // practice that refuses every review it ever triggers. Caught here rather than at review
                // time, because "switched on and permanently refusing" is indistinguishable from
                // "nobody has done this yet" in the report it produces.
                if (need.stance().demandsCompleteCapture() && !contract.completenessPolicy().supportsComplete()) {
                    throw new IllegalArgumentException(
                        "Evidence source " +
                            need.sourceKind() +
                            " can never be captured completely, so no claim about what is absent from it can rest on it"
                    );
                }
            }
        }
    }
}
