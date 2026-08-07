package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.conversation.ChatSignals;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.IssueArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.PullRequestArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions.SignalOption;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What an author may bind a practice to, now that the answer is derived from the domains rather than
 * written down in this module.
 */
class PracticeSignalOptionsTest extends BaseUnitTest {

    private final PracticeSignalOptions options = PracticeSignalOptionsFixture.real();

    @Test
    @DisplayName("a kind offers exactly the signals its descriptor declares")
    void offersWhatTheDomainDeclares() {
        assertThat(options.optionsFor(ArtifactKinds.PULL_REQUEST))
            .extracting(SignalOption::signal)
            .containsExactly(
                ScmSignals.PULL_REQUEST_OPENED,
                ScmSignals.PULL_REQUEST_READY,
                ScmSignals.PULL_REQUEST_SYNCHRONIZED,
                ScmSignals.PULL_REQUEST_REVIEWED,
                ScmSignals.PULL_REQUEST_MERGED,
                ScmSignals.PULL_REQUEST_CLOSED,
                ScmSignals.PULL_REQUEST_REVIEW_REQUESTED
            );
        assertThat(options.optionsFor(ArtifactKinds.ISSUE))
            .extracting(SignalOption::signal)
            .containsExactly(
                ScmSignals.ISSUE_OPENED,
                ScmSignals.ISSUE_LABELED,
                ScmSignals.ISSUE_CLOSED,
                ScmSignals.ISSUE_REVIEW_REQUESTED
            );
    }

    @Test
    @DisplayName("a conversation thread is authorable now that a descriptor declares what happens to it")
    void offersTheConversationSignal() {
        // The predecessor of this class had a hand-written arm returning an empty list for this kind,
        // and the three shipped conversation practices carried no trigger at all. Both were the same
        // absence: nothing declared that a settled thread is an occasion.
        assertThat(options.optionsFor(ArtifactKinds.CONVERSATION_THREAD))
            .extracting(SignalOption::signal)
            .containsExactly(ChatSignals.CONVERSATION_THREAD_SETTLED);
    }

    @Test
    @DisplayName("a kind no module declares offers nothing, by being absent rather than by a rule")
    void offersNothingForAnUndeclaredKind() {
        PracticeSignalOptions scmOnly = PracticeSignalOptionsFixture.with(
            new PullRequestArtifactDescriptor(),
            new IssueArtifactDescriptor()
        );

        assertThat(scmOnly.optionsFor(ArtifactKinds.CONVERSATION_THREAD)).isEmpty();
        assertThat(scmOnly.eligibleFor(ArtifactKinds.CONVERSATION_THREAD)).isEmpty();
        assertThat(scmOnly.authorableKinds()).doesNotContain(ArtifactKinds.CONVERSATION_THREAD);
    }

    @Test
    @DisplayName("the domain's recommendation reaches the authoring surface")
    void carriesTheDomainsRecommendation() {
        List<SignalName> recommended = options
            .optionsFor(ArtifactKinds.PULL_REQUEST)
            .stream()
            .filter(SignalOption::recommended)
            .map(SignalOption::signal)
            .toList();

        assertThat(recommended).containsExactly(
            ScmSignals.PULL_REQUEST_OPENED,
            ScmSignals.PULL_REQUEST_READY,
            ScmSignals.PULL_REQUEST_SYNCHRONIZED
        );
    }

    @Test
    @DisplayName("a signal raised from inside Hephaestus says so rather than looking like a broken producer")
    void separatesInternallyRaisedSignals() {
        // Both of these are real occasions with no ingested event behind them: somebody asked for a
        // review, or a scheduler decided a discussion had finished. Coverage must not read that as a
        // vendor failing to deliver.
        assertThat(options.producedByIngestion(ScmSignals.PULL_REQUEST_REVIEW_REQUESTED)).isFalse();
        assertThat(options.producedByIngestion(ChatSignals.CONVERSATION_THREAD_SETTLED)).isFalse();
        assertThat(options.producedByIngestion(ScmSignals.PULL_REQUEST_MERGED)).isTrue();
    }

    @Test
    @DisplayName("only reviewable kinds are authorable")
    void offersOnlyReviewableKinds() {
        assertThat(options.authorableKinds()).containsExactlyInAnyOrder(
            ArtifactKinds.PULL_REQUEST,
            ArtifactKinds.ISSUE,
            ArtifactKinds.CONVERSATION_THREAD,
            new ArtifactKind("docs.document")
        );
    }
}
