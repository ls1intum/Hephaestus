package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import java.util.List;

public final class PracticeTestEvidence {

    private PracticeTestEvidence() {}

    public static PracticeAutomatedReviewPolicy pullRequest() {
        return forArtifact(ArtifactKinds.PULL_REQUEST);
    }

    public static PracticeAutomatedReviewPolicy conversationThread() {
        return forArtifact(ArtifactKinds.CONVERSATION_THREAD);
    }

    public static PracticeAutomatedReviewPolicy forArtifact(ArtifactKind artifactKind) {
        String profile;
        List<String> kinds;
        if (ArtifactKinds.PULL_REQUEST.equals(artifactKind)) {
            profile = "pull-request-review";
            kinds = List.of("scm.pull-request.core", "scm.pull-request.diff");
        } else if (ArtifactKinds.ISSUE.equals(artifactKind)) {
            profile = "issue-review";
            kinds = List.of("scm.issue.core");
        } else if (ArtifactKinds.CONVERSATION_THREAD.equals(artifactKind)) {
            profile = "conversation-review";
            kinds = List.of("slack.conversation.thread");
        } else {
            throw new IllegalArgumentException("Unsupported artifact kind: " + artifactKind);
        }
        return new PracticeAutomatedReviewPolicy(
            new SourceContractVersion("1.0.0"),
            new EvidenceProfileId(profile),
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            ),
            kinds
                .stream()
                .map(kind ->
                    new PracticeEvidenceRequirement(
                        new SourceKind(kind),
                        EvidenceCompletenessRequirement.COMPLETE,
                        EvidenceContentRequirement.NO_REQUIREMENT
                    )
                )
                .toList(),
            List.of(),
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            List.of(),
            null
        );
    }
}
