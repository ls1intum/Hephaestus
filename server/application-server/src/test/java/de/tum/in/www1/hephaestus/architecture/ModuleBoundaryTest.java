package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.*;
import static de.tum.in.www1.hephaestus.architecture.conditions.HephaestusConditions.*;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Module Boundary Tests - Architecture Enforcement.
 *
 * <h2>Architecture: Modular Monolith with Multi-Provider Support</h2>
 * <ul>
 *   <li><b>gitprovider</b> - Shared kernel for git provider data sync (GitHub now, GitLab coming)</li>
 *   <li><b>workspace</b> - Cross-cutting context (multi-tenancy)</li>
 *   <li><b>Feature modules</b> - leaderboard, activity, mentor, profile depend on both</li>
 *   <li><b>Provider subpackages</b> - github/ and (future) gitlab/ isolate provider-specific logic</li>
 * </ul>
 *
 * <h2>Key Boundaries Enforced</h2>
 * <ol>
 *   <li><b>gitprovider isolation</b> - Cannot depend on feature modules (uses SPI for callbacks)</li>
 *   <li><b>SPI pattern</b> - gitprovider.common.spi defines contracts; workspace.adapter implements</li>
 *   <li><b>Provider isolation</b> - GitHub-specific classes must be in github/ subpackages</li>
 *   <li><b>Domain events</b> - Cross-cutting concerns via events, not direct coupling</li>
 * </ol>
 *
 * @see ArchitectureTestConstants
 */
@DisplayName("Module Boundaries")
class ModuleBoundaryTest extends HephaestusArchitectureTest {

    // ========================================================================
    // SPI (SERVICE PROVIDER INTERFACE) PATTERN
    // ========================================================================

    @Nested
    @DisplayName("SPI Pattern Enforcement")
    class SpiPatternTests {

        /**
         * SPI interfaces in gitprovider.common.spi should be interfaces.
         *
         * <p>SPIs define contracts that external modules implement to provide
         * workspace context, authentication, and sync targets to the core.
         */
        @Test
        @DisplayName("SPI classes are interfaces")
        void spiClassesAreInterfaces() {
            ArchRule rule = classes()
                .that()
                .resideInAPackage("..gitprovider.common.spi..")
                .and()
                .areNotRecords()
                .and()
                .areNotEnums()
                .and()
                .areNotMemberClasses()
                .should()
                .beInterfaces()
                .because("SPI classes define contracts and should be interfaces");
            rule.check(classes);
        }

        /**
         * Adapters in workspace.adapter should implement SPI interfaces.
         *
         * <p>The workspace module bridges workspace-specific data (tokens, targets)
         * to the gitprovider sync engine via adapter implementations.
         */
        @Test
        @DisplayName("Workspace adapters implement gitprovider SPIs")
        void workspaceAdaptersImplementSpis() {
            ArchRule rule = classes()
                .that()
                .resideInAPackage("..workspace.adapter..")
                .and()
                .areNotInterfaces()
                .and()
                .areNotMemberClasses() // Exclude anonymous inner classes
                .and()
                .areNotAnonymousClasses() // Exclude anonymous classes
                .and()
                .haveSimpleNameNotContaining("$") // Exclude generated inner classes
                .and()
                .haveSimpleNameNotEndingWith("Test")
                .should()
                .implement(JavaClass.Predicates.resideInAPackage("..spi.."))
                .because("Workspace adapters bridge workspace context to gitprovider via SPIs");
            rule.check(classes);
        }

