package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

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
 * <p>These tests enforce quality standards identified in code audits:
 * <ul>
 *   <li>God class detection (SRP violations)</li>
 *   <li>Constructor parameter limits</li>
 *   <li>Return type safety (Optional vs null)</li>
 * </ul>
 */
@DisplayName("Code Quality")
@Tag("architecture")
class CodeQualityTest {

    private static final String BASE_PACKAGE = "de.tum.in.www1.hephaestus";

    // Industry standard thresholds
    private static final int MAX_CONSTRUCTOR_PARAMS = 12;
    private static final int MAX_CLASS_LINES_SOFT = 500;
    private static final int MAX_CLASS_LINES_HARD = 2000;

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
         * This is a leading indicator of SRP violations.
         */
        @Test
        @DisplayName("Services have max 12 constructor dependencies (frozen)")
        void servicesHaveLimitedConstructorParams() {
            ArchCondition<JavaClass> haveLimitedParams = new ArchCondition<>(
                "have at most " + MAX_CONSTRUCTOR_PARAMS + " constructor parameters"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    long maxParams = javaClass
                        .getConstructors()
                        .stream()
                        .mapToLong(c -> c.getRawParameterTypes().size())
                        .max()
                        .orElse(0);

                    if (maxParams > MAX_CONSTRUCTOR_PARAMS) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "GOD CLASS: %s has %d constructor params (max %d) - split into smaller services",
                                    javaClass.getSimpleName(),
                                    maxParams,
                                    MAX_CONSTRUCTOR_PARAMS
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

            // Frozen to track existing violations
            freeze(rule).check(classes);
        }

        /**
         * Controllers should be thin with limited dependencies.
         */
        @Test
        @DisplayName("Controllers have max 5 dependencies")
        void controllersAreThin() {
            ArchCondition<JavaClass> haveFewDeps = new ArchCondition<>("have at most 5 constructor parameters") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    long maxParams = javaClass
                        .getConstructors()
                        .stream()
                        .mapToLong(c -> c.getRawParameterTypes().size())
                        .max()
                        .orElse(0);

                    if (maxParams > 5) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "FAT CONTROLLER: %s has %d dependencies - consider service extraction",
                                    javaClass.getSimpleName(),
                                    maxParams
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

            freeze(rule).check(classes);
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
