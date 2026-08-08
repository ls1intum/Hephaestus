package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewPolicy;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.util.List;
import java.util.Objects;

/**
 * What a capture runs under: the source contract it reads, and the kind of artifact it is about.
 *
 * <p>Those two answers are the whole plan, because <em>which</em> sources get staged is not a decision
 * anybody makes per run — it is every source the contract says applies to the artifact kind. A review of
 * a pull request gets the pull request, its diff, its comments, its review threads, the repository tree,
 * the linked work items, the rest of the project, the team's documentation, and what earlier reviews
 * found and said. Not a subset chosen for it.
 *
 * <p>It used to be a subset. The plan unioned the {@code needs} of the practices eligible for the signal
 * and the sandbox view was then cut back to that union, on the theory that a source no practice named was
 * a source nobody would read. Three things were wrong with it. It reduced nothing: every collector had
 * already run and every artifact was already in the content-addressed store before the cut happened, so
 * it withheld from the model without withholding from disk. It was far more than a trim — measured
 * against the shipped catalog, the repository tree reached 20 of 37 practices, documentation reached one,
 * and the project inventory reached none at all, which meant the orchestrator prompt told the model to
 * read a file that was never staged. And it conflated two different questions: what a practice
 * <em>needs</em> before it may be reviewed at all (readiness, which still refuses), and what the model
 * <em>can see</em> while reviewing it. An agent that can explore the whole workspace finds the thing the
 * practice author did not think to declare; one handed a pre-cut subset cannot.
 *
 * <p>What still withholds is not relevance. A source with no unexpired use decision for this purpose is
 * refused as {@code GOVERNANCE_NOT_EFFECTIVE}, a source with no collector in this deployment is reported
 * {@code NO_PROVIDER}, and a collector that fails costs its own source and nothing else. Those are
 * consent, capability and fault — none of them is an opinion about what the review will find useful.
 */
public record EvidencePlan(SourceContractVersion contractVersion, ArtifactKind artifactKind) {
    public EvidencePlan {
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(artifactKind, "artifactKind");
    }

    /**
     * The contract and artifact kind these practices are reviewed under.
     *
     * <p>Takes no signal. What occasioned the review decides which of a practice's bindings speaks for
     * its readiness — that question is still asked, in {@code checkAutomatedReviewReadiness}. It no
     * longer decides what gets staged, because staging is not a per-practice question any more.
     */
    public static EvidencePlan compile(List<Practice> practices) {
        if (practices.isEmpty()) {
            throw new JobPreparationException("Cannot compile evidence for an empty practice set");
        }
        PracticeAutomatedReviewPolicy first = requireRequirements(practices.getFirst());
        ArtifactKind artifactKind = practices.getFirst().getArtifactKind();
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
        }
        return new EvidencePlan(first.sourceContractVersion(), artifactKind);
    }

    private static PracticeAutomatedReviewPolicy requireRequirements(Practice practice) {
        if (practice.getAutomatedReviewPolicy() == null) {
            throw new JobPreparationException("Practice has no evidence requirements: " + practice.getSlug());
        }
        return practice.getAutomatedReviewPolicy();
    }
}
