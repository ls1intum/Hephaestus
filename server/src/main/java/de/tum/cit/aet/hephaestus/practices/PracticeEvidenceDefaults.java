package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PracticeEvidenceDefaults {

    private final ArtifactSourceCatalogRegistry catalogs;

    public PracticeEvidenceDefaults(ArtifactSourceCatalogRegistry catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * The evidence a review of this kind needs when the author has not said otherwise.
     *
     * <p>An artifact kind is an open vocabulary, so the compiler no longer proves this covers every one;
     * an unknown kind throws rather than falling back to a pull request's requirements, because a
     * silently borrowed default would demand a diff of something that has none and refuse every review.
     */
    public PracticeAutomatedReviewPolicy forArtifact(ArtifactKind artifact) {
        if (ArtifactKinds.PULL_REQUEST.equals(artifact)) {
            return requirements(
                List.of(
                    requirement("scm.pull-request.core", EvidenceCompletenessRequirement.COMPLETE),
                    // A diff with no changes in it cannot support a judgement about how a change
                    // was made; without this the model falls back to the title and description.
                    requirement(
                        "scm.pull-request.diff",
                        EvidenceCompletenessRequirement.COMPLETE,
                        EvidenceContentRequirement.NON_EMPTY
                    )
                ),
                List.of(optionalRequirement("scm.pull-request.comments")),
                "RUNTIME_BEHAVIOR_NOT_OBSERVED",
                "Repository evidence does not establish behavior in a deployed runtime."
            );
        }
        if (ArtifactKinds.ISSUE.equals(artifact)) {
            return requirements(
                List.of(requirement("scm.issue.core", EvidenceCompletenessRequirement.COMPLETE)),
                List.of(optionalRequirement("scm.issue.comments")),
                "IMPLEMENTATION_NOT_OBSERVED",
                "Issue evidence does not establish whether the described work was implemented correctly."
            );
        }
        if (ArtifactKinds.CONVERSATION_THREAD.equals(artifact)) {
            return requirements(
                List.of(requirement("slack.conversation.thread", EvidenceCompletenessRequirement.COMPLETE)),
                List.of(),
                "PRIVATE_CONTEXT_NOT_OBSERVED",
                "The captured thread does not include decisions or context shared outside the conversation."
            );
        }
        throw new IllegalArgumentException("No default evidence requirements for artifact kind: " + artifact);
    }

    private PracticeAutomatedReviewPolicy requirements(
        List<PracticeEvidenceRequirement> required,
        List<PracticeOptionalContextSource> optional,
        String limitationCode,
        String limitationDescription
    ) {
        return new PracticeAutomatedReviewPolicy(
            catalogs.current().version(),
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
        EvidenceCompletenessRequirement completeness
    ) {
        return requirement(sourceKind, completeness, EvidenceContentRequirement.NO_REQUIREMENT);
    }

    private static PracticeEvidenceRequirement requirement(
        String sourceKind,
        EvidenceCompletenessRequirement completeness,
        EvidenceContentRequirement content
    ) {
        return new PracticeEvidenceRequirement(new SourceKind(sourceKind), completeness, content);
    }

    private static PracticeOptionalContextSource optionalRequirement(String sourceKind) {
        return new PracticeOptionalContextSource(new SourceKind(sourceKind));
    }
}
