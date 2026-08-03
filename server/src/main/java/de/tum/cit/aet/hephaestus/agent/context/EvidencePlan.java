package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceDeclaration;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Exact, minimized evidence selection compiled before any provider is called. */
public record EvidencePlan(
    SourceContractVersion contractVersion,
    EvidenceProfileId profileId,
    Set<SourceKind> selectedSources
) {
    public EvidencePlan {
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(profileId, "profileId");
        selectedSources = Set.copyOf(Objects.requireNonNull(selectedSources, "selectedSources"));
        if (selectedSources.isEmpty()) {
            throw new IllegalArgumentException("An evidence plan must select at least one source");
        }
    }

    public static EvidencePlan compile(List<Practice> practices) {
        if (practices.isEmpty()) {
            throw new JobPreparationException("Cannot compile evidence for an empty practice set");
        }
        PracticeEvidenceDeclaration first = requireDeclaration(practices.getFirst());
        Set<SourceKind> selected = new LinkedHashSet<>();
        for (Practice practice : practices) {
            PracticeEvidenceDeclaration declaration = requireDeclaration(practice);
            if (
                !first.sourceContractVersion().equals(declaration.sourceContractVersion()) ||
                !first.profile().equals(declaration.profile())
            ) {
                throw new JobPreparationException(
                    "Practices sharing one invocation must use the same evidence contract and profile"
                );
            }
            declaration.required().forEach(requirement -> selected.add(requirement.sourceKind()));
            declaration.optional().forEach(requirement -> selected.add(requirement.sourceKind()));
        }
        return new EvidencePlan(first.sourceContractVersion(), first.profile(), selected);
    }

    private static PracticeEvidenceDeclaration requireDeclaration(Practice practice) {
        if (practice.getEvidence() == null) {
            throw new JobPreparationException("Practice has no evidence declaration: " + practice.getSlug());
        }
        return practice.getEvidence();
    }
}
