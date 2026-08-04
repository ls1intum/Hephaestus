package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedAssessmentPolicy;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record EvidencePlan(
    SourceContractVersion contractVersion,
    EvidenceProfileId evidenceProfile,
    Set<SourceKind> selectedSources
) {
    public EvidencePlan {
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(evidenceProfile, "evidenceProfile");
        selectedSources = Set.copyOf(Objects.requireNonNull(selectedSources, "selectedSources"));
        if (selectedSources.isEmpty()) {
            throw new IllegalArgumentException("An evidence plan must select at least one source");
        }
    }

    public static EvidencePlan compile(List<Practice> practices) {
        if (practices.isEmpty()) {
            throw new JobPreparationException("Cannot compile evidence for an empty practice set");
        }
        PracticeAutomatedAssessmentPolicy first = requireRequirements(practices.getFirst());
        Set<SourceKind> selected = new LinkedHashSet<>();
        for (Practice practice : practices) {
            PracticeAutomatedAssessmentPolicy requirements = requireRequirements(practice);
            if (
                !first.sourceContractVersion().equals(requirements.sourceContractVersion()) ||
                !first.evidenceProfile().equals(requirements.evidenceProfile())
            ) {
                throw new JobPreparationException(
                    "Practices sharing one invocation must use the same source contract and evidence profile"
                );
            }
            requirements.requiredEvidence().forEach(requirement -> selected.add(requirement.sourceKind()));
            requirements.optionalContext().forEach(requirement -> selected.add(requirement.sourceKind()));
        }
        return new EvidencePlan(first.sourceContractVersion(), first.evidenceProfile(), selected);
    }

    private static PracticeAutomatedAssessmentPolicy requireRequirements(Practice practice) {
        if (practice.getAutomatedAssessmentPolicy() == null) {
            throw new JobPreparationException("Practice has no evidence requirements: " + practice.getSlug());
        }
        return practice.getAutomatedAssessmentPolicy();
    }
}
