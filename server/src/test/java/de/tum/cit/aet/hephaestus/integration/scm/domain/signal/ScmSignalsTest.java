package de.tum.cit.aet.hephaestus.integration.scm.domain.signal;

import static de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.signal.RevisionScheme;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
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

    private Optional<SignalKey> issue(String triggerEvent, String title, String body, Instant updatedAt) {
        return ScmSignals.issueKey(
            WORKSPACE_ID,
            ARTIFACT_ID,
            ScmSignals.forTriggerEvent(triggerEvent).orElseThrow(),
            title,
            body,
            updatedAt
        );
    }

    @Test
    void shouldReMeasureAnIssueAfterItsAuthorRewritesTheDescription() {
        // The motivating case for per-signal revisions. An issue has no commits, so if its signals
        // keyed on anything code-shaped a practice about how issues are written could be measured once
        // and never again — exactly the coaching loop it exists for.
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
    void shouldTreatASecondLabellingOfUnchangedProseAsItsOwnOccurrence() {
        // A labelling's identity is the label set, which the payload does not carry. Keying it on the
        // prose alone would deduplicate every label after the first.
        SignalKey firstLabel = issue(
            TriggerEventNames.ISSUE_LABELED,
            "Bug",
            "it broke",
            Instant.parse("2026-01-01T10:00:00Z")
        ).orElseThrow();
        SignalKey secondLabel = issue(
            TriggerEventNames.ISSUE_LABELED,
            "Bug",
            "it broke",
            Instant.parse("2026-01-01T10:05:00Z")
        ).orElseThrow();

        assertThat(secondLabel.revision()).isNotEqualTo(firstLabel.revision());
    }

    @Test
    void shouldNotReReviewUnchangedCodeBecauseTheDescriptionWasEdited() {
        // The converse of the issue case: a push signal's subject is the code, so editing prose around
        // it must not buy another review.
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
        // A merge happens once. Keying it on anything that can move would let a provider's redelivery
        // spend a second review on the same landing.
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
        // Better no ledger row than one keyed on nothing: a row we cannot identify would deduplicate
        // away every later occurrence of the same signal.
        assertThat(pullRequest(TriggerEventNames.PULL_REQUEST_READY, null, "t", "b")).isEmpty();
    }

    @Test
    void shouldDeriveTheArtifactKindFromTheSignalRatherThanTheCaller() {
        SignalKey key = pullRequest(TriggerEventNames.PULL_REQUEST_READY, "abc123", "t", "b").orElseThrow();

        assertThat(key.artifactKind()).isEqualTo(ScmSignals.PULL_REQUEST);
    }

    @Test
    void shouldRoundTripEverySignalBackToTheTriggerEventThatRaisedIt() {
        // The reaper re-runs the gate from a stored signal, which needs the literal practices subscribe
        // to; a signal that cannot name its trigger can never be re-offered.
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
            ScmSignals.PULL_REQUEST_REVIEW_REQUESTED,
            ScmSignals.ISSUE_OPENED,
            ScmSignals.ISSUE_LABELED,
            ScmSignals.ISSUE_CLOSED,
            ScmSignals.ISSUE_REVIEW_REQUESTED,
        }) {
            assertThat(ScmSignals.revisionScheme(signal)).as(signal.value()).isNotNull();
        }
        assertThat(ScmSignals.revisionScheme(ScmSignals.ISSUE_OPENED)).isEqualTo(RevisionScheme.CONTENT_DIGEST);
        assertThat(ScmSignals.revisionScheme(ScmSignals.PULL_REQUEST_READY)).isEqualTo(RevisionScheme.HEAD_COMMIT);
    }
}
