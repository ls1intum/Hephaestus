package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.conversation.ChatSignals;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationThreadArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackLane;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.IssueArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.PullRequestArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The practices module restates two kind literals the SCM domain owns, because it may not import the
 * module that declares them. A restated literal is a fork waiting to happen — that is exactly how
 * {@code CONVERSATION_THREAD} and {@code SLACK_MESSAGE_THREAD} came to name one thing — so the two
 * spellings are held together here, in test code, which may see both sides.
 */
class ArtifactKindsAgreementTest extends BaseUnitTest {

    @Test
    @DisplayName("the practices module's kind literals are the ones the owning domains declare")
    void agreesWithTheOwningDomain() {
        assertThat(ArtifactKinds.PULL_REQUEST).isEqualTo(ScmSignals.PULL_REQUEST);
        assertThat(ArtifactKinds.ISSUE).isEqualTo(ScmSignals.ISSUE);
        assertThat(ArtifactKinds.CONVERSATION_THREAD).isEqualTo(ChatSignals.CONVERSATION_THREAD);
    }

    @Test
    @DisplayName("the persisted spellings are pinned: changing one is a data migration, not an edit")
    void spellingsArePinned() {
        // These strings are in observation.artifact_kind, feedback.artifact_kind, agent_job.artifact_kind
        // and every signal name in every bundled practice's bindings. Re-spelling one without migrating
        // orphans every row already written under the old spelling.
        assertThat(
            Stream.of(ArtifactKinds.PULL_REQUEST, ArtifactKinds.ISSUE, ArtifactKinds.CONVERSATION_THREAD).map(
                ArtifactKind::value
            )
        ).containsExactly("scm.pull_request", "scm.issue", "chat.conversation_thread");
    }

    @Test
    @DisplayName("only a pull request carries a diff, so only a pull request has an inline lane")
    void onlyPullRequestsHaveAnInlineLane() {
        assertThat(ArtifactKinds.hasInlineLane(ArtifactKinds.PULL_REQUEST)).isTrue();
        assertThat(ArtifactKinds.hasInlineLane(ArtifactKinds.ISSUE)).isFalse();
        assertThat(ArtifactKinds.hasInlineLane(ArtifactKinds.CONVERSATION_THREAD)).isFalse();
        assertThat(ArtifactKinds.hasInlineLane(ArtifactKind.of("docs.document"))).isFalse();
    }

    @Test
    @DisplayName("the inline-lane shortcut says what the descriptors say")
    void inlineLaneAgreesWithTheDescriptors() {
        // The authority is the descriptor's FeedbackLane.IN_CONTEXT_INLINE; ArtifactKinds restates it
        // because DeliveryComposer is static and has no registry to ask. A restated fact is a fork
        // waiting to happen, so the two are held together here rather than by a comment promising they
        // agree. Asked of every shipped descriptor, so a fourth kind cannot arrive with the shortcut
        // silently wrong about it.
        for (ArtifactDescriptor descriptor : List.of(
            new PullRequestArtifactDescriptor(),
            new IssueArtifactDescriptor(),
            new ConversationThreadArtifactDescriptor()
        )) {
            assertThat(ArtifactKinds.hasInlineLane(descriptor.kind()))
                .as("ArtifactKinds and the descriptor disagree about the inline lane of '%s'", descriptor.kind())
                .isEqualTo(descriptor.lanes().contains(FeedbackLane.IN_CONTEXT_INLINE));
        }
    }

    @Test
    @DisplayName("a kind is equal by value, so a freshly parsed one matches the constant")
    void equalByValue() {
        // The enums this replaced were compared with ==, and every one of those comparisons had to be
        // rewritten. A kind read back out of a column is a different instance and must still match.
        assertThat(ArtifactKind.of("scm.pull_request")).isEqualTo(ArtifactKinds.PULL_REQUEST);
        assertThat(List.of(ArtifactKinds.PULL_REQUEST)).contains(ArtifactKind.of("scm.pull_request"));
    }
}
