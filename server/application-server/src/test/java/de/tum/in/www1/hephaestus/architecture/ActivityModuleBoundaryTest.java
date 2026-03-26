package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.*;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Activity Module Boundary Tests.
 *
 * <p>The activity module has a focused internal structure:
 * <ul>
 *   <li><b>activity root</b> - Core activity event handling and leaderboard cache</li>
 *   <li><b>activity.scoring</b> - XP/scoring calculations</li>
 * </ul>
 *
 * <p>Note: Practice detection is in the separate <b>practices</b> module:
 * <ul>
 *   <li><b>practices.model</b> - Practice catalog entities</li>
 *   <li><b>practices.finding</b> - Practice findings and contributor feedback</li>
 *   <li><b>practices.review</b> - Detection and delivery gates for agent-based review</li>
 *   <li><b>practices.spi</b> - Service provider interfaces (UserRoleChecker, AgentConfigChecker)</li>
 * </ul>
 *
 * <p>These tests enforce proper separation of concerns within the activity module and practices module.
 *
 * @see ArchitectureTestConstants
 */
@DisplayName("Activity Module Boundaries")
class ActivityModuleBoundaryTest extends HephaestusArchitectureTest {

    // ========================================================================
    // ACTIVITY MODULE ISOLATION
    // ========================================================================

    @Nested
    @DisplayName("Activity Module Isolation")
    class ActivityModuleIsolationTests {

        /**
         * Activity module should not depend on leaderboard internals.
         *
         * <p>Activity and leaderboard are peer modules. Activity generates events
         * that leaderboard may consume, but should not have direct dependencies
         * on leaderboard's service layer.
         */
        @Test
        @DisplayName("Activity does not depend on leaderboard services")
        void activityDoesNotDependOnLeaderboardServices() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..activity..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..leaderboard..service..")
                .orShould()
                .dependOnClassesThat()
                .resideInAPackage("..leaderboard..repository..")
                .because(
                    "Activity should not depend on leaderboard - use domain events for cross-module communication"
                );
            rule.check(classes);
        }

        /**
         * Activity module should not depend on mentor module.
         *
         * <p>Activity tracks user actions; mentor provides AI feedback.
         * These are orthogonal concerns.
         */
        @Test
        @DisplayName("Activity does not depend on mentor module")
        void activityDoesNotDependOnMentor() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..activity..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..mentor..")
                .because("Activity and mentor are independent feature modules");
            rule.check(classes);
        }

        /**
         * Activity module should not depend on notification module.
         *
         * <p>Notifications are triggered by activity events through the event system,
         * not through direct dependencies.
         */
        @Test
        @DisplayName("Activity does not depend on notification module")
        void activityDoesNotDependOnNotification() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..activity..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..notification..")
                .because("Activity should use domain events to trigger notifications");
            rule.check(classes);
        }

        /**
         * Activity module should not depend on profile module.
         *
         * <p>Profile is a read-only view module that aggregates data from activity,
         * not the other way around.
         */
        @Test
        @DisplayName("Activity does not depend on profile module")
        void activityDoesNotDependOnProfile() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..activity..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..profile..")
                .because("Profile depends on activity, not vice versa");
            rule.check(classes);
        }

        /**
         * Activity module should not depend on contributors module.
         */
        @Test
        @DisplayName("Activity does not depend on contributors module")
        void activityDoesNotDependOnContributors() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..activity..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..contributors..")
                .because("Activity should not depend on contributors");
            rule.check(classes);
        }
    }

    // ========================================================================
    // ACTIVITY SCORING ISOLATION
    // ========================================================================

    @Nested
    @DisplayName("Activity Scoring Isolation")
    class ScoringSubmoduleTests {

        /**
         * Scoring package should be pure calculation logic.
         *
         * <p>The scoring package calculates XP - it should not have
         * direct dependencies on external services or controllers.
         */
        @Test
        @DisplayName("Scoring does not depend on controllers")
        void scoringDoesNotDependOnControllers() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..activity.scoring..")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Controller")
                .because("Scoring logic should be independent of presentation layer");
            rule.check(classes);
        }

        /**
         * Scoring should not depend on external feature modules.
         */
        @Test
        @DisplayName("Scoring has minimal external dependencies")
        void scoringHasMinimalExternalDependencies() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..activity.scoring..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..leaderboard..", "..mentor..", "..notification..", "..profile..")
                .because("Scoring should be a pure calculation module");
            rule.check(classes);
        }
    }

    // ========================================================================
    // PRACTICES MODULE CONTROLLER ISOLATION
    // ========================================================================

    @Nested
    @DisplayName("Practices Controller Isolation")
    class PracticesControllerTests {

        /**
         * Only PracticeCatalogController (CRUD), PracticeFindingController (contributor findings API),
         * and FindingFeedbackController (contributor feedback) are allowed as REST entry points
         * in the practices module.
         */
        @Test
        @DisplayName("Practices has dedicated controllers")
        void practicesHasDedicatedController() {
            ArchRule rule = classes()
                .that()
                .resideInAPackage("..practices..")
                .and()
                .haveSimpleNameEndingWith("Controller")
                .should()
                .haveSimpleName("PracticeCatalogController")
                .orShould()
                .haveSimpleName("PracticeFindingController")
                .orShould()
                .haveSimpleName("FindingFeedbackController")
                .because(
                    "Only PracticeCatalogController, PracticeFindingController, and FindingFeedbackController are allowed REST entry points"
                );
            rule.check(classes);
        }
    }
}
