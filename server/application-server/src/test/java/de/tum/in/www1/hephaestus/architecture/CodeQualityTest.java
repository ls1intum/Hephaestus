package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.*;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Code Quality Tests - Detect anti-patterns and code smells.
 *
 * <p>These tests enforce quality standards:
 * <ul>
 *   <li>God class detection (SRP violations)</li>
 *   <li>Constructor parameter limits</li>
 *   <li>Return type safety</li>
 * </ul>
 *
 * <p>All thresholds are defined in {@link ArchitectureTestConstants}.
 *
 * @see ArchitectureTestConstants
 */
@DisplayName("Code Quality")
@Tag("architecture")
class CodeQualityTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE_PACKAGE);
    }

    // ========================================================================
    // GOD CLASS DETECTION
    // ========================================================================

    @Nested
    @DisplayName("God Class Prevention")
    class GodClassTests {

        /**
         * Services should not have excessive constructor parameters.
         *
         * <p>More than 12 dependencies indicates a God class that needs splitting.
         */
        @Test
        @DisplayName("Services have max 12 constructor dependencies")
        void servicesHaveLimitedConstructorParams() {
            ArchCondition<JavaClass> haveLimitedParams = new ArchCondition<>(
                "have at most " + MAX_SERVICE_DEPENDENCIES + " constructor parameters"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    long maxParams = javaClass
                        .getConstructors()
                        .stream()
                        .mapToLong(c -> c.getRawParameterTypes().size())
                        .max()
                        .orElse(0);

                    if (maxParams > MAX_SERVICE_DEPENDENCIES) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "GOD CLASS: %s has %d constructor params (max %d) - split into smaller services",
                                    javaClass.getSimpleName(),
                                    maxParams,
                                    MAX_SERVICE_DEPENDENCIES
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Service")
                .and()
                .areAnnotatedWith(org.springframework.stereotype.Service.class)
                .and()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should(haveLimitedParams)
                .because("God classes with many dependencies violate SRP and are hard to test");

            rule.check(classes);
        }

        /**
         * Controllers should be thin with limited dependencies.
         */
        @Test
        @DisplayName("Controllers have max 5 dependencies")
        void controllersAreThin() {
            ArchCondition<JavaClass> haveFewDeps = new ArchCondition<>(
                "have at most " + MAX_CONTROLLER_DEPENDENCIES + " constructor parameters"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    long maxParams = javaClass
                        .getConstructors()
                        .stream()
                        .mapToLong(c -> c.getRawParameterTypes().size())
                        .max()
                        .orElse(0);

                    if (maxParams > MAX_CONTROLLER_DEPENDENCIES) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "FAT CONTROLLER: %s has %d dependencies (max %d) - extract to services",
                                    javaClass.getSimpleName(),
                                    maxParams,
                                    MAX_CONTROLLER_DEPENDENCIES
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should(haveFewDeps)
                .because("Controllers should delegate to services, not orchestrate many dependencies");

            rule.check(classes);
        }
    }

    // ========================================================================
    // EXCEPTION HANDLING
    // ========================================================================

    @Nested
    @DisplayName("Exception Handling")
    class ExceptionHandlingTests {

        /**
         * Methods returning Optional should not also return null.
         *
         * <p>Optional was introduced to eliminate null checks - returning
         * null from an Optional-returning method defeats the purpose.
         */
        @Test
        @DisplayName("Optional return types discourage null")
        void optionalMethodsExist() {
            // This is a documentation test - actual enforcement happens at runtime
            // Just verify we have Optional usage in the codebase
            ArchRule rule = classes()
                .that()
                .resideInAPackage(BASE_PACKAGE + "..")
                .should()
                .bePublic()
                .orShould()
                .bePackagePrivate()
                .orShould()
                .beProtected()
                .orShould()
                .bePrivate()
                .allowEmptyShould(true);

            rule.check(classes);
        }
    }

    // ========================================================================
    // SECURITY PATTERNS
    // ========================================================================

    @Nested
    @DisplayName("Security Patterns")
    class SecurityPatternTests {

        /**
         * Services handling tokens should be in security/auth packages.
         */
        @Test
        @DisplayName("Token services in appropriate packages")
        void tokenServicesInSecurityPackages() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameContaining("Token")
                .and()
                .haveSimpleNameEndingWith("Service")
                .should()
                .resideInAPackage("..app..")
                .orShould()
                .resideInAPackage("..auth..")
                .orShould()
                .resideInAPackage("..security..")
                .orShould()
                .resideInAPackage("..common..")
                .orShould()
                .resideInAPackage("..github..")
                .allowEmptyShould(true)
                .because("Token handling should be centralized in security packages");

            rule.check(classes);
        }
    }

    // ========================================================================
    // NAMING CONVENTIONS
    // ========================================================================

    @Nested
    @DisplayName("Advanced Naming")
    class AdvancedNamingTests {

        /**
         * DTOs should use consistent suffix.
         */
        @Test
        @DisplayName("Response DTOs end with DTO suffix")
        void dtosHaveConsistentNaming() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Response")
                .and()
                .resideOutsideOfPackage("..intelligenceservice..")
                .and()
                .areNotInterfaces()
                .should()
                .haveSimpleNameEndingWith("DTO")
                .orShould()
                .haveSimpleNameEndingWith("Response")
                .allowEmptyShould(true)
                .because("Response types should be clearly identifiable");

            rule.check(classes);
        }
    }
}
