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

@DisplayName("Loudness tiers")
class PracticeReviewTierTest extends BaseUnitTest {

    @Nested
    @DisplayName("admission")
    class Admission {

        @Test
        void offIsTheOnlyTierThatStopsAReview() {
            assertThat(PracticeReviewTier.OFF.admitsReview()).isFalse();
            assertThat(PracticeReviewTier.MEASURE.admitsReview()).isTrue();
            assertThat(PracticeReviewTier.COACH.admitsReview()).isTrue();
            assertThat(PracticeReviewTier.ENGAGE.admitsReview()).isTrue();
        }
    }

    @Nested
    @DisplayName("delivery")
    class Delivery {

        @Test
        void onlyEngageReachesTheArtifact() {
            assertThat(PracticeReviewTier.OFF.delivers(FeedbackChannel.IN_CONTEXT)).isFalse();
            assertThat(PracticeReviewTier.MEASURE.delivers(FeedbackChannel.IN_CONTEXT)).isFalse();
            assertThat(PracticeReviewTier.COACH.delivers(FeedbackChannel.IN_CONTEXT)).isFalse();
            assertThat(PracticeReviewTier.ENGAGE.delivers(FeedbackChannel.IN_CONTEXT)).isTrue();
        }

        @Test
        void coachAndEngageReachTheConversation() {
            assertThat(PracticeReviewTier.OFF.delivers(FeedbackChannel.CONVERSATION)).isFalse();
            assertThat(PracticeReviewTier.MEASURE.delivers(FeedbackChannel.CONVERSATION)).isFalse();
            assertThat(PracticeReviewTier.COACH.delivers(FeedbackChannel.CONVERSATION)).isTrue();
            assertThat(PracticeReviewTier.ENGAGE.delivers(FeedbackChannel.CONVERSATION)).isTrue();
        }

        /**
         * PROFILE is a declared channel with no producer anywhere in {@code src/main} — nothing ever writes
         * a PROFILE feedback unit. COACH is therefore documented and implemented as conversation-only
         * rather than as "the quiet channels", because claiming a delivery the code cannot perform is the
         * failure mode this contract exists to prevent. This test is the guard: when a PROFILE producer is
         * built, it fails, and whoever builds it decides deliberately which tiers reach it.
         */
        @ParameterizedTest
        @EnumSource(PracticeReviewTier.class)
        void noTierClaimsTheUnwrittenProfileChannel(PracticeReviewTier tier) {
            assertThat(tier.delivers(FeedbackChannel.PROFILE)).isFalse();
        }

        /** Loudness is monotone: a louder tier delivers everywhere a quieter one does, and never less. */
        @Test
        void eachTierDeliversASupersetOfTheOneBelowIt() {
            PracticeReviewTier[] ascending = {
                PracticeReviewTier.OFF,
                PracticeReviewTier.MEASURE,
                PracticeReviewTier.COACH,
                PracticeReviewTier.ENGAGE,
            };
            for (int i = 1; i < ascending.length; i++) {
                for (FeedbackChannel channel : FeedbackChannel.values()) {
                    if (ascending[i - 1].delivers(channel)) {
                        assertThat(ascending[i].delivers(channel))
                            .as("%s must deliver %s because %s does", ascending[i], channel, ascending[i - 1])
                            .isTrue();
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("persistence shape")
    class PersistenceShape {

        /** The column and its check constraint are sized from this; a longer constant would truncate. */
        @Test
        void everyConstantFitsTheColumn() {
            assertThat(Arrays.stream(PracticeReviewTier.values()).map(Enum::name).map(String::length)).allSatisfy(
                length -> assertThat(length).isLessThanOrEqualTo(PracticeReviewTier.MAX_LENGTH)
            );
        }

        @Test
        void theDefaultIsTheLoudestTier() {
            assertThat(PracticeReviewTier.DEFAULT).isEqualTo(PracticeReviewTier.ENGAGE);
        }
    }
}
