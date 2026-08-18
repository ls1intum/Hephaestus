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

        /** Areas are optional — the FK is {@code ON DELETE SET NULL} — so an unfiled practice is normal. */
        @Test
        void aPracticeWithNoAreaFallsStraightThroughToTheWorkspace() {
            Practice practice = new Practice();
            practice.setReviewTier(null);
            practice.setArea(null);

            EffectiveReviewTier resolved = ReviewTierResolver.resolvePractice(practice, PracticeReviewTier.OFF);
            assertThat(resolved.tier()).isEqualTo(PracticeReviewTier.OFF);
            assertThat(resolved.source()).isEqualTo(ReviewTierSource.WORKSPACE);
        }

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
         * The migration nulls out every practice whose migrated tier equals this value, which is only
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
         * Separate overloads, each with its own caller, so a divergence would stay invisible until two
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
         * The source says which level answered; it does not say whether the level being described
         * inherited. Reading "inherited" off the source instead of off the presence of an override is how
         * an area that decided for itself gets rendered as though it had not.
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
