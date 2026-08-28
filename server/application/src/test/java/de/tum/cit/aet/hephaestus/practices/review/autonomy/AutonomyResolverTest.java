package de.tum.cit.aet.hephaestus.practices.review.autonomy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.dto.AutonomyAssignmentDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
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
        void aPracticeThatDecidedNothingTakesItsGroupsAnswer() {
            EffectiveAutonomy resolved = AutonomyResolver.resolvePractice(
                null,
                PracticeAutonomy.HUMAN_APPROVAL,
                PracticeAutonomy.AUTOMATIC
            );
            assertThat(resolved.autonomy()).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(resolved.source()).isEqualTo(AutonomySource.GROUP);
        }

        @Test
        void whenNeitherDecidedTheWorkspaceAnswers() {
            EffectiveAutonomy resolved = AutonomyResolver.resolvePractice(null, null, PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(resolved.autonomy()).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(resolved.source()).isEqualTo(AutonomySource.WORKSPACE);
        }

        @Test
        void aPracticeWithNoGroupFallsStraightThroughToTheWorkspace() {
            Practice practice = new Practice();
            practice.setAutonomy(null);
            practice.setGroup(null);

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
    @DisplayName("groups")
    class Groups {

        @Test
        void anGroupResolvesItsOwnTierTheSameWay() {
            PracticeGroup group = new PracticeGroup();
            group.setAutonomy(PracticeAutonomy.OFF);

            EffectiveAutonomy resolved = AutonomyResolver.resolveGroup(group, PracticeAutonomy.AUTOMATIC);
            assertThat(resolved.autonomy()).isEqualTo(PracticeAutonomy.OFF);
            assertThat(resolved.source()).isEqualTo(AutonomySource.GROUP);
        }

        @Test
        void anGroupThatDecidedNothingReportsTheWorkspaceAsTheSource() {
            PracticeGroup group = new PracticeGroup();
            group.setAutonomy(null);

            EffectiveAutonomy resolved = AutonomyResolver.resolveGroup(group, PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(resolved.autonomy()).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(resolved.source()).isEqualTo(AutonomySource.WORKSPACE);
        }

        @Test
        void resolvingThroughTheEntityAgreesWithResolvingTheColumns() {
            PracticeGroup group = new PracticeGroup();
            group.setAutonomy(PracticeAutonomy.HUMAN_APPROVAL);
            Practice practice = new Practice();
            practice.setAutonomy(null);
            practice.setGroup(group);

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
        void anGroupThatDecidedIsNotInheritingEvenThoughTheSourceIsGroup() {
            PracticeGroup group = new PracticeGroup();
            group.setAutonomy(PracticeAutonomy.OFF);

            EffectiveAutonomy resolved = AutonomyResolver.resolveGroup(group, PracticeAutonomy.AUTOMATIC);
            AutonomyAssignmentDTO reported = AutonomyAssignmentDTO.of(resolved, group.getAutonomy());

            assertThat(reported.source()).isEqualTo(AutonomySource.GROUP);
            assertThat(reported.inherited()).isFalse();
            assertThat(reported.override()).isEqualTo(PracticeAutonomy.OFF);
        }

        @Test
        void aPracticeUnderThatGroupIsInheritingFromTheSameSource() {
            PracticeGroup group = new PracticeGroup();
            group.setAutonomy(PracticeAutonomy.OFF);
            Practice practice = new Practice();
            practice.setAutonomy(null);
            practice.setGroup(group);

            EffectiveAutonomy resolved = AutonomyResolver.resolvePractice(practice, PracticeAutonomy.AUTOMATIC);
            AutonomyAssignmentDTO reported = AutonomyAssignmentDTO.of(resolved, practice.getAutonomy());

            assertThat(reported.source()).isEqualTo(AutonomySource.GROUP);
            assertThat(reported.inherited()).isTrue();
            assertThat(reported.override()).isNull();
        }
    }
}
