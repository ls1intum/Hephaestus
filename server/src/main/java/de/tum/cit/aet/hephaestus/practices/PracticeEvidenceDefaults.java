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

    public PracticeEvidenceDeclaration forArtifact(WorkArtifact artifact) {
        return switch (artifact) {
            case PULL_REQUEST -> declaration(
                "pull-request-review",
                requirement(
                    "scm.pull-request.core",
                    EvidenceCompletenessRequirement.COMPLETE,
                    EvidenceFreshnessRequirement.CURRENT
                ),
                List.of(
                    requirement(
                        "scm.pull-request.comments",
                        EvidenceCompletenessRequirement.ANY,
                        EvidenceFreshnessRequirement.ANY
                    )
                ),
                "RUNTIME_BEHAVIOR_NOT_OBSERVED",
                "Repository evidence does not establish behavior in a deployed runtime."
            );
            case ISSUE -> declaration(
                "issue-review",
                requirement(
                    "scm.issue.core",
                    EvidenceCompletenessRequirement.COMPLETE,
                    EvidenceFreshnessRequirement.CURRENT
                ),
                List.of(
                    requirement(
                        "scm.issue.comments",
                        EvidenceCompletenessRequirement.ANY,
                        EvidenceFreshnessRequirement.ANY
                    )
                ),
                "IMPLEMENTATION_NOT_OBSERVED",
                "Issue evidence does not establish whether the described work was implemented correctly."
            );
            case CONVERSATION_THREAD -> declaration(
                "conversation-review",
                requirement(
                    "slack.conversation.thread",
                    EvidenceCompletenessRequirement.COMPLETE,
                    EvidenceFreshnessRequirement.CURRENT
                ),
                List.of(),
                "PRIVATE_CONTEXT_NOT_OBSERVED",
                "The captured thread does not include decisions or context shared outside the conversation."
            );
        };
    }

    private PracticeEvidenceDeclaration declaration(
        String profile,
        PracticeEvidenceRequirement required,
        List<PracticeEvidenceRequirement> optional,
        String blindSpotCode,
        String blindSpotSummary
    ) {
        return new PracticeEvidenceDeclaration(
            catalogs.current().version(),
            new EvidenceProfileId(profile),
            List.of(required),
            optional,
            PracticeEvidenceRefusal.DECLINE_SEMANTIC_JUDGMENT,
            List.of(new PracticeEvidenceBlindSpot(blindSpotCode, blindSpotSummary))
        );
    }

    private static PracticeEvidenceRequirement requirement(
        String sourceKind,
        EvidenceCompletenessRequirement completeness,
        EvidenceFreshnessRequirement freshness
    ) {
        return new PracticeEvidenceRequirement(new SourceKind(sourceKind), completeness, freshness);
    }
}
