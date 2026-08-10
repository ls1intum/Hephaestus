package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Autonomy tiers")
class PracticeReviewTierTest extends BaseUnitTest {

    @Nested
    @DisplayName("admission")
    class Admission {

        @Test
        void offIsTheOnlyTierThatStopsAReview() {
            assertThat(PracticeReviewTier.OFF.admitsReview()).isFalse();
            assertThat(PracticeReviewTier.OBSERVE.admitsReview()).isTrue();
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
            assertThat(PracticeReviewTier.OBSERVE.deliversWithoutApproval()).isFalse();
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
                PracticeReviewTier.OBSERVE,
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
    @DisplayName("selectability")
    class Selectability {

        /**
         * PROPOSE is declared, admitted by the DB CHECK, and refused at every write boundary until an
         * approval queue exists. A practice parked there would prepare feedback nobody can approve and
         * swallow it — worse than the tier not being offered at all.
         *
         * <p>This test is the one line to delete when the queue ships.
         */
        @Test
        void proposeIsTheOnlyTierAnAdministratorMayNotChooseYet() {
            assertThat(PracticeReviewTier.PROPOSE.selectable()).isFalse();
            assertThat(PracticeReviewTier.OFF.selectable()).isTrue();
            assertThat(PracticeReviewTier.OBSERVE.selectable()).isTrue();
            assertThat(PracticeReviewTier.DELIVER.selectable()).isTrue();
        }

        /**
         * Declared but unselectable is a deliberate and temporary state, so it is pinned to exactly one
         * constant. A second unselectable tier means somebody has started using this as a general escape
         * hatch for shipping vocabulary ahead of behaviour.
         */
        @ParameterizedTest
        @EnumSource(PracticeReviewTier.class)
        void nothingElseIsShippedAheadOfItsBehaviour(PracticeReviewTier tier) {
            assertThat(tier.selectable()).isEqualTo(tier != PracticeReviewTier.PROPOSE);
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