        /**
         * SPI interfaces should be framework-agnostic.
         *
         * <p>SPIs define pure contracts without Spring dependencies to ensure
         * the core sync engine remains portable.
         */
        @Test
        @DisplayName("SPIs do not depend on Spring framework")
        void spiInterfacesAreFrameworkAgnostic() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..gitprovider.common.spi..")
                .and()
                .areInterfaces()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "org.springframework.stereotype..",
                    "org.springframework.beans..",
                    "org.springframework.context.."
                )
                .because("SPIs should be framework-agnostic contracts");
            rule.check(classes);
        }
    }

    // ========================================================================
    // GITPROVIDER BOUNDED CONTEXT ISOLATION
    // ========================================================================

    @Nested
    @DisplayName("Gitprovider Bounded Context")
    class GitproviderBoundaryTests {

        /**
         * Gitprovider should not depend on feature modules.
         *
         * <p>The gitprovider is the core ETL engine for GitHub data sync.
         * It should not have direct dependencies on workspace, leaderboard,
         * or other feature modules - only on SPIs they implement.
         */
        @Test
        @DisplayName("Gitprovider does not depend on workspace internals")
        void gitproviderDoesNotDependOnWorkspace() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..gitprovider..")
                .and()
                .resideOutsideOfPackage("..gitprovider.common.spi..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..workspace..")
                .because("Gitprovider should depend on SPIs, not workspace implementation");
            rule.check(classes);
        }

        /**
         * Gitprovider should not depend on feature modules.
         *
         * <p>This is the SINGLE SOURCE OF TRUTH for gitprovider isolation.
         * Cross-cutting concerns should be handled via domain events, not direct dependencies.
         *
         * <p>CRITICAL: Keep this list in sync with the actual feature modules.
         */
        @Test
        @DisplayName("Gitprovider does not depend on feature modules")
        void gitproviderDoesNotDependOnFeatureModules() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..gitprovider..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "..leaderboard..",
                    "..activity..",
                    "..mentor..",
                    "..notification..",
                    "..profile..",
                    "..account..",
                    "..contributors.."
                )
                .because("Gitprovider should be isolated - use domain events for cross-cutting concerns");
            rule.check(classes);
        }

        /**
         * Provider-specific processors and handlers should be in provider subpackages.
         *
         * <p>To support multiple git providers (GitHub, GitLab), provider-specific
         * logic should be isolated in github/ or gitlab/ subpackages.
         * The sync/ package is allowed to contain orchestrators that dispatch to providers.
         */
        @Test
        @DisplayName("GitHub-specific classes are in github packages")
        void githubSpecificClassesAreInGithubPackages() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameStartingWith("GitHub")
                .and()
                .resideInAPackage("..gitprovider..")
                .and()
                .resideOutsideOfPackage("..sync..") // Sync orchestrators can dispatch to providers
                .should()
                .resideInAPackage("..github..")
                .because("GitHub-specific logic should be in github/ subpackages for multi-provider support");
            rule.check(classes);
        }

    }

    // ========================================================================
    // DOMAIN EVENT PATTERNS
    // ========================================================================

    @Nested
    @DisplayName("Domain Event Patterns")
    class DomainEventPatternTests {

        /**
         * Application domain events should be in events packages.
         *
         * <p>Events enable loose coupling between modules. Our domain events
         * are in gitprovider.common.events. Activity events are in the activity
         * module and use the activity_event ledger for persistence.
         * Generated GraphQL model classes ending in 'Event' are excluded.
         */
        @Test
        @DisplayName("Domain events are in events packages")
        void domainEventsAreInEventsPackages() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Event")
                .and()
                .resideInAPackage("..gitprovider..")
                .and()
                .resideOutsideOfPackage("..graphql..") // Exclude generated GraphQL model
                .and()
                .areNotInterfaces()
                .should()
                .resideInAPackage("..events..")
                .because("Domain events should be in events packages");
            rule.check(classes);
        }
    }

    // ========================================================================
    // UTILITY CLASS PATTERNS
    // ========================================================================

    @Nested
    @DisplayName("Utility Class Patterns")
    class UtilityClassPatternTests {

        /**
         * Utility classes should be final or have private constructors.
         *
         * <p>Utility classes contain static methods and should not be instantiated
         * or extended.
         */
        @Test
        @DisplayName("Utility classes are final or have private constructors")
        void utilityClassesAreFinalOrPrivateConstructor() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Utils")
                .or()
                .haveSimpleNameEndingWith("Util")
                .or()
                .haveSimpleNameEndingWith("Helper")
                .should(beFinalOrHaveOnlyPrivateConstructors())
                .because("Utility classes should not be instantiated or extended");
            rule.check(classes);
        }
    }

    // ========================================================================
    // DTO PATTERNS
    // ========================================================================

    @Nested
    @DisplayName("DTO Patterns")
    class DtoPatternTests {

        /**
         * DTOs should be records or have only final fields.
         *
         * <p>Immutable DTOs are thread-safe and prevent accidental mutation.
         * Java records are preferred for DTOs.
         */
        @Test
        @DisplayName("DTOs are records or have final fields")
        void dtosAreImmutable() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("DTO")
                .or()
                .haveSimpleNameEndingWith("Dto")
                .and()
                .resideOutsideOfPackage(GENERATED_INTELLIGENCE_SERVICE_PACKAGE)
                .should(beImmutable())
                .because("DTOs should be immutable for thread safety");
            rule.check(classes);
        }
    }

    // ========================================================================
    // REPOSITORY PATTERNS
    // ========================================================================

    @Nested
    @DisplayName("Repository Patterns")
    class RepositoryPatternTests {

        /**
         * Spring Data repositories should be interfaces.
         *
         * <p>Spring Data automatically implements repository interfaces.
         * Domain entities named "Repository" are excluded from this check.
         */
        @Test
        @DisplayName("Spring Data repositories are interfaces")
        void springDataRepositoriesAreInterfaces() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Repository")
                .and()
                .resideInAPackage(BASE_PACKAGE + "..")
                .and()
                .resideOutsideOfPackage("..graphql..")
                .and()
                .areAssignableTo(org.springframework.data.repository.Repository.class)
                .should()
                .beInterfaces()
                .because("Spring Data repositories should be interfaces");
            rule.check(classes);
        }
    }

    // ========================================================================
    // EXCEPTION PATTERNS
    // ========================================================================

    @Nested
    @DisplayName("Exception Patterns")
    class ExceptionPatternTests {

        /**
         * Custom exceptions should extend RuntimeException.
         *
         * <p>Using unchecked exceptions simplifies error handling and
         * prevents exception declaration pollution in method signatures.
         */
        @Test
        @DisplayName("Custom exceptions extend RuntimeException")
        void customExceptionsExtendRuntimeException() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Exception")
                .and()
                .resideInAPackage(BASE_PACKAGE + "..")
                .should()
                .beAssignableTo(RuntimeException.class)
                .because("Custom exceptions should be unchecked for cleaner APIs");
            rule.check(classes);
        }
    }

    // ========================================================================
    // SPI BYPASS DETECTION
    // ========================================================================

    @Nested
    @DisplayName("SPI Bypass Detection")
    class SpiBypassDetectionTests {

        /**
         * Feature modules should only depend on gitprovider's public contracts.
         *
         * <p>Feature modules (leaderboard, activity, profile, practices) must NOT bypass
         * the SPI layer and directly depend on gitprovider internals like:
         * <ul>
         *   <li>gitprovider.sync - internal sync orchestration</li>
         *   <li>gitprovider.installation - internal installation management</li>
         *   <li>gitprovider.common.github - GitHub-specific internal code</li>
         * </ul>
         *
         * <p>They SHOULD depend on:
         * <ul>
         *   <li>gitprovider.common.spi - Service Provider Interfaces</li>
         *   <li>gitprovider.common.dto - Data Transfer Objects</li>
         *   <li>gitprovider.common.events - Domain events</li>
         *   <li>gitprovider entity packages (for reading data)</li>
         * </ul>
         */
        @Test
        @DisplayName("Feature modules do not bypass gitprovider SPIs")
        void featureModulesDoNotBypassGitproviderSpis() {
            // Internal packages that should NOT be accessed directly by feature modules
            String[] forbiddenInternalPackages = {
                "..gitprovider.sync..",
                "..gitprovider.installation..",
                "..gitprovider.common.github..",
            };

            ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..leaderboard..", "..activity..", "..profile..", "..practices..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(forbiddenInternalPackages)
                .because(
                    "Feature modules should depend on gitprovider SPIs (..spi..), DTOs (..dto..), " +
                        "or events (..events..) - not internal packages like sync or installation"
                );
            rule.check(classes);
        }

        /**
         * Verifies that feature modules use the SPI pattern correctly.
         *
         * <p>When feature modules need to provide functionality to gitprovider (callbacks,
         * context providers), they should implement SPIs defined in gitprovider.common.spi,
         * not create ad-hoc coupling.
         */
        @Test
        @DisplayName("Feature modules do not depend on gitprovider GitHub service implementations")
        void featureModulesDoNotDependOnGitHubServiceImplementations() {
            ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..leaderboard..", "..activity..", "..profile..", "..practices..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..gitprovider..github..service..")
                .because("Feature modules should use SPIs, not depend on provider-specific service implementations");
            rule.check(classes);
        }
    }

    // ========================================================================
    // FEATURE MODULE BOUNDARIES
    // ========================================================================

    @Nested
    @DisplayName("Feature Module Boundaries")
    class FeatureModuleBoundaryTests {

        /**
         * Leaderboard should not depend on workspace internal implementation.
         *
         * <p>Feature modules should depend on public APIs, not internal details.
         */
        @Test
        @DisplayName("Leaderboard does not depend on workspace internals")
        void leaderboardDoesNotDependOnWorkspaceInternals() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..leaderboard..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..workspace..internal..", "..workspace..adapter..")
                .because("Leaderboard should depend on workspace public API, not internals");
            rule.check(classes);
        }

        /**
         * Activity module should not depend on leaderboard internals.
         *
         * <p>Activity and leaderboard are peer modules - they should not
         * have direct dependencies on each other's internal implementation.
         */
        @Test
        @DisplayName("Activity does not depend on leaderboard internals")
        void activityDoesNotDependOnLeaderboardInternals() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..activity..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..leaderboard..internal..", "..leaderboard..repository..")
                .because("Activity should not depend on leaderboard internal classes");
            rule.check(classes);
        }

        /**
         * Mentor module should only depend on gitprovider and shared modules.
         *
         * <p>Mentor is a standalone feature that consumes pull request data.
         */
        @Test
        @DisplayName("Mentor has limited dependencies on feature modules")
        void mentorHasLimitedDependencies() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..mentor..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..leaderboard..", "..activity..", "..notification..")
                .because("Mentor should be isolated from other feature modules");
            rule.check(classes);
        }

        /**
         * Profile module should not depend on core sync logic.
         *
         * <p>Profile is a read-only view module that should not modify
         * or depend on the sync engine internals.
         */
        @Test
        @DisplayName("Profile does not depend on gitprovider sync internals")
        void profileDoesNotDependOnSyncInternals() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..profile..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..gitprovider..sync..", "..gitprovider..github..service..")
                .because("Profile should only read data, not depend on sync internals");
            rule.check(classes);
        }

        /**
         * Notification module should not directly depend on feature module internals.
         *
         * <p>Notification should receive events through the event system,
         * not by depending on feature module implementations.
         */
        @Test
        @DisplayName("Notification uses events, not direct dependencies")
        void notificationUsesEventsNotDirectDependencies() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..notification..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..leaderboard..service..", "..activity..service..", "..mentor..service..")
                .because("Notification should use domain events for cross-module communication");
            rule.check(classes);
        }
    }
}
