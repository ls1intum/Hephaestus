package de.tum.cit.aet.hephaestus.practices.feedback;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Pure delivery-policy resolver. A nullable fact is not applicable at this decision point; once a
 * check denies delivery, later checks are retained as {@code NOT_REACHED} so traces remain complete.
 */
public final class DeliveryPolicyResolver {

    public static final String VERSION = "1";

    private DeliveryPolicyResolver() {}

    public static Result resolve(Facts facts) {
        List<Candidate> candidates = List.of(
            new Candidate(
                DeliveryPolicyCheck.INSTANCE_SILENT_MODE,
                facts.instanceMayDeliver(),
                FeedbackSuppressionReason.INSTANCE_SILENCED
            ),
            new Candidate(
                DeliveryPolicyCheck.WORKSPACE_ENABLED,
                facts.workspaceEnabled(),
                FeedbackSuppressionReason.WORKSPACE_DISABLED
            ),
            new Candidate(
                DeliveryPolicyCheck.ROLLOUT_REVISION,
                facts.rolloutCurrent(),
                facts.externalDeliveryAllowed()
                    ? FeedbackSuppressionReason.STALE_ROLLOUT_REVISION
                    : FeedbackSuppressionReason.ADMINISTRATIVE_INTERNAL_ONLY
            ),
            new Candidate(
                DeliveryPolicyCheck.WORKSPACE_DELIVERY,
                facts.deliveryActive(),
                FeedbackSuppressionReason.WORKSPACE_DELIVERY_PAUSED
            ),
            new Candidate(
                DeliveryPolicyCheck.CURRENT_COVERAGE,
                facts.currentlyCovered(),
                FeedbackSuppressionReason.OUTSIDE_CURRENT_COVERAGE
            ),
            new Candidate(
                DeliveryPolicyCheck.PRACTICE_AUTHORITY,
                facts.practiceAuthority(),
                FeedbackSuppressionReason.PRACTICE_REQUIRES_APPROVAL
            ),
            new Candidate(
                DeliveryPolicyCheck.HUMAN_APPROVAL,
                facts.humanApproval(),
                FeedbackSuppressionReason.PRACTICE_REQUIRES_APPROVAL
            ),
            new Candidate(
                DeliveryPolicyCheck.RECIPIENT_CONSENT,
                facts.recipientConsent(),
                FeedbackSuppressionReason.RECIPIENT_OPTED_OUT
            ),
            new Candidate(
                DeliveryPolicyCheck.ARTIFACT_ELIGIBILITY,
                facts.artifactEligible(),
                facts.artifactRefusal() == null ? FeedbackSuppressionReason.ARTIFACT_GONE : facts.artifactRefusal()
            )
        );

        List<CheckResult> checks = new ArrayList<>(candidates.size());
        FeedbackSuppressionReason refusal = null;
        for (Candidate candidate : candidates) {
            if (refusal != null) {
                checks.add(new CheckResult(candidate.check(), DeliveryPolicyCheckStatus.NOT_REACHED));
            } else if (candidate.fact() == null) {
                checks.add(new CheckResult(candidate.check(), DeliveryPolicyCheckStatus.NOT_APPLICABLE));
            } else if (candidate.fact()) {
                checks.add(new CheckResult(candidate.check(), DeliveryPolicyCheckStatus.PASSED));
            } else {
                refusal = candidate.refusal();
                checks.add(new CheckResult(candidate.check(), DeliveryPolicyCheckStatus.DENIED));
            }
        }
        return new Result(refusal == null, refusal, List.copyOf(checks));
    }

    public record Facts(
        boolean instanceMayDeliver,
        boolean workspaceEnabled,
        boolean externalDeliveryAllowed,
        @Nullable Boolean rolloutCurrent,
        @Nullable Boolean deliveryActive,
        @Nullable Boolean currentlyCovered,
        @Nullable Boolean practiceAuthority,
        @Nullable Boolean humanApproval,
        @Nullable Boolean recipientConsent,
        @Nullable Boolean artifactEligible,
        @Nullable FeedbackSuppressionReason artifactRefusal
    ) {}

    public record CheckResult(DeliveryPolicyCheck check, DeliveryPolicyCheckStatus status) {}

    public record Result(
        boolean allowed,
        @Nullable FeedbackSuppressionReason suppressionReason,
        List<CheckResult> checks
    ) {}

    private record Candidate(DeliveryPolicyCheck check, @Nullable Boolean fact, FeedbackSuppressionReason refusal) {}
}
