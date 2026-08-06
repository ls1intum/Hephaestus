package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;

public final class PracticeTestEvidence {

    private PracticeTestEvidence() {}

    public static PracticeAutomatedReviewPolicy pullRequest() {
        return forArtifact(WorkArtifact.PULL_REQUEST);
    }

    public static PracticeAutomatedReviewPolicy conversationThread() {
        return forArtifact(WorkArtifact.CONVERSATION_THREAD);
    }

    public static PracticeAutomatedReviewPolicy forArtifact(WorkArtifact artifactType) {
        String profile;
        List<String> kinds;
        switch (artifactType) {
            case PULL_REQUEST -> {
                profile = "pull-request-review";
                kinds = List.of("scm.pull-request.core", "scm.pull-request.diff");
            }
            case ISSUE -> {
                profile = "issue-review";
                kinds = List.of("scm.issue.core");
            }
            case CONVERSATION_THREAD -> {
                profile = "conversation-review";
                kinds = List.of("slack.conversation.thread");
            }
            default -> throw new IllegalArgumentException("Unsupported artifact type: " + artifactType);
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
