package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.GeneralCodingRules.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.*;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
class ArchitectureTest extends HephaestusArchitectureTest {

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
    // MODULE BOUNDARIES - SPI patterns (main isolation tests in ModuleBoundaryTest)
    // ========================================================================

    @Nested
    @DisplayName("Module Boundaries")
    class ModuleBoundaries {

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

        /**
         * Adapter classes should use @Component, not @Service.
         *
         * <p>Adapters are technical integration code that bridges infrastructure
         * and domain layers. They should use @Component because they don't contain
         * business logic - they delegate to services.
         *
         * <p><b>Why package-based, not name-based:</b> Spring's @Service annotation
         * indicates the class is in the service LAYER, not that it has a particular
         * name. A class named "PaymentProcessor" containing business logic SHOULD
         * use @Service. We test by package location (which implies architectural layer)
         * rather than by name suffix.
         */
        @Test
        @DisplayName("Adapter classes use @Component, not @Service")
        void adaptersAreNotServices() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..adapter..")
                .should()
                .beAnnotatedWith(org.springframework.stereotype.Service.class)
                .because("Adapters are infrastructure glue, not business services - use @Component");
            rule.check(classes);
        }
    }
}
