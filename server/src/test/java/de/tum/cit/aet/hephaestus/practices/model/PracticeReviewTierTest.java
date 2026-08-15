package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Autonomy tiers")
class PracticeReviewTierTest extends BaseUnitTest {

    @Nested
    @DisplayName("admission")
    class Admission {

        @Test
        void offIsTheOnlyTierThatStopsAReview() {
            assertThat(PracticeReviewTier.OFF.admitsReview()).isFalse();
            assertThat(PracticeReviewTier.PROPOSE.admitsReview()).isTrue();
            assertThat(PracticeReviewTier.DELIVER.admitsReview()).isTrue();
        }
    }

    @Nested
    @DisplayName("autonomy")
    class Autonomy {

        /**
         * The whole axis in one assertion: only the top tier may act without a person.
         */
        @Test
        void onlyDeliverActsWithoutAHuman() {
            assertThat(PracticeReviewTier.OFF.deliversWithoutApproval()).isFalse();
            assertThat(PracticeReviewTier.PROPOSE.deliversWithoutApproval()).isFalse();
            assertThat(PracticeReviewTier.DELIVER.deliversWithoutApproval()).isTrue();
        }

        /** If this ever flips to true, PROPOSE has silently become DELIVER. */
        @Test
        void proposeStillDoesNotDeliverOnItsOwnOnceApprovalExists() {
            assertThat(PracticeReviewTier.PROPOSE.deliversWithoutApproval()).isFalse();
        }
    }

    @Nested
    @DisplayName("the shape of the ladder")
    class LadderShape {

        /**
         * Pinned by exact value and order, so adding a rung or reordering one is a deliberate edit here
         * rather than a silent change to what every screen renders.
         */
        @Test
        void theLadderIsExactlyOffProposeDeliver() {
            assertThat(PracticeReviewTier.values()).containsExactly(
                PracticeReviewTier.OFF,
                PracticeReviewTier.PROPOSE,
                PracticeReviewTier.DELIVER
            );
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

        /** Mirrors {@link PracticeReviewTier#DEFAULT}: a migration must not change how loud a running system is. */
        @Test
        void theFallbackIsWhatEveryPracticeAlreadyDid() {
            assertThat(PracticeReviewTier.DEFAULT).isEqualTo(PracticeReviewTier.DELIVER);
        }
    }
}
