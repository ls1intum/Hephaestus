package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;

public final class PracticeTestEvidence {

    private PracticeTestEvidence() {}

    public static PracticeEvidenceDeclaration pullRequest() {
        return forArtifact(WorkArtifact.PULL_REQUEST);
    }

    public static PracticeEvidenceDeclaration conversationThread() {
        return forArtifact(WorkArtifact.CONVERSATION_THREAD);
    }

    public static PracticeEvidenceDeclaration forArtifact(WorkArtifact artifactType) {
        String profile;
        String kind;
        switch (artifactType) {
            case PULL_REQUEST -> {
                profile = "pull-request-review";
                kind = "scm.pull-request.core";
            }
            case ISSUE -> {
                profile = "issue-review";
                kind = "scm.issue.core";
            }
            case CONVERSATION_THREAD -> {
                profile = "conversation-review";
                kind = "slack.conversation.thread";
            }
            default -> throw new IllegalArgumentException("Unsupported artifact type: " + artifactType);
        }
        return new PracticeEvidenceDeclaration(
            new SourceContractVersion("1.0.0"),
            new EvidenceProfileId(profile),
            List.of(
                new PracticeEvidenceRequirement(
                    new SourceKind(kind),
                    EvidenceCompletenessRequirement.COMPLETE,
                    EvidenceFreshnessRequirement.CURRENT
                )
            ),
            List.of(),
            PracticeEvidenceRefusal.DECLINE_SEMANTIC_JUDGMENT,
            List.of()
        );
    }
}
