package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.GeneralCodingRules.*;
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
 * Core Architecture Tests - Critical architectural invariants.
 *
 * <p>These tests enforce fundamental architectural constraints that prevent
 * architectural erosion and maintain system integrity:
 *
 * <h2>Test Categories (Priority Order)</h2>
 * <ol>
 *   <li><b>Structural Integrity</b> - Cycle detection, layering violations</li>
 *   <li><b>Module Boundaries</b> - Bounded context isolation, SPI patterns</li>
 *   <li><b>Spring Patterns</b> - Framework best practices</li>
 *   <li><b>Coding Standards</b> - General code quality</li>
 * </ol>
 *
 * <p>All thresholds are defined in {@link ArchitectureTestConstants}.
 *
 * @see ArchitectureTestConstants
 * @see <a href="https://www.archunit.org/userguide/html/000_Index.html">ArchUnit User Guide</a>
 */
@DisplayName("Core Architecture")
@Tag("architecture")
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE_PACKAGE);
    }

    // ========================================================================
    // STRUCTURAL INTEGRITY - Critical architectural invariants
    // ========================================================================

    @Nested
    @DisplayName("Structural Integrity")
    class StructuralIntegrity {

        /**
         * No cyclic dependencies between top-level modules.
         *
         * <p>Circular dependencies between modules create tight coupling,
         * make testing difficult, and prevent independent deployment.
         * This is one of the most important architectural constraints.
         */
        @Test
        @DisplayName("No cycles between top-level modules")
        void noCyclesBetweenModules() {
            ArchRule rule = slices()
                .matching(BASE_PACKAGE + ".(*)..")
                .namingSlices("Module '$1'")
                .should()
                .beFreeOfCycles()
                .because("Cyclic dependencies between modules prevent independent evolution and testing");
            rule.check(classes);
        }

        /**
         * Gitprovider is a self-contained bounded context.
         *
         * <p>The gitprovider module represents a unified domain aggregate for
         * GitHub data synchronization. Internal dependencies between its
         * sub-packages (issue, repository, user, etc.) are expected due to
         * JPA entity relationships. However, gitprovider should not depend
         * on feature modules (leaderboard, activity, mentor, etc.).
         *
         * <p>Note: Cycles within gitprovider are acceptable because entities
         * like Issue, PullRequest, Repository have bidirectional JPA relationships
         * which is standard ORM practice.
         */
        @Test
        @DisplayName("Gitprovider does not depend on feature modules")
        void gitproviderDoesNotDependOnFeatureModules() {
            // Gitprovider is infrastructure - it should not depend on features
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..gitprovider..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..activity..", "..leaderboard..", "..mentor..", "..notification..", "..profile..")
                .because("Gitprovider is shared infrastructure and must not depend on feature modules");
            rule.check(classes);
        }

        /**
         * Controllers should only delegate to services.
         *
         * <p>Controllers are thin entry points - they should not contain
         * business logic or access data layer directly.
         */
        @Test
        @DisplayName("Controllers delegate to services, not repositories")
        void controllersDoNotAccessRepositories() {
            ArchRule rule = noClasses()
                .that()
                .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith(org.springframework.stereotype.Repository.class)
                .because("Controllers should delegate to services, not access data layer directly");
            rule.check(classes);
        }
    }

    // ========================================================================
    // MODULE BOUNDARIES - Bounded context and SPI patterns
    // ========================================================================

    @Nested
    @DisplayName("Module Boundaries")
    class ModuleBoundaries {

        /**
         * The gitprovider module is the core sync engine.
         *
         * <p>It should NOT depend on feature modules (workspace, leaderboard, etc.)
         * Instead, it defines SPIs that feature modules implement.
         */
        @Test
        @DisplayName("gitprovider core does not depend on feature modules")
        void gitproviderDoesNotDependOnFeatureModules() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..gitprovider..")
                .and()
                .resideOutsideOfPackage("..gitprovider.common.spi..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "..workspace..",
                    "..leaderboard..",
                    "..mentor..",
                    "..activity..",
                    "..profile..",
                    "..account..",
                    "..contributors..",
                    "..notification.."
                )
                .because("gitprovider is the core ETL engine; feature modules depend on it via SPIs");
            rule.check(classes);
        }

        /**
         * SPI interfaces define the contract for dependency inversion.
         *
         * <p>Classes implementing SPIs should be in adapter packages
         * within their respective feature modules.
         */
        @Test
        @DisplayName("SPI interfaces are in the spi package")
        void spiInterfacesAreInSpiPackage() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Provider")
                .or()
                .haveSimpleNameEndingWith("Resolver")
                .or()
                .haveSimpleNameEndingWith("Listener")
                .and()
                .areInterfaces()
                .and()
                .resideInAPackage("..gitprovider.common..")
                .should()
                .resideInAPackage("..spi..")
                .because("Service Provider Interfaces enable dependency inversion");
            rule.check(classes);
        }
    }

    // ========================================================================
    // SPRING BEST PRACTICES - Framework patterns
    // ========================================================================

    @Nested
    @DisplayName("Spring Best Practices")
    class SpringBestPractices {

        /**
         * Transaction boundaries belong on service layer.
         *
         * <p>Controllers should not define transactions - this is the
         * responsibility of the service layer.
         */
        @Test
        @DisplayName("@Transactional not on controllers")
        void transactionalNotOnControllers() {
            ArchRule rule = noClasses()
                .that()
                .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should()
                .beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                .because("Transaction boundaries should be defined in the service layer");
            rule.check(classes);
        }

        /**
         * Configuration classes follow naming conventions.
         *
         * <p>All @Configuration classes should have "Config" or "Configuration"
         * suffix for discoverability.
         */
        @Test
        @DisplayName("@Configuration classes have Config suffix")
        void configurationClassesHaveConfigSuffix() {
            ArchRule rule = classes()
                .that()
                .areAnnotatedWith(org.springframework.context.annotation.Configuration.class)
                .should()
                .haveSimpleNameEndingWith("Config")
                .orShould()
                .haveSimpleNameEndingWith("Configuration")
                .because("Configuration classes should be easily identifiable by naming");
            rule.check(classes);
        }

        /**
         * Repository interfaces extend Spring Data.
         *
         * <p>Custom repository implementations should still extend
         * Spring Data abstractions for consistency.
         */
        @Test
        @DisplayName("Repositories extend Spring Data")
        void repositoriesExtendSpringData() {
            ArchRule rule = classes()
                .that()
                .areAnnotatedWith(org.springframework.stereotype.Repository.class)
                .and()
                .areInterfaces()
                .should()
                .beAssignableTo(org.springframework.data.repository.Repository.class)
                .because("Repositories should use Spring Data abstractions");
            rule.check(classes);
        }
    }

    // ========================================================================
    // CODING STANDARDS - Core quality rules
    // ========================================================================

    @Nested
    @DisplayName("Coding Standards (Core)")
    class CodingStandardsCore {

        /**
         * No console output - use SLF4J.
         */
        @Test
        @DisplayName("No System.out/err")
        void noSystemOutOrErr() {
            ArchRule rule = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because(
                "Use SLF4J (LoggerFactory.getLogger) instead of System.out/err"
            );
            rule.check(classes);
        }

        /**
         * No generic exceptions (excludes generated intelligenceservice client code).
         */
        @Test
        @DisplayName("No generic exceptions")
        void noGenericExceptions() {
            ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should(THROW_GENERIC_EXCEPTIONS)
                .because("Use specific exception types for better error handling");
            rule.check(classes);
        }

        /**
         * No field injection.
         */
        @Test
        @DisplayName("No field injection")
        void noFieldInjection() {
            ArchRule rule = NO_CLASSES_SHOULD_USE_FIELD_INJECTION.because(
                "Constructor injection makes dependencies explicit and testable"
            );
            rule.check(classes);
        }
    }

    // ========================================================================
    // CODING STANDARDS - Logging and dependencies
    // ========================================================================

    @Nested
    @DisplayName("Coding Standards (Logging)")
    class CodingStandardsLogging {

        /**
         * No Joda Time usage.
         *
         * <p>Java 8+ has java.time API - Joda Time is deprecated.
         */
        @Test
        @DisplayName("No Joda Time usage")
        void noJodaTime() {
            ArchRule rule = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.joda.time..")
                .because("Use java.time API instead of deprecated Joda Time");
            rule.check(classes);
        }

        /**
         * No java.util.logging.
         *
         * <p>SLF4J provides consistent logging facade.
         */
        @Test
        @DisplayName("No java.util.logging")
        void noJavaUtilLogging() {
            ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("..intelligenceservice..") // Exclude generated API clients
                .should()
                .dependOnClassesThat()
                .resideInAPackage("java.util.logging..")
                .because("Use SLF4J for consistent logging across the application");
            rule.check(classes);
        }

        /**
         * No Apache Commons Logging.
         *
         * <p>SLF4J provides consistent logging facade.
         * Generated intelligence-service client is excluded.
         */
        @Test
        @DisplayName("No Apache Commons Logging")
        void noCommonsLogging() {
            ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("..intelligenceservice..") // Exclude generated API clients
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.apache.commons.logging..")
                .because("Use SLF4J for consistent logging across the application");
            rule.check(classes);
        }
    }

    // ========================================================================
    // NAMING CONVENTIONS - Consistency and discoverability
    // ========================================================================

    @Nested
    @DisplayName("Naming Conventions")
    class NamingConventions {

        @Test
        @DisplayName("Controllers end with 'Controller'")
        void controllerNaming() {
            ArchRule rule = classes()
                .that()
                .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should()
                .haveSimpleNameEndingWith("Controller")
                .because("Consistent naming improves code discoverability");
            rule.check(classes);
        }

        @Test
        @DisplayName("Repositories end with 'Repository'")
        void repositoryNaming() {
            ArchRule rule = classes()
                .that()
                .areAnnotatedWith(org.springframework.stereotype.Repository.class)
                .should()
                .haveSimpleNameEndingWith("Repository")
                .because("Consistent naming improves code discoverability");
            rule.check(classes);
        }
    }
}
