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

@DisplayName("Feedback admission")
class PracticeAutonomyPolicyTest extends BaseUnitTest {

    @Nested
    @DisplayName("Provenance")
    class Provenance {

        @Test
        void aBackfilledObservationIsRefusedOnBothInPlaceChannels() {
            for (PracticeAutonomy autonomy : PracticeAutonomy.values()) {
                for (FeedbackChannel channel : new FeedbackChannel[] {
                    FeedbackChannel.IN_CONTEXT, FeedbackChannel.IN_CHAT,
                }) {
                    assertThat(PracticeAutonomyPolicy.delivers(ObservationOrigin.BACKFILL, autonomy, channel))
                            .as("BACKFILL at autonomy %s, on channel %s", autonomy, channel)
                            .isFalse();
                }
            }
        }

        @Test
        void aBackfillIsEntitledToTheInAppChannelAndOnlyThat() {
            assertThat(ObservationOrigin.BACKFILL.delivers(FeedbackChannel.IN_APP))
                    .isTrue();
            assertThat(ObservationOrigin.BACKFILL.delivers(FeedbackChannel.IN_CONTEXT))
                    .isFalse();
            assertThat(ObservationOrigin.BACKFILL.delivers(FeedbackChannel.IN_CHAT))
                    .isFalse();
        }

        @ParameterizedTest
        @EnumSource(
                value = ObservationOrigin.class,
                names = {"LIVE", "MANUAL"})
        void aMeasurementOfWorkAsItHappenedLeavesEveryChannelToTheTier(ObservationOrigin origin) {
            assertThat(Arrays.stream(FeedbackChannel.values()).allMatch(origin::delivers))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Conjunction")
    class Conjunction {

        @Test
        void bothAxesMustAdmitAChannel() {
            assertThat(PracticeAutonomyPolicy.delivers(
                            ObservationOrigin.LIVE, PracticeAutonomy.AUTOMATIC, FeedbackChannel.IN_CONTEXT))
                    .isTrue();
            assertThat(PracticeAutonomyPolicy.delivers(
                            ObservationOrigin.LIVE, PracticeAutonomy.HUMAN_APPROVAL, FeedbackChannel.IN_CONTEXT))
                    .isFalse();
            assertThat(PracticeAutonomyPolicy.delivers(
                            ObservationOrigin.BACKFILL, PracticeAutonomy.AUTOMATIC, FeedbackChannel.IN_CONTEXT))
                    .isFalse();
        }

        @Test
        void theTierAppliesUniformlyToEveryChannel() {
            for (FeedbackChannel channel : FeedbackChannel.values()) {
                assertThat(PracticeAutonomyPolicy.delivers(
                                ObservationOrigin.LIVE, PracticeAutonomy.HUMAN_APPROVAL, channel))
                        .as("HUMAN_APPROVAL on channel %s", channel)
                        .isFalse();
                assertThat(PracticeAutonomyPolicy.delivers(ObservationOrigin.LIVE, PracticeAutonomy.AUTOMATIC, channel))
                        .as("AUTOMATIC on channel %s", channel)
                        .isTrue();
            }
        }

        @Test
        void shouldFailClosedWhenAutonomyCannotBeResolved() {
            assertThat(PracticeAutonomyPolicy.delivers(ObservationOrigin.LIVE, null, FeedbackChannel.IN_CONTEXT))
                    .isFalse();
            assertThat(PracticeAutonomyPolicy.delivers(ObservationOrigin.BACKFILL, null, FeedbackChannel.IN_CONTEXT))
                    .isFalse();
        }
    }
}
