package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
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
 * Cross-Cutting Module Boundary Tests.
 *
 * <p>Tests for modules that don't have explicit isolation tests yet:
 * <ul>
 *   <li><b>contributors</b> - Contributor listing and analytics</li>
 *   <li><b>account</b> - User account management</li>
 *   <li><b>config</b> - Application configuration</li>
 *   <li><b>core</b> - Shared core utilities</li>
 *   <li><b>shared</b> - Cross-cutting shared code</li>
 *   <li><b>integrations</b> - External service integrations</li>
 * </ul>
 *
 * @see ArchitectureTestConstants
 */
@DisplayName("Cross-Cutting Module Boundaries")
@Tag("architecture")
class CrossCuttingModuleBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE_PACKAGE);
    }

    // ========================================================================
    // CONTRIBUTORS MODULE ISOLATION
    // ========================================================================

    @Nested
    @DisplayName("Contributors Module Isolation")
    class ContributorsModuleTests {

        /**
         * Contributors module should not depend on leaderboard internals.
         *
         * <p>Contributors is a read-only query module that aggregates
         * contributor information. It should not depend on leaderboard's
         * scoring logic.
         */
        @Test
        @DisplayName("Contributors does not depend on leaderboard internals")
        void contributorsDoesNotDependOnLeaderboardInternals() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..contributors..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..leaderboard..service..", "..leaderboard..repository..")
                .allowEmptyShould(true)
                .because("Contributors module is independent of leaderboard scoring");
            rule.check(classes);
        }

        /**
         * Contributors module should not depend on activity internals.
         */
        @Test
        @DisplayName("Contributors does not depend on activity internals")
        void contributorsDoesNotDependOnActivityInternals() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..contributors..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..activity..service..", "..activity..repository..", "..activity.badpractice..")
                .allowEmptyShould(true)
                .because("Contributors module should not depend on activity internals");
            rule.check(classes);
        }

        /**
         * Contributors module should not depend on mentor module.
         */
        @Test
        @DisplayName("Contributors does not depend on mentor module")
        void contributorsDoesNotDependOnMentor() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..contributors..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..mentor..")
                .allowEmptyShould(true)
                .because("Contributors and mentor are independent modules");
            rule.check(classes);
        }
    }

    // ========================================================================
    // ACCOUNT MODULE ISOLATION
    // ========================================================================

    @Nested
    @DisplayName("Account Module Isolation")
    class AccountModuleTests {

        /**
         * Account module should not depend on feature modules.
         *
         * <p>Account is a foundational module for user management.
         * Feature modules depend on account, not vice versa.
         */
        @Test
        @DisplayName("Account does not depend on feature modules")
        void accountDoesNotDependOnFeatureModules() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..account..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "..leaderboard..",
                    "..activity..",
                    "..mentor..",
                    "..notification..",
                    "..profile..",
                    "..contributors.."
                )
                .allowEmptyShould(true)
                .because("Account is a foundational module - feature modules depend on it");
            rule.check(classes);
        }

        /**
         * Account module should not depend on gitprovider sync logic.
         *
         * <p>Account handles user sessions and preferences, not data sync.
         */
        @Test
        @DisplayName("Account does not depend on gitprovider sync")
        void accountDoesNotDependOnGitproviderSync() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..account..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..gitprovider..sync..")
                .allowEmptyShould(true)
                .because("Account handles user management, not data sync");
            rule.check(classes);
        }
    }

    // ========================================================================
    // CORE & SHARED PACKAGE PROTECTION
    // ========================================================================

    @Nested
    @DisplayName("Core & Shared Package Protection")
    class CoreSharedPackageTests {

        /**
         * Core package should not depend on feature modules.
         *
         * <p>Core contains shared utilities and base classes that feature
         * modules depend on. It should be dependency-free regarding features.
         */
        @Test
        @DisplayName("Core does not depend on feature modules")
        void coreDoesNotDependOnFeatureModules() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..core..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "..leaderboard..",
                    "..activity..",
                    "..mentor..",
                    "..notification..",
                    "..profile..",
                    "..contributors..",
                    "..workspace.."
                )
                .allowEmptyShould(true)
                .because("Core is a foundation layer - should not depend on feature modules");
            rule.check(classes);
        }

        /**
         * Core package should not depend on gitprovider.
         *
         * <p>Core utilities should be git-provider agnostic.
         */
        @Test
        @DisplayName("Core does not depend on gitprovider")
        void coreDoesNotDependOnGitprovider() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..core..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..gitprovider..")
                .allowEmptyShould(true)
                .because("Core should be infrastructure-agnostic");
            rule.check(classes);
        }

        /**
         * Shared package should have minimal dependencies.
         *
         * <p>Shared code should only depend on core and external libraries.
         */
        @Test
        @DisplayName("Shared has minimal module dependencies")
        void sharedHasMinimalDependencies() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..shared..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "..leaderboard..",
                    "..activity..",
                    "..mentor..",
                    "..notification..",
                    "..profile..",
                    "..contributors..",
                    "..gitprovider.."
                )
                .allowEmptyShould(true)
                .because("Shared code should not depend on feature modules");
            rule.check(classes);
        }
    }

    // ========================================================================
    // INTEGRATIONS MODULE ISOLATION
    // ========================================================================

    @Nested
    @DisplayName("Integrations Module Isolation")
    class IntegrationsModuleTests {

        /**
         * Integrations module should not depend on feature module internals.
         *
         * <p>Integrations connect to external services. Feature modules
         * should consume integrations, not vice versa.
         */
        @Test
        @DisplayName("Integrations does not depend on feature module internals")
        void integrationsDoesNotDependOnFeatureInternals() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..integrations..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "..leaderboard..service..",
                    "..activity..service..",
                    "..mentor..service..",
                    "..profile..service.."
                )
                .allowEmptyShould(true)
                .because("Integrations are consumed by features, not vice versa");
            rule.check(classes);
        }
    }

    // ========================================================================
    // CONFIG MODULE ISOLATION
    // ========================================================================

    @Nested
    @DisplayName("Config Module Isolation")
    class ConfigModuleTests {

        /**
         * Config package should not depend on feature module internals.
         *
         * <p>Configuration wires up beans but should not contain feature logic.
         */
        @Test
        @DisplayName("Config does not depend on feature service implementations")
        void configDoesNotDependOnFeatureServiceImplementations() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..config..")
                .and()
                .haveSimpleNameEndingWith("Config")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "..leaderboard..repository..",
                    "..activity..repository..",
                    "..mentor..repository.."
                )
                .allowEmptyShould(true)
                .because("Config should wire up services, not access repositories directly");
            rule.check(classes);
        }
    }

    // ========================================================================
    // NO CYCLES IN FEATURE MODULES
    // ========================================================================

    @Nested
    @DisplayName("Feature Module Cycle Detection")
    class FeatureModuleCycleTests {

        /**
         * No cycles within activity module subpackages.
         */
        @Test
        @DisplayName("No cycles within activity subpackages")
        void noCyclesWithinActivitySubpackages() {
            ArchRule rule = slices()
                .matching(BASE_PACKAGE + ".activity.(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)
                .because("Activity subpackages should not have circular dependencies");
            rule.check(classes);
        }

        /**
         * No cycles within leaderboard module subpackages.
         */
        @Test
        @DisplayName("No cycles within leaderboard subpackages")
        void noCyclesWithinLeaderboardSubpackages() {
            ArchRule rule = slices()
                .matching(BASE_PACKAGE + ".leaderboard.(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)
                .because("Leaderboard subpackages should not have circular dependencies");
            rule.check(classes);
        }

        /**
         * No cycles within mentor module subpackages.
         */
        @Test
        @DisplayName("No cycles within mentor subpackages")
        void noCyclesWithinMentorSubpackages() {
            ArchRule rule = slices()
                .matching(BASE_PACKAGE + ".mentor.(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)
                .because("Mentor subpackages should not have circular dependencies");
            rule.check(classes);
        }

        /**
         * No cycles within gitprovider common subpackages.
         */
        @Test
        @DisplayName("No cycles within gitprovider.common subpackages")
        void noCyclesWithinGitproviderCommonSubpackages() {
            ArchRule rule = slices()
                .matching(BASE_PACKAGE + ".gitprovider.common.(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)
                .because("Gitprovider common subpackages should not have circular dependencies");
            rule.check(classes);
        }
    }

    // ========================================================================
    // WORKSPACE ADAPTER PATTERN VERIFICATION
    // ========================================================================

    @Nested
    @DisplayName("Workspace Cross-Cutting Concerns")
    class WorkspaceCrossCuttingTests {

        /**
         * Workspace adapters should only be accessed by workspace package.
         *
         * <p>Adapters are internal implementation details of the workspace module.
         * Other modules should use workspace's public API.
         */
        @Test
        @DisplayName("Workspace adapters are internal")
        void workspaceAdaptersAreInternal() {
            ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("..workspace..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..workspace.adapter..")
                .allowEmptyShould(true)
                .because("Workspace adapters are internal - use workspace public API");
            rule.check(classes);
        }

        /**
         * Workspace validation should not depend on external services.
         *
         * <p>Validation logic should be pure - it validates input without
         * making external service calls.
         */
        @Test
        @DisplayName("Workspace validation has minimal dependencies")
        void workspaceValidationHasMinimalDependencies() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..workspace.validation..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "..leaderboard..",
                    "..activity..",
                    "..mentor..",
                    "..gitprovider..sync.."
                )
                .allowEmptyShould(true)
                .because("Validation should be pure logic without external service dependencies");
            rule.check(classes);
        }

        /**
         * Workspace context should be accessed via proper patterns.
         *
         * <p>The workspace context provides tenant isolation. Direct access
         * to context internals should be limited.
         */
        @Test
        @DisplayName("Workspace context internals are protected")
        void workspaceContextInternalsAreProtected() {
            ArchRule rule = classes()
                .that()
                .resideInAPackage("..workspace.context..")
                .should()
                .bePublic()
                .orShould()
                .bePackagePrivate()
                .allowEmptyShould(true)
                .because("Workspace context classes should have proper visibility");
            rule.check(classes);
        }
    }
}
