package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.*;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Activity Module Boundary Tests.
 *
 * <p>The activity module has a complex internal structure:
 * <ul>
 *   <li><b>activity root</b> - Core activity event handling and scoring</li>
 *   <li><b>activity.badpractice</b> - Bad practice entity persistence (repositories, feedback)</li>
 *   <li><b>activity.badpracticedetector</b> - Bad practice detection logic (scheduler, detector, event listeners)</li>
 *   <li><b>activity.model</b> - Activity domain models</li>
 *   <li><b>activity.scoring</b> - XP/scoring calculations</li>
 * </ul>
 *
 * <p>These tests enforce proper separation of concerns within the activity module.
 *
 * @see ArchitectureTestConstants
 */
@DisplayName("Activity Module Boundaries")
@Tag("architecture")
class ActivityModuleBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE_PACKAGE);
    }

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
                .allowEmptyShould(true)
                .because("Activity should not depend on leaderboard - use domain events for cross-module communication");
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
                .allowEmptyShould(true)
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
                .allowEmptyShould(true)
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
                .allowEmptyShould(true)
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
                .allowEmptyShould(true)
                .because("Activity should not depend on contributors");
            rule.check(classes);
        }
    }

    // ========================================================================
    // BAD PRACTICE SUBMODULE ISOLATION
    // ========================================================================

    @Nested
    @DisplayName("Bad Practice Submodule Isolation")
    class BadPracticeSubmoduleTests {

        /**
         * badpractice package (persistence) should not depend on badpracticedetector (logic).
         *
         * <p>The badpractice package contains repositories and feedback services.
         * The badpracticedetector package contains detection logic and schedulers.
         * Persistence layer should not depend on detection logic.
         */
        @Test
        @DisplayName("badpractice does not depend on badpracticedetector")
        void badpracticeDoesNotDependOnDetector() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..activity.badpractice..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..activity.badpracticedetector..")
                .allowEmptyShould(true)
                .because("Persistence layer (badpractice) should not depend on detection logic (badpracticedetector)");
            rule.check(classes);
        }

        /**
         * badpracticedetector can depend on badpractice repositories.
         *
         * <p>The detector uses repositories to persist detected bad practices.
         * This is the expected direction of dependency.
         */
        @Test
        @DisplayName("badpracticedetector may depend on badpractice (verify direction)")
        void verifyDetectorDependencyDirection() {
            // This test documents the expected dependency direction.
            // badpracticedetector → badpractice is allowed
            // badpractice → badpracticedetector is NOT allowed (tested above)
            ArchRule rule = classes()
                .that()
                .resideInAPackage("..activity.badpracticedetector..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                    "..activity..",
                    "..gitprovider..",
                    "..workspace..",
                    "..intelligenceservice..",
                    "..core..",
                    "..shared..",
                    "java..",
                    "jakarta..",
                    "org.springframework..",
                    "org.slf4j..",
                    "org.hibernate..",
                    ""  // primitives
                )
                .allowEmptyShould(true)
                .because("badpracticedetector should only depend on allowed packages");
            rule.check(classes);
        }

        /**
         * Bad practice detector should not depend on leaderboard.
         */
        @Test
        @DisplayName("badpracticedetector does not depend on leaderboard")
        void badpracticeDetectorDoesNotDependOnLeaderboard() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..activity.badpracticedetector..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..leaderboard..")
                .allowEmptyShould(true)
                .because("Bad practice detection is independent of leaderboard");
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
                .allowEmptyShould(true)
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
                .resideInAnyPackage(
                    "..leaderboard..",
                    "..mentor..",
                    "..notification..",
                    "..profile.."
                )
                .allowEmptyShould(true)
                .because("Scoring should be a pure calculation module");
            rule.check(classes);
        }
    }

    // ========================================================================
    // ACTIVITY MODEL ISOLATION
    // ========================================================================

    @Nested
    @DisplayName("Activity Model Isolation")
    class ActivityModelTests {

        /**
         * Activity models should not depend on service layer.
         *
         * <p>Models are pure domain objects - they should not have
         * dependencies on services which would create circular dependencies.
         */
        @Test
        @DisplayName("Activity models do not depend on services")
        void activityModelsDoNotDependOnServices() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..activity.model..")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Service")
                .allowEmptyShould(true)
                .because("Domain models should not depend on service layer");
            rule.check(classes);
        }

        /**
         * Activity models should not depend on controllers.
         */
        @Test
        @DisplayName("Activity models do not depend on controllers")
        void activityModelsDoNotDependOnControllers() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..activity.model..")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Controller")
                .allowEmptyShould(true)
                .because("Domain models should not depend on presentation layer");
            rule.check(classes);
        }
    }
}
