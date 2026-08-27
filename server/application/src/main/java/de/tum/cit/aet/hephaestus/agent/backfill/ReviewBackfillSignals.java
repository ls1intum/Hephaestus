package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import java.util.Optional;

/**
 * The one signal a backfilled artifact is measured against: the signal its <em>current</em> state would
 * have raised. No draft, edit, or thread-resolution history is retained anywhere in this system, so a
 * campaign cannot replay history — only the artifact as it stands now, one occasion each.
 *
 * <p>Reusing the live vocabulary rather than inventing a {@code backfilled} signal keeps practices binding
 * {@code scm.pull_request.merged}, not a campaign-specific name, and lets the ledger's unique constraint
 * de-duplicate for free. The cost: a backfilled row and a live row for the same occurrence share a ledger
 * identity, and only {@code discovered_via} tells them apart.
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
                pullRequest.getBody());
    }

    static Optional<SignalKey> keyFor(long workspaceId, Issue issue) {
        SignalName signal = issue.getState() == Issue.State.CLOSED ? ScmSignals.ISSUE_CLOSED : ScmSignals.ISSUE_OPENED;
        return ScmSignals.issueKey(workspaceId, issue.getId(), signal, issue.getTitle(), issue.getBody(), null);
    }
}
