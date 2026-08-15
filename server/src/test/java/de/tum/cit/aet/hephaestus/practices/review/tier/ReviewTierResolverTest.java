package de.tum.cit.aet.hephaestus.practices.review.tier;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.dto.ReviewTierAssignmentDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The practice → area → workspace chain. The one thing every gate, the catalogue, the trace and the rollup
 * all ask, so a mistake here is a mistake in all of them at once.
 */
@DisplayName("Review tier resolution")
class ReviewTierResolverTest extends BaseUnitTest {

    @Nested
    @DisplayName("precedence")
    class Precedence {

        @Test
        void aPracticeThatDecidedForItselfWins() {
            EffectiveReviewTier resolved = ReviewTierResolver.resolvePractice(
                PracticeReviewTier.OFF,
                PracticeReviewTier.DELIVER,
                PracticeReviewTier.DELIVER
            );
            assertThat(resolved.tier()).isEqualTo(PracticeReviewTier.OFF);
            assertThat(resolved.source()).isEqualTo(ReviewTierSource.PRACTICE);
        }

        @Test
        void aPracticeThatDecidedNothingTakesItsAreasAnswer() {
            EffectiveReviewTier resolved = ReviewTierResolver.resolvePractice(
                null,
                PracticeReviewTier.PROPOSE,
                PracticeReviewTier.DELIVER
            );
            assertThat(resolved.tier()).isEqualTo(PracticeReviewTier.PROPOSE);
            assertThat(resolved.source()).isEqualTo(ReviewTierSource.AREA);
        }

        @Test
        void whenNeitherDecidedTheWorkspaceAnswers() {
            EffectiveReviewTier resolved = ReviewTierResolver.resolvePractice(null, null, PracticeReviewTier.PROPOSE);
            assertThat(resolved.tier()).isEqualTo(PracticeReviewTier.PROPOSE);
            assertThat(resolved.source()).isEqualTo(ReviewTierSource.WORKSPACE);
        }

        /**
         * A practice in no area skips the middle level rather than failing. Areas are optional — the FK is
         * {@code ON DELETE SET NULL} — so an unfiled practice is a normal state, not a broken one.
         */
        @Test
        void aPracticeWithNoAreaFallsStraightThroughToTheWorkspace() {
            Practice practice = new Practice();
            practice.setReviewTier(null);
            practice.setArea(null);

            EffectiveReviewTier resolved = ReviewTierResolver.resolvePractice(practice, PracticeReviewTier.OFF);
            assertThat(resolved.tier()).isEqualTo(PracticeReviewTier.OFF);
            assertThat(resolved.source()).isEqualTo(ReviewTierSource.WORKSPACE);
        }

        /** Every level can hold every tier, and the nearest one that holds any always wins. */
        @ParameterizedTest
        @EnumSource(PracticeReviewTier.class)
        void theNearestDecisionWinsWhicheverTierItIs(PracticeReviewTier tier) {
            assertThat(ReviewTierResolver.resolvePractice(tier, null, PracticeReviewTier.DELIVER).tier()).isEqualTo(
                tier
            );
            assertThat(ReviewTierResolver.resolvePractice(null, tier, PracticeReviewTier.DELIVER).tier()).isEqualTo(
                tier
            );
            assertThat(ReviewTierResolver.resolvePractice(null, null, tier).tier()).isEqualTo(tier);
        }
    }

    @Nested
    @DisplayName("the bottom of the chain")
    class Bottom {

        /**
         * A workspace that never chose keeps doing what every practice already did. The migration relies on
         * this exactly: it nulls out every practice whose migrated tier equals this value, and that is only
         * behaviour-preserving because an unset workspace resolves back to it.
         */
        @Test
        void anUnsetWorkspaceResolvesToTheTierEveryPracticeAlreadyHad() {
            assertThat(ReviewTierResolver.workspaceDefault(null)).isEqualTo(PracticeReviewTier.DEFAULT);
            assertThat(ReviewTierResolver.workspaceDefault(null)).isEqualTo(PracticeReviewTier.DELIVER);
        }

