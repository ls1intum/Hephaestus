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
 * How often a review may be asked for by hand, on one artifact and by one person. Neither limit is
 * served by the workspace's existing cooldown or the partial unique index on in-flight jobs: both key
 * off attributes a hand-requested review does not carry.
 *
 * <h2>Two limits, because they bound different harms</h2>
 * <ul>
 *   <li><b>Per artifact.</b> Reuses the workspace's own cooldown setting rather than inventing a second
 *       knob.</li>
 *   <li><b>Per person.</b> Without it, asking for one review each of twenty colleagues' merge requests
 *       passes every artifact-keyed check while being precisely the pattern that turns coaching into
 *       nagging.</li>
 * </ul>
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
     * The reason to refuse this request, or empty to let it through. A refusal is not recorded as a
     * ledger row, so retries do not tighten the next honest ask's allowance.
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
     * Counted within the workspace whose budget the review would spend, not globally, so one busy
     * workspace can't lock a person out of every other workspace they belong to.
     */
    private boolean requesterQuotaExhausted(long workspaceId, Collection<Long> requesterIds, Instant now) {
        int allowance = reviewProperties.maxRequestsPerRequesterPerHour();
        if (allowance <= 0) {
            return false;
        }
        return signals.countRequestsBySince(workspaceId, requesterIds, now.minus(REQUESTER_WINDOW)) >= allowance;
    }

    private boolean artifactAskedForRecently(Workspace workspace, ArtifactKind kind, long artifactId, Instant now) {
        int cooldownMinutes = workspace.getReviewSettings().resolveCooldownMinutes(reviewProperties.cooldownMinutes());
        if (cooldownMinutes <= 0) {
            return false;
        }
        Instant since = now.minus(Duration.ofMinutes(cooldownMinutes));
        return signals.existsManualRequestSince(workspace.getId(), kind.value(), artifactId, since);
    }
}
