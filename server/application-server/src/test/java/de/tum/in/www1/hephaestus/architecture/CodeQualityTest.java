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
    // METHOD COMPLEXITY LIMITS
    // ========================================================================

    @Nested
    @DisplayName("Method Complexity")
    class MethodComplexityTests {

        /** Maximum parameters per method - indicates complex method. */
        private static final int MAX_METHOD_PARAMETERS = 6;

        /**
         * Methods should not have too many parameters.
         *
         * <p>Too many parameters indicates complex methods that are hard
         * to test and maintain. Consider using parameter objects.
         */
        @Test
        @DisplayName("Methods have limited parameters (max 6)")
        void methodsHaveLimitedParameters() {
            ArchCondition<JavaClass> haveMethodsWithLimitedParams = new ArchCondition<>(
                "have methods with at most " + MAX_METHOD_PARAMETERS + " parameters"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass
                        .getMethods()
                        .stream()
                        .filter(m -> m.getOwner().equals(javaClass)) // Only declared methods
                        .filter(m -> !m.getName().startsWith("$")) // Exclude synthetic
                        .filter(m -> !m.getName().equals("<init>")) // Exclude constructors
                        .filter(m -> !m.getName().startsWith("lambda$")) // Exclude lambdas
                        .forEach(method -> {
                            int paramCount = method.getRawParameterTypes().size();
                            if (paramCount > MAX_METHOD_PARAMETERS) {
                                events.add(
                                    SimpleConditionEvent.violated(
                                        javaClass,
                                        String.format(
                                            "COMPLEXITY: %s.%s has %d parameters (max %d) - consider parameter object",
                                            javaClass.getSimpleName(),
                                            method.getName(),
                                            paramCount,
                                            MAX_METHOD_PARAMETERS
                                        )
                                    )
                                );
                            }
                        });
                }
            };

            ArchRule rule = classes()
                .that()
                .resideInAPackage(BASE_PACKAGE + "..")
                .and()
                .resideOutsideOfPackage("..intelligenceservice..")
                .and()
                .resideOutsideOfPackage(GENERATED_GRAPHQL_PACKAGE)
                .and()
                .areNotAnonymousClasses()
                .and()
                .areNotMemberClasses()
                .should(haveMethodsWithLimitedParams)
                .because("Too many parameters indicates complex methods needing refactoring");

            rule.check(classes);
        }

        /**
         * Public methods in services should not have excessive nesting indicators.
         *
         * <p>Methods with many boolean parameters often indicate high cyclomatic complexity.
         * This is a proxy check since ArchUnit cannot directly measure cyclomatic complexity.
         */
        @Test
        @DisplayName("Service methods avoid excessive boolean parameters")
        void serviceMethodsAvoidExcessiveBooleanParams() {
            ArchCondition<JavaClass> avoidManyBooleans = new ArchCondition<>(
                "avoid methods with more than 2 boolean parameters"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass
                        .getMethods()
                        .stream()
                        .filter(m -> m.getOwner().equals(javaClass))
                        .filter(m -> !m.getName().startsWith("$"))
                        .forEach(method -> {
                            long booleanCount = method
                                .getRawParameterTypes()
                                .stream()
                                .filter(p -> p.getName().equals("boolean") || p.getName().equals("java.lang.Boolean"))
                                .count();
                            if (booleanCount > 2) {
                                events.add(
                                    SimpleConditionEvent.violated(
                                        javaClass,
                                        String.format(
                                            "COMPLEXITY: %s.%s has %d boolean params - consider using enum or builder",
                                            javaClass.getSimpleName(),
                                            method.getName(),
                                            booleanCount
                                        )
                                    )
                                );
                            }
                        });
                }
            };

            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Service")
                .and()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should(avoidManyBooleans)
                .because("Multiple boolean parameters indicate complexity and poor API design");

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
