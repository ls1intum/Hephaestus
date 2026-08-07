package de.tum.cit.aet.hephaestus.agent.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.signal.RevisionScheme;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A campaign measures each artifact at the state it is actually in, using the live signal vocabulary.
 * That reuse is what makes a backfill match the practices a workspace already bound, and what makes the
 * ledger de-duplicate an artifact the live path already measured.
 */
@DisplayName("Backfill signal selection")
class ReviewBackfillSignalsTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 7L;
    private static final long ARTIFACT_ID = 42L;

    @Test
    void aMergedPullRequestIsMeasuredAtItsMerge() {
        PullRequest pr = pullRequest();
        pr.setMerged(true);
        pr.setState(Issue.State.MERGED);

        assertThat(ReviewBackfillSignals.keyFor(WORKSPACE_ID, pr))
            .get()
            .extracting(SignalKey::signalName)
            .isEqualTo(ScmSignals.PULL_REQUEST_MERGED);
    }

    @Test
    void aClosedButUnmergedPullRequestIsMeasuredAtItsClose() {
        PullRequest pr = pullRequest();
        pr.setState(Issue.State.CLOSED);

        assertThat(ReviewBackfillSignals.keyFor(WORKSPACE_ID, pr))
            .get()
            .extracting(SignalKey::signalName)
            .isEqualTo(ScmSignals.PULL_REQUEST_CLOSED);
    }

    /** A draft has not reached "ready"; claiming it had would measure it against the wrong occasion. */
    @Test
    void anOpenDraftIsMeasuredAsOpenedNotAsReady() {
        PullRequest pr = pullRequest();
        pr.setDraft(true);

        assertThat(ReviewBackfillSignals.keyFor(WORKSPACE_ID, pr))
            .get()
            .extracting(SignalKey::signalName)
            .isEqualTo(ScmSignals.PULL_REQUEST_OPENED);
    }

    @Test
    void anOpenNonDraftPullRequestIsMeasuredAsReady() {
        assertThat(ReviewBackfillSignals.keyFor(WORKSPACE_ID, pullRequest()))
            .get()
            .extracting(SignalKey::signalName)
            .isEqualTo(ScmSignals.PULL_REQUEST_READY);
    }

    /**
     * The revision is the live scheme's, not a per-run one. That is what makes a second campaign over the
     * same still-unchanged artifact a no-op instead of a second charge for the same measurement.
     */
    @Test
    void theRevisionIsTheOneTheLivePathWouldHaveUsed() {
        PullRequest ready = pullRequest();
        assertThat(ReviewBackfillSignals.keyFor(WORKSPACE_ID, ready).orElseThrow().revision().scheme()).contains(
            RevisionScheme.HEAD_COMMIT
        );

        PullRequest merged = pullRequest();
        merged.setMerged(true);
        assertThat(ReviewBackfillSignals.keyFor(WORKSPACE_ID, merged).orElseThrow().revision().scheme()).contains(
            RevisionScheme.TERMINAL_STATE
        );
    }

    /**
     * Nothing stable to key a code-shaped measurement on. Recording it under an invented revision would
     * make the row permanently un-deduplicable against the live path.
     */
    @Test
    void aPullRequestWithNoHeadCommitIsNotMeasurable() {
        PullRequest pr = pullRequest();
        pr.setHeadRefOid(null);

        assertThat(ReviewBackfillSignals.keyFor(WORKSPACE_ID, pr)).isEmpty();
    }

    @Test
    void anIssueIsMeasuredOpenOrClosed() {
        Issue open = issue();
        Issue closed = issue();
        closed.setState(Issue.State.CLOSED);

        assertThat(ReviewBackfillSignals.keyFor(WORKSPACE_ID, open).map(SignalKey::signalName)).contains(
            ScmSignals.ISSUE_OPENED
        );
        assertThat(ReviewBackfillSignals.keyFor(WORKSPACE_ID, closed).map(SignalKey::signalName)).contains(
            ScmSignals.ISSUE_CLOSED
        );
    }

    /** An issue has no commits, so its identity is what the author wrote — which a campaign can read. */
    @Test
    void anIssueIsAlwaysMeasurable() {
        Optional<SignalKey> key = ReviewBackfillSignals.keyFor(WORKSPACE_ID, issue());

        assertThat(key).isPresent();
        assertThat(key.orElseThrow().revision().scheme()).contains(RevisionScheme.CONTENT_DIGEST);
    }

    private PullRequest pullRequest() {
        PullRequest pr = new PullRequest();
        pr.setId(ARTIFACT_ID);
        pr.setState(Issue.State.OPEN);
        pr.setTitle("Add a thing");
        pr.setBody("because reasons");
        pr.setHeadRefOid("0123456789abcdef0123456789abcdef01234567");
        return pr;
    }

    private Issue issue() {
        Issue issue = new Issue();
        issue.setId(ARTIFACT_ID);
        issue.setState(Issue.State.OPEN);
        issue.setTitle("Something is wrong");
        issue.setBody("here is what");
        return issue;
    }
}
