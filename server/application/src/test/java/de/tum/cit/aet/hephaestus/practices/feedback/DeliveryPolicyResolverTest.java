package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyResolver.FactAnswer;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DeliveryPolicyResolverTest extends BaseUnitTest {

    @Test
    void recordsEveryCheckInStableOrderWhenDeliveryIsAllowed() {
        DeliveryPolicyResolver.Result result = DeliveryPolicyResolver.resolve(everyCheckPasses());

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
                FactAnswer.PASSES,
                true,
                FactAnswer.PASSES,
                FactAnswer.PASSES,
                FactAnswer.PASSES,
                FactAnswer.DENIES,
                FactAnswer.DENIES,
                FactAnswer.DENIES,
                FeedbackSuppressionReason.ARTIFACT_CLOSED);

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
                        DeliveryPolicyCheckStatus.NOT_REACHED);
    }

    @Test
    void shouldKeepConsentAndArtifactStateAsIndependentVetoes() {
        DeliveryPolicyResolver.Facts optedOut =
                factsWith(FactAnswer.PASSES, FactAnswer.DENIES, FactAnswer.PASSES, null);
        DeliveryPolicyResolver.Facts closed = factsWith(
                FactAnswer.PASSES, FactAnswer.PASSES, FactAnswer.DENIES, FeedbackSuppressionReason.ARTIFACT_CLOSED);

        assertThat(DeliveryPolicyResolver.resolve(optedOut).suppressionReason())
                .isEqualTo(FeedbackSuppressionReason.RECIPIENT_OPTED_OUT);
        assertThat(DeliveryPolicyResolver.resolve(closed).suppressionReason())
                .isEqualTo(FeedbackSuppressionReason.ARTIFACT_CLOSED);
    }

    @Test
    void notApplicableChecksDoNotInterruptResolution() {
        DeliveryPolicyResolver.Result result = DeliveryPolicyResolver.resolve(
                factsWith(FactAnswer.NOT_APPLICABLE, FactAnswer.NOT_APPLICABLE, FactAnswer.PASSES, null));

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
            FactAnswer[] checks = new FactAnswer[expected.length];
            Arrays.fill(checks, FactAnswer.PASSES);
            checks[denied] = FactAnswer.DENIES;
            DeliveryPolicyResolver.Facts facts = new DeliveryPolicyResolver.Facts(
                    checks[0],
                    checks[1] == FactAnswer.PASSES,
                    checks[2],
                    checks[3],
                    checks[4],
                    checks[5],
                    checks[6],
                    checks[7],
                    null);

            assertThat(DeliveryPolicyResolver.resolve(facts).suppressionReason())
                    .isEqualTo(expected[denied]);
        }
    }

    private static DeliveryPolicyResolver.Facts everyCheckPasses() {
        return factsWith(FactAnswer.PASSES, FactAnswer.PASSES, FactAnswer.PASSES, null);
    }

    private static DeliveryPolicyResolver.Facts factsWith(
            FactAnswer authority,
            FactAnswer consent,
            FactAnswer artifact,
            @Nullable FeedbackSuppressionReason artifactReason) {
        return new DeliveryPolicyResolver.Facts(
                FactAnswer.PASSES,
                true,
                FactAnswer.PASSES,
                FactAnswer.PASSES,
                FactAnswer.PASSES,
                authority,
                consent,
                artifact,
                artifactReason);
    }
}
