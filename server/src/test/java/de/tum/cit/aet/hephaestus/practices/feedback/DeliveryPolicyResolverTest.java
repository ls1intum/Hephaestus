package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DeliveryPolicyResolverTest extends BaseUnitTest {

    @Test
    void recordsEveryCheckInStableOrderWhenDeliveryIsAllowed() {
        DeliveryPolicyResolver.Result result = DeliveryPolicyResolver.resolve(allTrue());

        assertThat(result.allowed()).isTrue();
        assertThat(result.suppressionReason()).isNull();
        assertThat(result.checks())
            .extracting(DeliveryPolicyResolver.CheckResult::check)
            .containsExactly(DeliveryPolicyCheck.values());
        assertThat(result.checks()).allMatch(check -> check.status() == DeliveryPolicyCheckStatus.PASSED);
    }

    @Test
    void firstDenialWinsAndLeavesACompleteTrace() {
        DeliveryPolicyResolver.Facts facts = new DeliveryPolicyResolver.Facts(
            true,
            true,
            true,
            true,
            true,
            false,
            false,
            false,
            FeedbackSuppressionReason.ARTIFACT_CLOSED
        );

        DeliveryPolicyResolver.Result result = DeliveryPolicyResolver.resolve(facts);

        assertThat(result.suppressionReason()).isEqualTo(FeedbackSuppressionReason.PRACTICE_REQUIRES_APPROVAL);
        assertThat(result.checks())
            .extracting(DeliveryPolicyResolver.CheckResult::status)
            .containsExactly(
                DeliveryPolicyCheckStatus.PASSED,
                DeliveryPolicyCheckStatus.PASSED,
                DeliveryPolicyCheckStatus.PASSED,
                DeliveryPolicyCheckStatus.PASSED,
                DeliveryPolicyCheckStatus.PASSED,
                DeliveryPolicyCheckStatus.DENIED,
                DeliveryPolicyCheckStatus.NOT_REACHED,
                DeliveryPolicyCheckStatus.NOT_REACHED
            );
    }

    @Test
    void practiceAuthorityCannotBypassConsentOrArtifactState() {
        DeliveryPolicyResolver.Facts optedOut = factsWith(true, false, true, null);
        DeliveryPolicyResolver.Facts closed = factsWith(true, true, false, FeedbackSuppressionReason.ARTIFACT_CLOSED);

        assertThat(DeliveryPolicyResolver.resolve(optedOut).suppressionReason()).isEqualTo(
            FeedbackSuppressionReason.RECIPIENT_OPTED_OUT
        );
        assertThat(DeliveryPolicyResolver.resolve(closed).suppressionReason()).isEqualTo(
            FeedbackSuppressionReason.ARTIFACT_CLOSED
        );
    }

    @Test
    void notApplicableChecksDoNotInterruptResolution() {
        DeliveryPolicyResolver.Result result = DeliveryPolicyResolver.resolve(factsWith(null, null, true, null));

        assertThat(result.allowed()).isTrue();
        assertThat(result.checks())
            .filteredOn(check -> check.status() == DeliveryPolicyCheckStatus.NOT_APPLICABLE)
            .extracting(DeliveryPolicyResolver.CheckResult::check)
            .containsExactly(DeliveryPolicyCheck.PRACTICE_AUTHORITY, DeliveryPolicyCheck.RECIPIENT_CONSENT);
    }

    @Test
    void everyPolicyFactHasTheExpectedDecisivePrecedence() {
        FeedbackSuppressionReason[] expected = {
            FeedbackSuppressionReason.INSTANCE_SILENCED,
            FeedbackSuppressionReason.WORKSPACE_DISABLED,
            FeedbackSuppressionReason.STALE_ROLLOUT_REVISION,
            FeedbackSuppressionReason.WORKSPACE_DELIVERY_PAUSED,
            FeedbackSuppressionReason.OUTSIDE_CURRENT_COVERAGE,
            FeedbackSuppressionReason.PRACTICE_REQUIRES_APPROVAL,
            FeedbackSuppressionReason.RECIPIENT_OPTED_OUT,
            FeedbackSuppressionReason.ARTIFACT_GONE,
        };

        for (int denied = 0; denied < expected.length; denied++) {
            Boolean[] checks = new Boolean[expected.length];
            Arrays.fill(checks, true);
            checks[denied] = false;
            DeliveryPolicyResolver.Facts facts = new DeliveryPolicyResolver.Facts(
                checks[0],
                checks[1],
                checks[2],
                checks[3],
                checks[4],
                checks[5],
                checks[6],
                checks[7],
                null
            );

            assertThat(DeliveryPolicyResolver.resolve(facts).suppressionReason()).isEqualTo(expected[denied]);
        }
    }

    private static DeliveryPolicyResolver.Facts allTrue() {
        return factsWith(true, true, true, null);
    }

    private static DeliveryPolicyResolver.Facts factsWith(
        Boolean authority,
        Boolean consent,
        Boolean artifact,
        FeedbackSuppressionReason artifactReason
    ) {
        return new DeliveryPolicyResolver.Facts(
            true,
            true,
            true,
            true,
            true,
            authority,
            consent,
            artifact,
            artifactReason
        );
    }
}
