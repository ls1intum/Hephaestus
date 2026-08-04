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
                        EvidenceFreshnessRequirement.CURRENT
                    ),
                    requirement(
                        "scm.pull-request.diff",
                        EvidenceCompletenessRequirement.COMPLETE,
                        EvidenceFreshnessRequirement.CURRENT
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
                        EvidenceFreshnessRequirement.CURRENT
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
                        EvidenceFreshnessRequirement.CURRENT
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
            List.of(new PracticeEvidenceLimitation(limitationCode, limitationDescription))
        );
    }

    private static PracticeEvidenceRequirement requirement(
        String sourceKind,
        EvidenceCompletenessRequirement completeness,
        EvidenceFreshnessRequirement freshness
    ) {
        return new PracticeEvidenceRequirement(new SourceKind(sourceKind), completeness, freshness);
    }

    private static PracticeOptionalContextSource optionalRequirement(String sourceKind) {
        return new PracticeOptionalContextSource(new SourceKind(sourceKind));
    }
}
