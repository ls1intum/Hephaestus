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
 * <p>The activity module has a focused internal structure:
 * <ul>
 *   <li><b>activity root</b> - Core activity event handling and leaderboard cache</li>
 *   <li><b>activity.scoring</b> - XP/scoring calculations</li>
 * </ul>
 *
 * <p>Note: Bad practice detection is in the separate <b>practices</b> module:
 * <ul>
 *   <li><b>practices.model</b> - Bad practice entities (BadPracticeDetection, PullRequestBadPractice, etc.)</li>
 *   <li><b>practices.detection</b> - Detection logic (scheduler, detector, event listeners)</li>
 *   <li><b>practices.spi</b> - Service provider interfaces (BadPracticeNotificationSender, UserRoleChecker)</li>
 *   <li><b>practices.feedback</b> - Feedback handling</li>
 * </ul>
 *
 * <p>These tests enforce proper separation of concerns within the activity module and practices module.
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
    // PRACTICES MODULE ISOLATION
    // ========================================================================

    @Nested
    @DisplayName("Practices Module Isolation")
    class PracticesModuleTests {

        /**
         * practices.model package (persistence) should not depend on practices.detection (logic).
         *
         * <p>The practices.model package contains entities and repositories.
         * The practices.detection package contains detection logic and schedulers.
         * Model/persistence layer should not depend on detection logic.
         */
        @Test
        @DisplayName("practices.model does not depend on practices.detection")
        void practicesModelDoesNotDependOnDetection() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..practices.model..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..practices.detection..")
                .allowEmptyShould(true)
                .because("Model layer (practices.model) should not depend on detection logic (practices.detection)");
            rule.check(classes);
        }

        /**
         * practices.detection can depend on practices.model and practices.spi.
         *
         * <p>The detector uses model entities and SPI interfaces.
         * This is the expected direction of dependency.
         */
        @Test
        @DisplayName("practices.detection may depend on practices.model (verify direction)")
        void verifyDetectorDependencyDirection() {
            // This test documents the expected dependency direction.
            // practices.detection → practices.model is allowed
            // practices.model → practices.detection is NOT allowed (tested above)
            ArchRule rule = classes()
                .that()
                .resideInAPackage("..practices.detection..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                    "..practices..",
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
                    "lombok..", // Lombok-generated code
                    "" // primitives
                )
                .allowEmptyShould(true)
                .because("practices.detection should only depend on allowed packages");
            rule.check(classes);
        }

        /**
         * Practices detection should not depend on leaderboard.
         */
        @Test
        @DisplayName("practices.detection does not depend on leaderboard")
        void practicesDetectionDoesNotDependOnLeaderboard() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..practices.detection..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..leaderboard..")
                .allowEmptyShould(true)
                .because("Practices detection is independent of leaderboard");
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
                .resideInAnyPackage("..leaderboard..", "..mentor..", "..notification..", "..profile..")
                .allowEmptyShould(true)
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
         * PracticesController should be the only entry point for practices-related API.
         *
         * <p>All bad practice detection, resolution, and feedback endpoints should be
         * consolidated in PracticesController, not scattered across other controllers.
         */
        @Test
        @DisplayName("Practices has a dedicated controller")
        void practicesHasDedicatedController() {
            ArchRule rule = classes()
                .that()
                .resideInAPackage("..practices..")
                .and()
                .haveSimpleNameEndingWith("Controller")
                .should()
                .haveSimpleName("PracticesController")
                .allowEmptyShould(true)
                .because("All practices endpoints should be in PracticesController");
            rule.check(classes);
        }
    }
}
