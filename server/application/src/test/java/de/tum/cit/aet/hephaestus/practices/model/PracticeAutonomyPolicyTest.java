package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Feedback admission")
class PracticeAutonomyPolicyTest extends BaseUnitTest {

    @Nested
    @DisplayName("Provenance")
    class Provenance {

        @Test
        void aBackfilledResultIsRefusedOnBothInPlaceChannels() {
            for (PracticeAutonomy autonomy : PracticeAutonomy.values()) {
                for (FeedbackChannel channel : new FeedbackChannel[] {
                    FeedbackChannel.IN_CONTEXT, FeedbackChannel.IN_CHAT,
                }) {
                    assertThat(PracticeAutonomyPolicy.delivers(ResultOrigin.BACKFILL, autonomy, channel))
                            .as("BACKFILL at autonomy %s, on channel %s", autonomy, channel)
                            .isFalse();
                }
            }
        }

        @Test
        void aBackfillIsEntitledToTheInAppChannelAndOnlyThat() {
            assertThat(ResultOrigin.BACKFILL.delivers(FeedbackChannel.IN_APP)).isTrue();
            assertThat(ResultOrigin.BACKFILL.delivers(FeedbackChannel.IN_CONTEXT))
                    .isFalse();
            assertThat(ResultOrigin.BACKFILL.delivers(FeedbackChannel.IN_CHAT)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(
                value = ResultOrigin.class,
                names = {"LIVE", "MANUAL"})
        void aMeasurementOfWorkAsItHappenedLeavesEveryChannelToTheTier(ResultOrigin origin) {
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
                            ResultOrigin.LIVE, PracticeAutonomy.AUTOMATIC, FeedbackChannel.IN_CONTEXT))
                    .isTrue();
            assertThat(PracticeAutonomyPolicy.delivers(
                            ResultOrigin.LIVE, PracticeAutonomy.HUMAN_APPROVAL, FeedbackChannel.IN_CONTEXT))
                    .isFalse();
            assertThat(PracticeAutonomyPolicy.delivers(
                            ResultOrigin.BACKFILL, PracticeAutonomy.AUTOMATIC, FeedbackChannel.IN_CONTEXT))
                    .isFalse();
        }

        /** HUMAN_APPROVAL gates pushed feedback, not on-demand practice pages or chat. */
        @ParameterizedTest
        @CsvSource({
            "OFF, IN_CONTEXT, false",
            "OFF, IN_APP, false",
            "OFF, IN_CHAT, false",
            "HUMAN_APPROVAL, IN_CONTEXT, false",
            "HUMAN_APPROVAL, IN_APP, true",
            "HUMAN_APPROVAL, IN_CHAT, true",
            "AUTOMATIC, IN_CONTEXT, true",
            "AUTOMATIC, IN_APP, true",
            "AUTOMATIC, IN_CHAT, true",
        })
        void approvalGatesOnlyThePushedChannel(PracticeAutonomy autonomy, FeedbackChannel channel, boolean delivers) {
            assertThat(autonomy.delivers(channel)).isEqualTo(delivers);
            for (ResultOrigin origin : new ResultOrigin[] {ResultOrigin.LIVE, ResultOrigin.MANUAL}) {
                assertThat(PracticeAutonomyPolicy.delivers(origin, autonomy, channel))
                        .as("%s at %s on %s", origin, autonomy, channel)
                        .isEqualTo(delivers);
            }
        }

        @Test
        void onlyTheInContextChannelIsPushed() {
            assertThat(FeedbackChannel.IN_CONTEXT.pushed()).isTrue();
            assertThat(FeedbackChannel.IN_APP.pushed()).isFalse();
            assertThat(FeedbackChannel.IN_CHAT.pushed()).isFalse();
        }

        @Test
        void theOriginRuleStillBoundsThePullLanes() {
            // A backfill may reach the practice page under HUMAN_APPROVAL, and never the chat.
            assertThat(PracticeAutonomyPolicy.delivers(
                            ResultOrigin.BACKFILL, PracticeAutonomy.HUMAN_APPROVAL, FeedbackChannel.IN_APP))
                    .isTrue();
            assertThat(PracticeAutonomyPolicy.delivers(
                            ResultOrigin.BACKFILL, PracticeAutonomy.HUMAN_APPROVAL, FeedbackChannel.IN_CHAT))
                    .isFalse();
        }

        @Test
        void shouldFailClosedWhenAutonomyCannotBeResolved() {
            for (FeedbackChannel channel : FeedbackChannel.values()) {
                assertThat(PracticeAutonomyPolicy.delivers(ResultOrigin.LIVE, null, channel))
                        .as("unresolved autonomy on %s", channel)
                        .isFalse();
            }
            assertThat(PracticeAutonomyPolicy.delivers(ResultOrigin.BACKFILL, null, FeedbackChannel.IN_CONTEXT))
                    .isFalse();
        }
    }
}