        @Test
        void aWorkspaceThatChoseGetsWhatItChose() {
            assertThat(ReviewTierResolver.workspaceDefault(PracticeReviewTier.PROPOSE)).isEqualTo(
                PracticeReviewTier.PROPOSE
            );
        }
    }

    @Nested
    @DisplayName("areas")
    class Areas {

        @Test
        void anAreaResolvesItsOwnTierTheSameWay() {
            PracticeArea area = new PracticeArea();
            area.setReviewTier(PracticeReviewTier.OFF);

            EffectiveReviewTier resolved = ReviewTierResolver.resolveArea(area, PracticeReviewTier.DELIVER);
            assertThat(resolved.tier()).isEqualTo(PracticeReviewTier.OFF);
            assertThat(resolved.source()).isEqualTo(ReviewTierSource.AREA);
        }

        @Test
        void anAreaThatDecidedNothingReportsTheWorkspaceAsTheSource() {
            PracticeArea area = new PracticeArea();
            area.setReviewTier(null);

            EffectiveReviewTier resolved = ReviewTierResolver.resolveArea(area, PracticeReviewTier.PROPOSE);
            assertThat(resolved.tier()).isEqualTo(PracticeReviewTier.PROPOSE);
            assertThat(resolved.source()).isEqualTo(ReviewTierSource.WORKSPACE);
        }

        /**
         * Resolving a practice through its area entity must agree with resolving the two raw columns. They
         * are separate overloads and every caller picks one, so a divergence would be invisible until two
         * surfaces disagreed about the same practice.
         */
        @Test
        void resolvingThroughTheEntityAgreesWithResolvingTheColumns() {
            PracticeArea area = new PracticeArea();
            area.setReviewTier(PracticeReviewTier.PROPOSE);
            Practice practice = new Practice();
            practice.setReviewTier(null);
            practice.setArea(area);

            assertThat(ReviewTierResolver.resolvePractice(practice, PracticeReviewTier.DELIVER)).isEqualTo(
                ReviewTierResolver.resolvePractice(null, PracticeReviewTier.PROPOSE, PracticeReviewTier.DELIVER)
            );
            assertThat(ReviewTierResolver.effectiveTierOf(practice, PracticeReviewTier.DELIVER)).isEqualTo(
                PracticeReviewTier.PROPOSE
            );
        }
    }

    @Nested
    @DisplayName("what inherited means")
    class Inheritance {

        /**
         * The source says which level answered; it does not say whether the level being described inherited.
         * The two come apart exactly on an area that decided for itself: source {@code AREA}, and to that
         * area the value is its own. Reading "inherited" off the source instead of off the presence of an
         * override is how an area that had made a decision gets rendered as though it had not — offering a
         * reset for a value there is nothing to reset.
         */
        @Test
        void anAreaThatDecidedIsNotInheritingEvenThoughTheSourceIsArea() {
            PracticeArea area = new PracticeArea();
            area.setReviewTier(PracticeReviewTier.OFF);

            EffectiveReviewTier resolved = ReviewTierResolver.resolveArea(area, PracticeReviewTier.DELIVER);
            ReviewTierAssignmentDTO reported = ReviewTierAssignmentDTO.of(resolved, area.getReviewTier());

            assertThat(reported.source()).isEqualTo(ReviewTierSource.AREA);
            assertThat(reported.inherited()).isFalse();
            assertThat(reported.override()).isEqualTo(PracticeReviewTier.OFF);
        }

        /** The same source, one level down, IS an inheritance — same value, different level, different answer. */
        @Test
        void aPracticeUnderThatAreaIsInheritingFromTheSameSource() {
            PracticeArea area = new PracticeArea();
            area.setReviewTier(PracticeReviewTier.OFF);
            Practice practice = new Practice();
            practice.setReviewTier(null);
            practice.setArea(area);

            EffectiveReviewTier resolved = ReviewTierResolver.resolvePractice(practice, PracticeReviewTier.DELIVER);
            ReviewTierAssignmentDTO reported = ReviewTierAssignmentDTO.of(resolved, practice.getReviewTier());

            assertThat(reported.source()).isEqualTo(ReviewTierSource.AREA);
            assertThat(reported.inherited()).isTrue();
            assertThat(reported.override()).isNull();
        }
    }
}
