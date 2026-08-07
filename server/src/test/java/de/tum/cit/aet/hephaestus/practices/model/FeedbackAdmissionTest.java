package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The whole delivery predicate: which measurements may be said out loud, where. */
@DisplayName("Feedback admission")
class FeedbackAdmissionTest extends BaseUnitTest {

    @Nested
    @DisplayName("Provenance")
    class Provenance {

        /**
         * The load-bearing decision of this slice, written as a derivation rather than as a constant.
         *
         * <p>A backfilled observation is entitled to PROFILE and to nothing else, and PROFILE has no
         * producer anywhere in {@code src/main} — so a backfill is, today, measured and delivered
         * nowhere. Recording that as "backfills produce no feedback" would be a claim about a decision;
         * recording it this way makes it a consequence of two facts that are each independently true and
         * each independently tested.
         *
         * <p><strong>This test fails the day a PROFILE producer appears.</strong> It cannot appear
         * without {@code PracticeReviewTier.delivers(PROFILE)} becoming true for some tier, which trips
         * {@code PracticeReviewTierTest.noTierClaimsTheUnwrittenProfileChannel} first. Whoever builds
         * that surface then has to decide deliberately whether a retrospective measurement belongs on it
         * — which is the decision this pins open rather than answers.
         */
        @Test
        void aBackfilledObservationReachesNoChannelThatAnyoneCanWriteToToday() {
            for (PracticeReviewTier tier : PracticeReviewTier.values()) {
                for (FeedbackChannel channel : FeedbackChannel.values()) {
                    assertThat(FeedbackAdmission.delivers(ObservationOrigin.BACKFILL, tier, channel))
                        .as("BACKFILL at tier %s on channel %s", tier, channel)
                        .isFalse();
                }
            }
        }

        /** …and the reason is specifically PROFILE's, not a blanket refusal we would forget to revisit. */
        @Test
        void aBackfillIsEntitledToTheProfileChannelAndOnlyThat() {
            assertThat(ObservationOrigin.BACKFILL.delivers(FeedbackChannel.PROFILE)).isTrue();
            assertThat(ObservationOrigin.BACKFILL.delivers(FeedbackChannel.IN_CONTEXT)).isFalse();
            assertThat(ObservationOrigin.BACKFILL.delivers(FeedbackChannel.CONVERSATION)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = ObservationOrigin.class, names = { "LIVE", "MANUAL" })
        void aMeasurementOfWorkAsItHappenedLeavesEveryChannelToTheTier(ObservationOrigin origin) {
            assertThat(Arrays.stream(FeedbackChannel.values()).allMatch(origin::delivers)).isTrue();
        }
    }

    @Nested
    @DisplayName("Conjunction")
    class Conjunction {

        @Test
        void bothAxesMustAdmitAChannel() {
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.LIVE,
                    PracticeReviewTier.ENGAGE,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isTrue();
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.LIVE,
                    PracticeReviewTier.MEASURE,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isFalse();
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.BACKFILL,
                    PracticeReviewTier.ENGAGE,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isFalse();
        }

        /**
         * An unresolved tier admits, because withholding feedback a developer was owed on the strength of
         * a lookup miss is the worse failure. The provenance rule has no such escape hatch — it is known
         * without a lookup, so a null tier cannot smuggle a backfill onto the artifact.
         */
        @Test
        void anUnresolvedTierDoesNotUndoTheProvenanceRule() {
            assertThat(FeedbackAdmission.delivers(ObservationOrigin.LIVE, null, FeedbackChannel.IN_CONTEXT)).isTrue();
            assertThat(
                FeedbackAdmission.delivers(ObservationOrigin.BACKFILL, null, FeedbackChannel.IN_CONTEXT)
            ).isFalse();
        }
    }
}
