package de.tum.cit.aet.hephaestus.practices.feedback.approval;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.ProposedPlacement;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class FeedbackApprovalDigestTest {

    @Test
    void bindsTheDecisionToTheExactRevisionPracticesBodiesAnchorsAndOrder() {
        Feedback original = proposal(
                "revision-a",
                List.of("practice-a"),
                List.of(ProposedPlacement.summary("Summary"), ProposedPlacement.inline("Note", "A.java", 4, 6, "rk")));
        String digest = FeedbackApprovalDigest.of(original);

        assertThat(List.of(
                                proposal(
                                        "revision-b",
                                        original.getProposedPracticeSlugs(),
                                        original.getProposedPlacements()),
                                proposal("revision-a", List.of("practice-b"), original.getProposedPlacements()),
                                proposal(
                                        "revision-a",
                                        original.getProposedPracticeSlugs(),
                                        List.of(
                                                ProposedPlacement.summary("Summary"),
                                                ProposedPlacement.inline("Changed", "A.java", 4, 6, "rk"))),
                                proposal(
                                        "revision-a",
                                        original.getProposedPracticeSlugs(),
                                        List.of(
                                                ProposedPlacement.summary("Summary"),
                                                ProposedPlacement.inline("Note", "A.java", 5, 6, "rk"))),
                                proposal(
                                        "revision-a",
                                        original.getProposedPracticeSlugs(),
                                        original.getProposedPlacements().reversed()))
                        .stream()
                        .map(FeedbackApprovalDigest::of))
                .allSatisfy(changed -> assertThat(changed).isNotEqualTo(digest));
    }

    private static Feedback proposal(String revision, List<String> practices, List<ProposedPlacement> placements) {
        return Feedback.builder()
                .channel(FeedbackChannel.IN_CONTEXT)
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(1L)
                .recipientUserId(2L)
                .aboutUserId(2L)
                .body("Summary")
                .reviewedRevision(revision)
                .proposedPracticeSlugs(practices)
                .proposedPlacements(placements)
                .build();
    }
}
