package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewPolicy;
import de.tum.cit.aet.hephaestus.practices.PracticeBinding;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record EvidencePlan(
    SourceContractVersion contractVersion,
    ArtifactKind artifactKind,
    Set<SourceKind> selectedSources
) {
    public EvidencePlan {
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(artifactKind, "artifactKind");
        selectedSources = Set.copyOf(Objects.requireNonNull(selectedSources, "selectedSources"));
        if (selectedSources.isEmpty()) {
            throw new IllegalArgumentException("An evidence plan must select at least one source");
        }
    }

    /** What earlier reviews recorded about the person whose work is under review. */
    public static final SourceKind OBSERVATION_HISTORY = new SourceKind("hephaestus.observation-history");

    /** What earlier reviews already said to that person, and through which channel. */
    public static final SourceKind FEEDBACK_HISTORY = new SourceKind("hephaestus.feedback-history");

    /**
     * Sources every review of every practice gets, whether or not a binding asked for them.
     *
     * <p>These describe the <em>workspace</em>, not the artifact: what has already been observed about
     * this person and what has already been said to them. A review of one event is only ever a partial
     * practice review, and the thing that makes a sequence of them add up to more than a sequence is
     * that each one can see the ones before it. Making that conditional on 37 practice authors each
     * remembering to declare it would mean most reviews are staged without it, which is the same as not
     * having it.
     *
     * <p>They are staged as declared contract sources rather than as loose files precisely so the
     * never-fabricate guarantee extends to them: a claim about an earlier observation is a citation, and
     * it is checked against the staged bytes exactly like a citation to a diff. Free-floating context
     * would be quotable without being checkable.
     *
     * <p>They are deliberately absent from every binding's {@code needs}, which is what keeps them from
     * ever gating readiness: a first-ever review has an empty history and must still be reviewable.
     */
    public static final Set<SourceKind> WORKSPACE_CONTEXT_SOURCES = Set.of(OBSERVATION_HISTORY, FEEDBACK_HISTORY);

    /**
     * What to capture for a review of these practices occasioned by {@code signal}.
     *
     * <p>The signal decides what is collected because evidence is declared per binding: a practice
     * reviewed when a change merges may have to establish that no decision was ever recorded, while the
     * same practice reviewed when the change was opened is only reading what is in front of it. A
     * {@code null} signal — an explicit ask — selects everything every binding reads, so that a review
     * somebody requested is not silently narrower than one an event started.
     */
    public static EvidencePlan compile(List<Practice> practices, @Nullable SignalName signal) {
        if (practices.isEmpty()) {
            throw new JobPreparationException("Cannot compile evidence for an empty practice set");
        }
        PracticeAutomatedReviewPolicy first = requireRequirements(practices.getFirst());
        ArtifactKind artifactKind = practices.getFirst().getArtifactKind();
        Set<SourceKind> selected = new LinkedHashSet<>(WORKSPACE_CONTEXT_SOURCES);
        for (Practice practice : practices) {
            PracticeAutomatedReviewPolicy requirements = requireRequirements(practice);
            if (
                !first.sourceContractVersion().equals(requirements.sourceContractVersion()) ||
                !artifactKind.equals(practice.getArtifactKind())
            ) {
                throw new JobPreparationException(
                    "Practices sharing one invocation must use the same source contract and artifact kind"
                );
            }
            PracticeBinding.needsFor(practice.getBindings(), signal).forEach(need -> selected.add(need.sourceKind()));
        }
        return new EvidencePlan(first.sourceContractVersion(), artifactKind, selected);
    }

    private static PracticeAutomatedReviewPolicy requireRequirements(Practice practice) {
        if (practice.getAutomatedReviewPolicy() == null) {
            throw new JobPreparationException("Practice has no evidence requirements: " + practice.getSlug());
        }
        return practice.getAutomatedReviewPolicy();
    }
}
