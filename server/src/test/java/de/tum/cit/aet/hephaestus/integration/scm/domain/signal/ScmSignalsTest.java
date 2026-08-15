package de.tum.cit.aet.hephaestus.integration.scm.domain.signal;

import static de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.signal.RevisionScheme;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What counts as a new occurrence is the whole reason the ledger works, so these tests are written as
 * the coaching scenarios they protect rather than as mapping-table assertions.
 */
class ScmSignalsTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 7L;
    private static final long ARTIFACT_ID = 42L;

    private Optional<SignalKey> pullRequest(String triggerEvent, String headRefOid, String title, String body) {
        return ScmSignals.pullRequestKey(
            WORKSPACE_ID,
            ARTIFACT_ID,
            ScmSignals.forTriggerEvent(triggerEvent).orElseThrow(),
            headRefOid,
            title,
            body
        );
    }

    private Optional<SignalKey> issue(String triggerEvent, String title, String body, String labelName) {
        return ScmSignals.issueKey(
            WORKSPACE_ID,
            ARTIFACT_ID,
            ScmSignals.forTriggerEvent(triggerEvent).orElseThrow(),
            title,
            body,
            labelName
        );
    }

    @Test
    void shouldReMeasureAnIssueAfterItsAuthorRewritesTheDescription() {
        // An issue has no commits, so if its signal keyed on anything code-shaped, an issue-writing
        // practice could be measured once and never again.
        SignalKey before = issue(TriggerEventNames.ISSUE_CREATED, "Bug", "it broke", null).orElseThrow();
        SignalKey after = issue(
            TriggerEventNames.ISSUE_CREATED,
            "Bug",
            "Login 500s on expired token",
            null
        ).orElseThrow();

        assertThat(after.revision()).isNotEqualTo(before.revision());
    }

    @Test
    void shouldNotReMeasureAnIssueThatWasMerelyRedelivered() {
        SignalKey first = issue(TriggerEventNames.ISSUE_CREATED, "Bug", "it broke", null).orElseThrow();
        SignalKey redelivered = issue(TriggerEventNames.ISSUE_CREATED, "Bug", "it broke", null).orElseThrow();

        assertThat(redelivered).isEqualTo(first);
    }

    @Test
    void shouldGiveEachLabelOfOneUpdateItsOwnOccurrence() {
        // Applying three labels in one edit raises three events differing only in the label; keying on
        // anything else collapses them into one ledger row, measuring a labelling-bound practice once per
        // edit instead of once per label.
        SignalKey bug = issue(
            TriggerEventNames.ISSUE_LABELED,
            "Login fails",
            "500 on expired token",
            "bug"
        ).orElseThrow();
        SignalKey regression = issue(
            TriggerEventNames.ISSUE_LABELED,
            "Login fails",
            "500 on expired token",
            "regression"
        ).orElseThrow();
        SignalKey priorityHigh = issue(
            TriggerEventNames.ISSUE_LABELED,
            "Login fails",
            "500 on expired token",
            "priority/high"
        ).orElseThrow();

        assertThat(List.of(bug.revision(), regression.revision(), priorityHigh.revision())).doesNotHaveDuplicates();
    }

    @Test
    void shouldNotReMeasureTheSameLabellingThatWasMerelyRedelivered() {
        SignalKey first = issue(TriggerEventNames.ISSUE_LABELED, "Login fails", "500", "bug").orElseThrow();
        SignalKey redelivered = issue(TriggerEventNames.ISSUE_LABELED, "Login fails", "500", "bug").orElseThrow();

        assertThat(redelivered).isEqualTo(first);
    }

    @Test
    void shouldDeclineToKeyALabellingThatCannotNameItsLabel() {
        // Same rule as a code-shaped signal with no head commit: a row keyed on nothing swallows every later labelling.
        assertThat(issue(TriggerEventNames.ISSUE_LABELED, "Login fails", "500", null)).isEmpty();
    }

    @Test
    void shouldNotReReviewUnchangedCodeBecauseTheDescriptionWasEdited() {
        // Converse of the issue case: a push signal's subject is the code, so editing prose must not buy
        // another review.
        SignalKey before = pullRequest(
            TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
            "abc123",
            "Add cache",
            "faster"
        ).orElseThrow();
        SignalKey after = pullRequest(
            TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
            "abc123",
            "Add cache",
            "cuts p99 by 40%"
        ).orElseThrow();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void shouldReReviewAfterAPush() {
        SignalKey before = pullRequest(
            TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
            "abc123",
            "Add cache",
            "body"
        ).orElseThrow();
        SignalKey after = pullRequest(
            TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
            "def456",
            "Add cache",
            "body"
        ).orElseThrow();

        assertThat(after.revision()).isNotEqualTo(before.revision());
    }

    @Test
    void shouldMakeARedeliveredMergeInert() {
        // A merge happens once; keying it on anything that can move would let a redelivery spend a second review.
        SignalKey merged = pullRequest(
            TriggerEventNames.PULL_REQUEST_MERGED,
            "abc123",
            "Add cache",
            "body"
        ).orElseThrow();
        SignalKey redelivered = pullRequest(
            TriggerEventNames.PULL_REQUEST_MERGED,
            "def456",
            "Add cache",
            "body"
        ).orElseThrow();

        assertThat(redelivered).isEqualTo(merged);
    }

    @Test
    void shouldKeepMergedAndClosedApart() {
        SignalKey merged = pullRequest(TriggerEventNames.PULL_REQUEST_MERGED, "abc123", "t", "b").orElseThrow();
        SignalKey closed = pullRequest(TriggerEventNames.PULL_REQUEST_CLOSED, "abc123", "t", "b").orElseThrow();

        assertThat(closed).isNotEqualTo(merged);
    }

    @Test
    void shouldDeclineToKeyACodeShapedSignalWithNoHeadCommit() {
        // Better no ledger row than one keyed on nothing, which would deduplicate away every later occurrence.
        assertThat(pullRequest(TriggerEventNames.PULL_REQUEST_READY, null, "t", "b")).isEmpty();
    }

    @Test
    void shouldDeriveTheArtifactKindFromTheSignalRatherThanTheCaller() {
        SignalKey key = pullRequest(TriggerEventNames.PULL_REQUEST_READY, "abc123", "t", "b").orElseThrow();

        assertThat(key.artifactKind()).isEqualTo(ScmSignals.PULL_REQUEST);
    }

    @Test
    void shouldRoundTripEverySignalBackToTheTriggerEventThatRaisedIt() {
        // The reaper re-runs the gate from a stored signal; one that cannot name its trigger can never be re-offered.
        for (String triggerEvent : new String[] {
            TriggerEventNames.PULL_REQUEST_CREATED,
            TriggerEventNames.PULL_REQUEST_READY,
            TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
            TriggerEventNames.REVIEW_SUBMITTED,
            TriggerEventNames.PULL_REQUEST_MERGED,
            TriggerEventNames.PULL_REQUEST_CLOSED,
            TriggerEventNames.ISSUE_CREATED,
            TriggerEventNames.ISSUE_LABELED,
            TriggerEventNames.ISSUE_CLOSED,
        }) {
            var signal = ScmSignals.forTriggerEvent(triggerEvent).orElseThrow();
            assertThat(ScmSignals.triggerEventFor(signal)).as(triggerEvent).contains(triggerEvent);
        }
    }

    @Test
    void shouldGiveEveryDeclaredSignalARevisionScheme() {
        for (var signal : new de.tum.cit.aet.hephaestus.integration.core.signal.SignalName[] {
            ScmSignals.PULL_REQUEST_OPENED,
            ScmSignals.PULL_REQUEST_READY,
            ScmSignals.PULL_REQUEST_SYNCHRONIZED,
            ScmSignals.PULL_REQUEST_REVIEWED,
            ScmSignals.PULL_REQUEST_MERGED,
            ScmSignals.PULL_REQUEST_CLOSED,
            ScmSignals.PULL_REQUEST_MANUAL_REVIEW,
            ScmSignals.ISSUE_OPENED,
            ScmSignals.ISSUE_LABELED,
            ScmSignals.ISSUE_CLOSED,
            ScmSignals.ISSUE_MANUAL_REVIEW,
        }) {
            assertThat(ScmSignals.revisionScheme(signal)).as(signal.value()).isNotNull();
        }
        assertThat(ScmSignals.revisionScheme(ScmSignals.ISSUE_OPENED)).isEqualTo(RevisionScheme.CONTENT_DIGEST);
        assertThat(ScmSignals.revisionScheme(ScmSignals.PULL_REQUEST_READY)).isEqualTo(RevisionScheme.HEAD_COMMIT);
    }
}
