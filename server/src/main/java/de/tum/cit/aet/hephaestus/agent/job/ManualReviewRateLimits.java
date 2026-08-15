package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignalRepository;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * How often a review may be asked for by hand, on one artifact and by one person.
 *
 * <p>Neither limit is served by the cooldown the workspace already configures. That one is enforced by
 * {@link AgentJobService} against an agent-job idempotency key whose phase segment is the trigger
 * signal, so a hand-requested review — which carries no trigger signal and lands in the {@code manual}
 * phase — occupies a cooldown lane of its own and never collides with the lifecycle review it repeats.
 * A request is also not caught by the partial unique index on in-flight jobs, which settles a double
 * click and nothing slower.
 *
 * <h2>Two limits, because they bound different harms</h2>
 * <ul>
 *   <li><b>Per artifact.</b> Re-asking about the same merge request produces the same review of the
 *       same commit at full price. This one reuses the workspace's own cooldown setting rather than
 *       inventing a second knob: an operator who has said how often this workspace re-reviews a piece
 *       of work has already answered the question.</li>
 *   <li><b>Per person.</b> The one limit keyed on who is asking rather than on what is being asked
 *       about. Without it, asking for one review each of twenty colleagues' merge requests passes every
 *       artifact-keyed check while being precisely the pattern that turns coaching into nagging — and
 *       the feedback lands on those colleagues, not on the person who asked.</li>
 * </ul>
 *
 * <h2>Refused before anything is recorded</h2>
 * <p>A request that trips either limit leaves no ledger row. The ledger's manual rows are what these
 * limits count, so recording refusals would make the population self-inflating: each declined ask would
 * tighten the allowance for the next honest one, and a person who hit the limit once would be pushed
 * further past it by their own retries. The asker still gets the sentence saying which limit it was —
 * the refusal is legible, it is simply not an occurrence.
 */
@Component
class ManualReviewRateLimits {

    private static final Duration REQUESTER_WINDOW = Duration.ofHours(1);

    private final ArtifactSignalRepository signals;
    private final PracticeReviewProperties reviewProperties;

    ManualReviewRateLimits(ArtifactSignalRepository signals, PracticeReviewProperties reviewProperties) {
        this.signals = signals;
        this.reviewProperties = reviewProperties;
    }

    /**
     * The reason to refuse this request, or empty to let it through.
     *
     * @param requesterIds every SCM identity of the person asking, so a linked account does not get one
     *     allowance per provider. Never empty: {@link ReviewRequestAuthority} has already refused an ask
     *     it could not attribute to an identity.
     */
    Optional<SignalStateReason> refusalFor(
        Workspace workspace,
        ArtifactKind artifactKind,
        long artifactId,
        Collection<Long> requesterIds
    ) {
        Instant now = Instant.now();
        if (requesterQuotaExhausted(workspace.getId(), requesterIds, now)) {
            return Optional.of(SignalStateReason.REQUESTER_QUOTA_EXHAUSTED);
        }
        if (artifactAskedForRecently(workspace, artifactKind, artifactId, now)) {
            return Optional.of(SignalStateReason.REQUEST_COOLDOWN_ACTIVE);
        }
        return Optional.empty();
    }

    /**
     * The per-person limit, counted within the workspace whose budget the review would spend.
     *
     * <p>Per workspace rather than per instance because the thing being rationed — attention paid to
     * one team's work, out of one team's purse — is workspace-shaped, and a global counter would let
     * one busy workspace's legitimate use lock a person out of every other workspace they belong to.
     */
    private boolean requesterQuotaExhausted(long workspaceId, Collection<Long> requesterIds, Instant now) {
        int allowance = reviewProperties.maxRequestsPerRequesterPerHour();
        if (allowance <= 0) {
            return false;
        }
        return signals.countRequestsBySince(workspaceId, requesterIds, now.minus(REQUESTER_WINDOW)) >= allowance;
    }

    /** The per-artifact limit, at whatever cooldown this workspace runs reviews on. */
    private boolean artifactAskedForRecently(Workspace workspace, ArtifactKind kind, long artifactId, Instant now) {
        int cooldownMinutes = workspace.getReviewSettings().resolveCooldownMinutes(reviewProperties.cooldownMinutes());
        if (cooldownMinutes <= 0) {
            return false;
        }
        Instant since = now.minus(Duration.ofMinutes(cooldownMinutes));
        return signals.existsManualRequestSince(workspace.getId(), kind.value(), artifactId, since);
    }
}
