package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import java.util.Optional;

/**
 * The one signal a backfilled artifact is measured against: the signal its <em>current</em> state would
 * have raised.
 *
 * <p>A campaign cannot replay history and must not pretend to. No draft history, no edit history and no
 * thread-resolution timing is retained anywhere in this system, so there is no honest way to reconstruct
 * what a pull request looked like when it was opened, or what it looked like at review time. What the
 * mirror does hold is the artifact as it stands now, and that is exactly one occasion per artifact.
 *
 * <p>Reusing the live vocabulary rather than inventing a {@code backfilled} signal is what makes the rest
 * of the machinery work unchanged: practices bind {@code scm.pull_request.merged}, not
 * {@code scm.pull_request.backfilled}, so a campaign that named its own signal would match no practice
 * in any workspace and quietly review nothing. It also means the ledger's unique constraint does the
 * de-duplication for free — an artifact whose current-state signal was already recorded and settled is
 * one this campaign has nothing new to say about, and it is walked past rather than paid for twice.
 *
 * <p>The cost of that reuse, stated rather than hidden: a backfilled row and a live row for the same
 * occurrence are the same ledger identity, and only {@code discovered_via} tells them apart.
 */
final class ReviewBackfillSignals {

    private ReviewBackfillSignals() {}

    /**
     * The ledger identity for reviewing this pull request as it stands.
     *
     * <p>Empty when the mirror holds no head commit for a state whose signal is keyed on one — there is
     * then nothing stable to key the measurement to, and recording it under a made-up revision would
     * make the row un-deduplicable against the live path forever after.
     */
    static Optional<SignalKey> keyFor(long workspaceId, PullRequest pullRequest) {
        SignalName signal;
        if (pullRequest.isMerged()) {
            signal = ScmSignals.PULL_REQUEST_MERGED;
        } else if (pullRequest.getState() == Issue.State.CLOSED) {
            signal = ScmSignals.PULL_REQUEST_CLOSED;
        } else if (pullRequest.isDraft()) {
            // Still a draft, so the occasion it is at is "opened" — never "ready", which it has not
            // reached. Whether a draft is worth reviewing is then the binding's call, exactly as live.
            signal = ScmSignals.PULL_REQUEST_OPENED;
        } else {
            signal = ScmSignals.PULL_REQUEST_READY;
        }
        return ScmSignals.pullRequestKey(
            workspaceId,
            pullRequest.getId(),
            signal,
            pullRequest.getHeadRefOid(),
            pullRequest.getTitle(),
            pullRequest.getBody()
        );
    }

    /** The ledger identity for reviewing this issue as it stands. */
    static Optional<SignalKey> keyFor(long workspaceId, Issue issue) {
        SignalName signal = issue.getState() == Issue.State.CLOSED ? ScmSignals.ISSUE_CLOSED : ScmSignals.ISSUE_OPENED;
        return ScmSignals.issueKey(workspaceId, issue.getId(), signal, issue.getTitle(), issue.getBody(), null);
    }
}
