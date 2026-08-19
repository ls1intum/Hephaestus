package de.tum.cit.aet.hephaestus.practices.review.autonomy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.dto.AutonomyAssignmentDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Review autonomy resolution")
class AutonomyResolverTest extends BaseUnitTest {

    @Nested
    @DisplayName("precedence")
    class Precedence {

        @Test
        void aPracticeThatDecidedForItselfWins() {
            EffectiveAutonomy resolved = AutonomyResolver.resolvePractice(
                PracticeAutonomy.OFF,
                PracticeAutonomy.AUTOMATIC,
                PracticeAutonomy.AUTOMATIC
            );
            assertThat(resolved.autonomy()).isEqualTo(PracticeAutonomy.OFF);
            assertThat(resolved.source()).isEqualTo(AutonomySource.PRACTICE);
        }

        @Test
        void aPracticeThatDecidedNothingTakesItsAreasAnswer() {
            EffectiveAutonomy resolved = AutonomyResolver.resolvePractice(
                null,
                PracticeAutonomy.HUMAN_APPROVAL,
                PracticeAutonomy.AUTOMATIC
            );
            assertThat(resolved.autonomy()).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(resolved.source()).isEqualTo(AutonomySource.AREA);
        }

        @Test
        void whenNeitherDecidedTheWorkspaceAnswers() {
            EffectiveAutonomy resolved = AutonomyResolver.resolvePractice(null, null, PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(resolved.autonomy()).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(resolved.source()).isEqualTo(AutonomySource.WORKSPACE);
        }

        @Test
        void aPracticeWithNoAreaFallsStraightThroughToTheWorkspace() {
            Practice practice = new Practice();
            practice.setAutonomy(null);
            practice.setArea(null);

            EffectiveAutonomy resolved = AutonomyResolver.resolvePractice(practice, PracticeAutonomy.OFF);
            assertThat(resolved.autonomy()).isEqualTo(PracticeAutonomy.OFF);
            assertThat(resolved.source()).isEqualTo(AutonomySource.WORKSPACE);
        }

        @ParameterizedTest
        @EnumSource(PracticeAutonomy.class)
        void theNearestDecisionWinsForEveryAutonomy(PracticeAutonomy autonomy) {
            assertThat(
                AutonomyResolver.resolvePractice(autonomy, null, PracticeAutonomy.AUTOMATIC).autonomy()
            ).isEqualTo(autonomy);
            assertThat(
                AutonomyResolver.resolvePractice(null, autonomy, PracticeAutonomy.AUTOMATIC).autonomy()
            ).isEqualTo(autonomy);
            assertThat(AutonomyResolver.resolvePractice(null, null, autonomy).autonomy()).isEqualTo(autonomy);
        }
    }

    @Nested
    @DisplayName("the bottom of the chain")
    class Bottom {

        @Test
        void anUnsetWorkspaceRequiresHumanApproval() {
            assertThat(AutonomyResolver.workspaceDefault(null)).isEqualTo(PracticeAutonomy.DEFAULT);
            assertThat(AutonomyResolver.workspaceDefault(null)).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
        }

        @Test
        void aWorkspaceThatChoseGetsWhatItChose() {
            assertThat(AutonomyResolver.workspaceDefault(PracticeAutonomy.HUMAN_APPROVAL)).isEqualTo(
                PracticeAutonomy.HUMAN_APPROVAL
            );
        }
    }

    @Nested
    @DisplayName("areas")
    class Areas {

        @Test
        void anAreaResolvesItsOwnTierTheSameWay() {
            PracticeArea area = new PracticeArea();
            area.setAutonomy(PracticeAutonomy.OFF);

            EffectiveAutonomy resolved = AutonomyResolver.resolveArea(area, PracticeAutonomy.AUTOMATIC);
            assertThat(resolved.autonomy()).isEqualTo(PracticeAutonomy.OFF);
            assertThat(resolved.source()).isEqualTo(AutonomySource.AREA);
        }

        @Test
        void anAreaThatDecidedNothingReportsTheWorkspaceAsTheSource() {
            PracticeArea area = new PracticeArea();
            area.setAutonomy(null);

            EffectiveAutonomy resolved = AutonomyResolver.resolveArea(area, PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(resolved.autonomy()).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(resolved.source()).isEqualTo(AutonomySource.WORKSPACE);
        }

        @Test
        void resolvingThroughTheEntityAgreesWithResolvingTheColumns() {
            PracticeArea area = new PracticeArea();
            area.setAutonomy(PracticeAutonomy.HUMAN_APPROVAL);
            Practice practice = new Practice();
            practice.setAutonomy(null);
            practice.setArea(area);

            assertThat(AutonomyResolver.resolvePractice(practice, PracticeAutonomy.AUTOMATIC)).isEqualTo(
                AutonomyResolver.resolvePractice(null, PracticeAutonomy.HUMAN_APPROVAL, PracticeAutonomy.AUTOMATIC)
            );
            assertThat(AutonomyResolver.effectiveAutonomyOf(practice, PracticeAutonomy.AUTOMATIC)).isEqualTo(
                PracticeAutonomy.HUMAN_APPROVAL
            );
        }
    }

    @Nested
    @DisplayName("what inherited means")
    class Inheritance {

        @Test
        void anAreaThatDecidedIsNotInheritingEvenThoughTheSourceIsArea() {
            PracticeArea area = new PracticeArea();
            area.setAutonomy(PracticeAutonomy.OFF);

            EffectiveAutonomy resolved = AutonomyResolver.resolveArea(area, PracticeAutonomy.AUTOMATIC);
            AutonomyAssignmentDTO reported = AutonomyAssignmentDTO.of(resolved, area.getAutonomy());

            assertThat(reported.source()).isEqualTo(AutonomySource.AREA);
            assertThat(reported.inherited()).isFalse();
            assertThat(reported.override()).isEqualTo(PracticeAutonomy.OFF);
        }

        @Test
        void aPracticeUnderThatAreaIsInheritingFromTheSameSource() {
            PracticeArea area = new PracticeArea();
            area.setAutonomy(PracticeAutonomy.OFF);
            Practice practice = new Practice();
            practice.setAutonomy(null);
            practice.setArea(area);

            EffectiveAutonomy resolved = AutonomyResolver.resolvePractice(practice, PracticeAutonomy.AUTOMATIC);
            AutonomyAssignmentDTO reported = AutonomyAssignmentDTO.of(resolved, practice.getAutonomy());

            assertThat(reported.source()).isEqualTo(AutonomySource.AREA);
            assertThat(reported.inherited()).isTrue();
            assertThat(reported.override()).isNull();
        }
    }
}
