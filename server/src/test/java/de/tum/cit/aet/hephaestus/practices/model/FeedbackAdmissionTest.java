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
         * A backfilled observation is refused on both channels a producer writes to today, at every tier —
         * so a campaign is measured and delivered nowhere. The other half of that claim, that nothing
         * produces a {@code REFLECTION} unit, is pinned by {@code ReflectionChannelUnwrittenArchTest}.
         */
        @Test
        void aBackfilledObservationIsRefusedOnEveryChannelAProducerWritesToday() {
            for (PracticeReviewTier tier : PracticeReviewTier.values()) {
                for (FeedbackChannel channel : new FeedbackChannel[] {
                    FeedbackChannel.IN_CONTEXT,
                    FeedbackChannel.CONVERSATION,
                }) {
                    assertThat(FeedbackAdmission.delivers(ObservationOrigin.BACKFILL, tier, channel))
                        .as("BACKFILL at tier %s, on channel %s", tier, channel)
                        .isFalse();
                }
            }
        }

        /** …and the reason is specifically REFLECTION's, not a blanket refusal we would forget to revisit. */
        @Test
        void aBackfillIsEntitledToTheReflectionChannelAndOnlyThat() {
            assertThat(ObservationOrigin.BACKFILL.delivers(FeedbackChannel.REFLECTION)).isTrue();
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
            // Everything says yes.
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.LIVE,
                    PracticeReviewTier.DELIVER,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isTrue();
            // The tier alone refuses: no autonomy to act on its own.
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.LIVE,
                    PracticeReviewTier.PROPOSE,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isFalse();
            // The provenance alone refuses: a retrospective finding is not actionable on the work.
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.BACKFILL,
                    PracticeReviewTier.DELIVER,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isFalse();
        }

        /**
         * The tier is a decision about how much, never about where: a practice that is allowed to speak is
         * allowed to speak on every channel its provenance admits, and one that is not is silent on all of
         * them.
         */
        @Test
        void theTierAppliesUniformlyToEveryChannel() {
            for (FeedbackChannel channel : FeedbackChannel.values()) {
                assertThat(FeedbackAdmission.delivers(ObservationOrigin.LIVE, PracticeReviewTier.PROPOSE, channel))
                    .as("PROPOSE on channel %s", channel)
                    .isFalse();
                assertThat(FeedbackAdmission.delivers(ObservationOrigin.LIVE, PracticeReviewTier.DELIVER, channel))
                    .as("DELIVER on channel %s", channel)
                    .isTrue();
            }
        }

        /**
         * An unresolved tier admits <em>on its own axis</em>, because withholding feedback a developer was
         * owed on the strength of a lookup miss is the worse failure. The provenance rule has no such escape
         * hatch — it is known without a per-practice lookup, so a null tier cannot smuggle a backfill onto
         * the artifact.
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
