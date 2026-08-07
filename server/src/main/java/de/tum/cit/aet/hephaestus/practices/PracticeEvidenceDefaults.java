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
     *
     * <p>How strictly each source must be captured is no longer stated here — that belongs to the
     * source contract, which every practice agreed with anyway.
     */
    public PracticeAutomatedReviewPolicy forArtifact(ArtifactKind artifact) {
        if (ArtifactKinds.PULL_REQUEST.equals(artifact)) {
            return requirements(
                List.of(
                    required("scm.pull-request.core"),
                    // A diff is what a judgement about how a change was made is made from; its contract
                    // demands a complete, non-empty capture for exactly that reason.
                    required("scm.pull-request.diff"),
                    // Required rather than contextual, which is where every one of the 36 shipped
                    // practices that reads comments already put it. The stance is what separates "there
                    // were no comments" from "we failed to collect the comments", and only the first of
                    // those is a fact about a developer.
                    required("scm.pull-request.comments")
                ),
                "RUNTIME_BEHAVIOR_NOT_OBSERVED",
                "Repository evidence does not establish behavior in a deployed runtime."
            );
        }
        if (ArtifactKinds.ISSUE.equals(artifact)) {
            return requirements(
                List.of(required("scm.issue.core"), required("scm.issue.comments")),
                "IMPLEMENTATION_NOT_OBSERVED",
                "Issue evidence does not establish whether the described work was implemented correctly."
            );
        }
        if (ArtifactKinds.CONVERSATION_THREAD.equals(artifact)) {
            return requirements(
                List.of(required("slack.conversation.thread")),
                "PRIVATE_CONTEXT_NOT_OBSERVED",
                "The captured thread does not include decisions or context shared outside the conversation."
            );
        }
        throw new IllegalArgumentException("No default evidence requirements for artifact kind: " + artifact);
    }

    private PracticeAutomatedReviewPolicy requirements(
        List<PracticeEvidenceRequirement> needs,
        String limitationCode,
        String limitationDescription
    ) {
        return new PracticeAutomatedReviewPolicy(
            catalogs.current().version(),
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            ),
            needs,
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            List.of(new PracticeEvidenceLimitation(limitationCode, limitationDescription)),
            null
        );
    }

    private static PracticeEvidenceRequirement required(String sourceKind) {
        return new PracticeEvidenceRequirement(new SourceKind(sourceKind), EvidenceStance.REQUIRED);
    }
}
