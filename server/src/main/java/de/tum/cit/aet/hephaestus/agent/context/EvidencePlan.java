package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewPolicy;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

    public static EvidencePlan compile(List<Practice> practices) {
        if (practices.isEmpty()) {
            throw new JobPreparationException("Cannot compile evidence for an empty practice set");
        }
        PracticeAutomatedReviewPolicy first = requireRequirements(practices.getFirst());
        ArtifactKind artifactKind = practices.getFirst().getArtifactKind();
        Set<SourceKind> selected = new LinkedHashSet<>();
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
            requirements.requiredEvidence().forEach(requirement -> selected.add(requirement.sourceKind()));
            requirements.optionalContext().forEach(requirement -> selected.add(requirement.sourceKind()));
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
