package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;

public final class PracticeTestEvidence {

    private PracticeTestEvidence() {}

    public static PracticeAutomatedAssessmentPolicy pullRequest() {
        return forArtifact(WorkArtifact.PULL_REQUEST);
    }

    public static PracticeAutomatedAssessmentPolicy conversationThread() {
        return forArtifact(WorkArtifact.CONVERSATION_THREAD);
    }

    public static PracticeAutomatedAssessmentPolicy forArtifact(WorkArtifact artifactType) {
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
        return new PracticeAutomatedAssessmentPolicy(
            new SourceContractVersion("1.0.0"),
            new EvidenceProfileId(profile),
            new PracticeAutomatedAssessment(
                PracticeAutomatedAssessmentMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            ),
            kinds
                .stream()
                .map(kind ->
                    new PracticeEvidenceRequirement(
                        new SourceKind(kind),
                        EvidenceCompletenessRequirement.COMPLETE,
                        EvidenceFreshnessRequirement.CURRENT
                    )
                )
                .toList(),
            List.of(),
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_ASSESSMENT,
            List.of()
        );
    }
}
