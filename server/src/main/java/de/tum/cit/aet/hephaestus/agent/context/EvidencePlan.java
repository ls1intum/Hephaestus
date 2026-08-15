package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewPolicy;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.util.List;
import java.util.Objects;

/**
 * What a capture runs under: the source contract it reads, and the kind of artifact it is about. Every source
 * the contract declares for that kind is staged — not a subset the eligible practices choose — because staging
 * happens before per-practice readiness is checked, so it cannot depend on it.
 *
 * <p>What still withholds a source afterward is consent, capability, or a collector's own fault, not relevance.
 */
public record EvidencePlan(SourceContractVersion contractVersion, ArtifactKind artifactKind) {
    public EvidencePlan {
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(artifactKind, "artifactKind");
    }

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
