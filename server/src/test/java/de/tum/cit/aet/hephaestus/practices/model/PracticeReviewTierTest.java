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
         * The whole axis in one assertion: only the top tier may act without a person. The tier says how
         * far the system may go on its own; it says nothing about <em>where</em>, which is
         * {@code FeedbackReach}'s question and is asked separately.
         */
        @Test
        void onlyDeliverActsWithoutAHuman() {
            assertThat(PracticeReviewTier.OFF.deliversWithoutApproval()).isFalse();
            assertThat(PracticeReviewTier.PROPOSE.deliversWithoutApproval()).isFalse();
            assertThat(PracticeReviewTier.DELIVER.deliversWithoutApproval()).isTrue();
        }

        /**
         * PROPOSE answers no here and must keep answering no once the approval queue ships: delivering
         * something a person approved is a different act, recorded differently, and not this one. If this
         * test is ever changed to make PROPOSE deliver on its own, the tier has silently become DELIVER.
         */
        @Test
        void proposeStillDoesNotDeliverOnItsOwnOnceApprovalExists() {
            assertThat(PracticeReviewTier.PROPOSE.deliversWithoutApproval()).isFalse();
        }

        /** Autonomy is monotone: a higher tier does everything a lower one does, and never less. */
        @Test
        void eachTierDoesASupersetOfTheOneBelowIt() {
            PracticeReviewTier[] ascending = {
                PracticeReviewTier.OFF,
                PracticeReviewTier.PROPOSE,
                PracticeReviewTier.DELIVER,
            };
            for (int i = 1; i < ascending.length; i++) {
                if (ascending[i - 1].admitsReview()) {
                    assertThat(ascending[i].admitsReview())
                        .as("%s must admit a review because %s does", ascending[i], ascending[i - 1])
                        .isTrue();
                }
                if (ascending[i - 1].deliversWithoutApproval()) {
                    assertThat(ascending[i].deliversWithoutApproval())
                        .as("%s must deliver because %s does", ascending[i], ascending[i - 1])
                        .isTrue();
                }
            }
        }
    }

    @Nested
    @DisplayName("the shape of the ladder")
    class LadderShape {

        /**
         * Three modes, and an administrator may choose any of them. The ladder previously carried a fourth,
         * OBSERVE, which sat beside PROPOSE at "runs, records, sends nothing"; the two were told apart only
         * by whether feedback had been prepared, which nothing on the delivery path ever did. A rung nobody
         * can tell from its neighbour is not a rung, and a rung nobody can select is not a ladder — with
         * PROPOSE refused, OFF and DELIVER were an on/off switch wearing a ladder's clothes.
         *
         * <p>Pinned by exact value and order, so adding a rung or reordering one is a deliberate edit here
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

        /**
         * The bottom of the practice → area → workspace chain. It is the loudest tier on purpose: that is
         * what every practice did before the chain existed, and a migration must not change how loud a
         * running system is. Turning it down is now one decision instead of forty — but it is a decision
         * somebody makes, not one an upgrade makes for them.
         */
        @Test
        void theFallbackIsWhatEveryPracticeAlreadyDid() {
            assertThat(PracticeReviewTier.DEFAULT).isEqualTo(PracticeReviewTier.DELIVER);
        }
    }
}
