package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Practice autonomy")
class PracticeAutonomyTest extends BaseUnitTest {

    @Nested
    @DisplayName("admission")
    class Admission {

        @Test
        void offIsTheOnlyTierThatStopsAReview() {
            assertThat(FeedbackMode.OFF.admitsReview()).isFalse();
            assertThat(FeedbackMode.APPROVE_FIRST.admitsReview()).isTrue();
            assertThat(FeedbackMode.AUTOMATIC.admitsReview()).isTrue();
        }
    }

    @Nested
    @DisplayName("autonomy")
    class Autonomy {

        @Test
        void shouldOnlyDeliverAutomaticallyAtAutomaticAutonomy() {
            assertThat(FeedbackMode.OFF.deliversWithoutApproval()).isFalse();
            assertThat(FeedbackMode.APPROVE_FIRST.deliversWithoutApproval())
                    .isFalse();
            assertThat(FeedbackMode.AUTOMATIC.deliversWithoutApproval()).isTrue();
        }

        @Test
        void shouldRequireApprovalAtHumanApprovalAutonomyForThePushedChannelOnly() {
            assertThat(FeedbackMode.APPROVE_FIRST.deliversWithoutApproval())
                    .isFalse();
            assertThat(FeedbackMode.APPROVE_FIRST.delivers(FeedbackChannel.IN_CONTEXT))
                    .isFalse();
            assertThat(FeedbackMode.APPROVE_FIRST.delivers(FeedbackChannel.IN_APP))
                    .isTrue();
            assertThat(FeedbackMode.APPROVE_FIRST.delivers(FeedbackChannel.IN_CHAT))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("the shape of the ladder")
    class LadderShape {

        @Test
        void shouldExposeOnlyTheThreeCanonicalAutonomies() {
            assertThat(FeedbackMode.values())
                    .containsExactly(FeedbackMode.OFF, FeedbackMode.APPROVE_FIRST, FeedbackMode.AUTOMATIC);
        }
    }

    @Nested
    @DisplayName("persistence shape")
    class PersistenceShape {

        @Test
        void everyConstantFitsTheColumn() {
            assertThat(Arrays.stream(FeedbackMode.values()).map(Enum::name).map(String::length))
                    .allSatisfy(length -> assertThat(length).isLessThanOrEqualTo(FeedbackMode.MAX_LENGTH));
        }

        @Test
        void shouldFailSafeByDefault() {
            assertThat(FeedbackMode.DEFAULT).isEqualTo(FeedbackMode.APPROVE_FIRST);
        }
    }
}
