package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PracticeEvidenceDefaults {

    private final ArtifactSourceCatalogRegistry catalogs;

    public PracticeEvidenceDefaults(ArtifactSourceCatalogRegistry catalogs) {
        this.catalogs = catalogs;
    }

    public PracticeAutomatedReviewPolicy forArtifact(WorkArtifact artifact) {
        return switch (artifact) {
            case PULL_REQUEST -> requirements(
                "pull-request-review",
                List.of(
                    requirement(
                        "scm.pull-request.core",
                        EvidenceCompletenessRequirement.COMPLETE,
                        EvidenceFreshnessRequirement.NO_REQUIREMENT
                    ),
                    // A diff with no changes in it cannot support a judgement about how a change
                    // was made; without this the model falls back to the title and description.
                    requirement(
                        "scm.pull-request.diff",
                        EvidenceCompletenessRequirement.COMPLETE,
                        EvidenceFreshnessRequirement.CURRENT,
                        EvidenceContentRequirement.NON_EMPTY
                    )
                ),
                List.of(optionalRequirement("scm.pull-request.comments")),
                "RUNTIME_BEHAVIOR_NOT_OBSERVED",
                "Repository evidence does not establish behavior in a deployed runtime."
            );
            case ISSUE -> requirements(
                "issue-review",
                List.of(
                    requirement(
                        "scm.issue.core",
                        EvidenceCompletenessRequirement.COMPLETE,
                        EvidenceFreshnessRequirement.NO_REQUIREMENT
                    )
                ),
                List.of(optionalRequirement("scm.issue.comments")),
                "IMPLEMENTATION_NOT_OBSERVED",
                "Issue evidence does not establish whether the described work was implemented correctly."
            );
            case CONVERSATION_THREAD -> requirements(
                "conversation-review",
                List.of(
                    requirement(
                        "slack.conversation.thread",
                        EvidenceCompletenessRequirement.COMPLETE,
                        EvidenceFreshnessRequirement.NO_REQUIREMENT
                    )
                ),
                List.of(),
                "PRIVATE_CONTEXT_NOT_OBSERVED",
                "The captured thread does not include decisions or context shared outside the conversation."
            );
        };
    }

    private PracticeAutomatedReviewPolicy requirements(
        String profile,
        List<PracticeEvidenceRequirement> required,
        List<PracticeOptionalContextSource> optional,
        String limitationCode,
        String limitationDescription
    ) {
        return new PracticeAutomatedReviewPolicy(
            catalogs.current().version(),
            new EvidenceProfileId(profile),
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            ),
            required,
            optional,
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            List.of(new PracticeEvidenceLimitation(limitationCode, limitationDescription)),
            null
        );
    }

    private static PracticeEvidenceRequirement requirement(
        String sourceKind,
        EvidenceCompletenessRequirement completeness,
        EvidenceFreshnessRequirement freshness
    ) {
        return requirement(sourceKind, completeness, freshness, EvidenceContentRequirement.NO_REQUIREMENT);
    }

    private static PracticeEvidenceRequirement requirement(
        String sourceKind,
        EvidenceCompletenessRequirement completeness,
        EvidenceFreshnessRequirement freshness,
        EvidenceContentRequirement content
    ) {
        return new PracticeEvidenceRequirement(new SourceKind(sourceKind), completeness, freshness, content);
    }

    private static PracticeOptionalContextSource optionalRequirement(String sourceKind) {
        return new PracticeOptionalContextSource(new SourceKind(sourceKind));
    }
}
