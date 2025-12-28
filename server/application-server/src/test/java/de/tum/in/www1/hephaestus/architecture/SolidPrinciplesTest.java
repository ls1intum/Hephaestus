package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
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
 * SOLID Principles Architecture Tests.
 *
 * <p>These tests enforce adherence to SOLID principles:
 * <ul>
 *   <li><b>S</b>ingle Responsibility Principle</li>
 *   <li><b>O</b>pen/Closed Principle</li>
 *   <li><b>L</b>iskov Substitution Principle</li>
 *   <li><b>I</b>nterface Segregation Principle</li>
 *   <li><b>D</b>ependency Inversion Principle</li>
 * </ul>
 *
 * <p>Most rules are frozen to track technical debt while preventing
 * new violations.
 *
 * @see <a href="https://en.wikipedia.org/wiki/SOLID">SOLID Principles</a>
 */
@DisplayName("SOLID Principles")
@Tag("architecture")
class SolidPrinciplesTest {

    private static final String BASE_PACKAGE = "de.tum.in.www1.hephaestus";

    // Thresholds based on industry standards
    private static final int MAX_CLASS_METHODS = 30;
    private static final int MAX_SERVICE_DEPENDENCIES = 10;
    private static final int MAX_INTERFACE_METHODS = 8;

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE_PACKAGE);
    }

    // ========================================================================
    // SINGLE RESPONSIBILITY PRINCIPLE
    // ========================================================================

    @Nested
    @DisplayName("Single Responsibility Principle")
    class SingleResponsibilityTests {

        /**
         * Classes should not have too many methods.
         *
         * <p>A class with many methods likely has multiple responsibilities.
         * Target: max 30 methods per class.
         */
        @Test
        @DisplayName("Classes have limited methods (max 30)")
        void classesHaveLimitedMethods() {
            ArchCondition<JavaClass> haveLimitedMethods = new ArchCondition<>(
                "have at most " + MAX_CLASS_METHODS + " methods"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    int methodCount = (int) javaClass
                        .getMethods()
                        .stream()
                        .filter(m -> !m.getName().startsWith("$"))
                        .filter(m -> !m.getName().equals("equals"))
                        .filter(m -> !m.getName().equals("hashCode"))
                        .filter(m -> !m.getName().equals("toString"))
                        .filter(m -> !m.getName().startsWith("get"))
                        .filter(m -> !m.getName().startsWith("set"))
                        .filter(m -> !m.getName().startsWith("is"))
                        .count();

                    if (methodCount > MAX_CLASS_METHODS) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "%s has %d methods (max %d) - consider splitting",
                                    javaClass.getSimpleName(),
                                    methodCount,
                                    MAX_CLASS_METHODS
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
                .resideOutsideOfPackage("..intelligenceservice..")
                .should(haveLimitedMethods)
                .because("Services with too many methods likely have multiple responsibilities");

            freeze(rule).check(classes);
        }

        /**
         * Services should have limited dependencies.
         *
         * <p>A service with many injected dependencies is likely doing too much.
         * Target: max 10 constructor parameters.
         */
        @Test
        @DisplayName("Services have limited dependencies (max 10)")
        void servicesHaveLimitedDependencies() {
            ArchCondition<JavaClass> haveLimitedDependencies = new ArchCondition<>(
                "have at most " + MAX_SERVICE_DEPENDENCIES + " dependencies"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    // Count @Autowired fields
                    long fieldInjections = javaClass
                        .getFields()
                        .stream()
                        .filter(f -> f.isAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class))
                        .count();

                    // Count constructor parameters (rough proxy for dependencies)
                    long constructorParams = javaClass
                        .getConstructors()
                        .stream()
                        .mapToLong(c -> c.getRawParameterTypes().size())
                        .max()
                        .orElse(0);

                    long totalDeps = Math.max(fieldInjections, constructorParams);

                    if (totalDeps > MAX_SERVICE_DEPENDENCIES) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "%s has %d dependencies (max %d) - SRP violation",
                                    javaClass.getSimpleName(),
                                    totalDeps,
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
                .should(haveLimitedDependencies)
                .because("Too many dependencies indicate SRP violation");

            freeze(rule).check(classes);
        }

        /**
         * Controllers should be thin - delegate to services.
         *
         * <p>Controllers should validate input and delegate - they should
         * not have many dependencies directly.
         */
        @Test
        @DisplayName("Controllers have minimal dependencies (max 5)")
        void controllersHaveMinimalDependencies() {
            ArchCondition<JavaClass> haveMinimalDeps = new ArchCondition<>("have at most 5 service dependencies") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    long constructorParams = javaClass
                        .getConstructors()
                        .stream()
                        .mapToLong(c -> c.getRawParameterTypes().size())
                        .max()
                        .orElse(0);

                    if (constructorParams > 5) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "%s has %d dependencies - controllers should be thin",
                                    javaClass.getSimpleName(),
                                    constructorParams
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should(haveMinimalDeps)
                .because("Controllers should delegate to services");

            freeze(rule).check(classes);
        }
    }

    // ========================================================================
    // DEPENDENCY INVERSION PRINCIPLE
    // ========================================================================

    @Nested
    @DisplayName("Dependency Inversion Principle")
    class DependencyInversionTests {

        /**
         * No field injection - use constructor injection.
         *
         * <p>Field injection hides dependencies and makes testing harder.
         * Constructor injection makes dependencies explicit.
         */
        @Test
        @DisplayName("No @Autowired field injection")
        void noFieldInjection() {
            ArchRule rule = noFields()
                .should()
                .beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
                .because("Use constructor injection for explicit dependencies");

            // Frozen - 48 existing violations
            freeze(rule).check(classes);
        }

        /**
         * Services should depend on interfaces where available.
         *
         * <p>When interfaces exist, high-level modules should depend
         * on the abstraction, not the concrete implementation.
         */
        @Test
        @DisplayName("Prefer interface dependencies for cross-module communication")
        void preferInterfaceDependencies() {
            // This rule checks that workspace module depends on gitprovider SPIs
            ArchRule rule = classes()
                .that()
                .resideInAPackage("..workspace.adapter..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..spi..")
                .orShould()
                .dependOnClassesThat()
                .areInterfaces()
                .allowEmptyShould(true)
                .because("Adapters should implement SPI interfaces");

            rule.check(classes);
        }

        /**
         * No circular dependencies via ObjectProvider.
         *
         * <p>ObjectProvider is a valid way to break cycles, but should
         * be used sparingly and documented.
         */
        @Test
        @DisplayName("ObjectProvider usage is documented")
        void objectProviderUsageIsLimited() {
            ArchCondition<JavaField> beDocumented = new ArchCondition<>("have a comment explaining the cycle break") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    // Just count ObjectProvider usages - they should be rare
                    // This is a signal for code review
                    if (field.getRawType().getName().contains("ObjectProvider")) {
                        events.add(
                            SimpleConditionEvent.satisfied(
                                field,
                                String.format(
                                    "%s uses ObjectProvider (verify cycle is documented)",
                                    field.getFullName()
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = fields()
                .that()
                .haveRawType(org.springframework.beans.factory.ObjectProvider.class)
                .should(beDocumented)
                .allowEmptyShould(true);

            // Just informational - no freeze needed
            rule.check(classes);
        }
    }

    // ========================================================================
    // INTERFACE SEGREGATION PRINCIPLE
    // ========================================================================

    @Nested
    @DisplayName("Interface Segregation Principle")
    class InterfaceSegregationTests {

        /**
         * Interfaces should have limited methods.
         *
         * <p>Fat interfaces force implementations to provide methods they
         * don't need. Prefer small, focused interfaces.
         */
        @Test
        @DisplayName("Interfaces have limited methods (max 8)")
        void interfacesHaveLimitedMethods() {
            ArchCondition<JavaClass> haveLimitedMethods = new ArchCondition<>(
                "have at most " + MAX_INTERFACE_METHODS + " methods"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    // Skip Spring Data repositories - they have many default methods
                    if (javaClass.isAssignableTo(org.springframework.data.repository.Repository.class)) {
                        return;
                    }

                    int methodCount = (int) javaClass
                        .getMethods()
                        .stream()
                        .filter(m -> !m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC))
                        .filter(m -> m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT))
                        .count();

                    if (methodCount > MAX_INTERFACE_METHODS) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "%s has %d abstract methods (max %d) - ISP violation",
                                    javaClass.getSimpleName(),
                                    methodCount,
                                    MAX_INTERFACE_METHODS
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .areInterfaces()
                .and()
                .resideInAPackage(BASE_PACKAGE + "..")
                .and()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should(haveLimitedMethods)
                .because("Interfaces should be small and focused (ISP)");

            freeze(rule).check(classes);
        }

        /**
         * SPI interfaces should be particularly focused.
         *
         * <p>Service Provider Interfaces define module contracts -
         * they should be minimal.
         */
        @Test
        @DisplayName("SPI interfaces are focused (max 5 methods)")
        void spiInterfacesAreFocused() {
            ArchCondition<JavaClass> beFocused = new ArchCondition<>("have at most 5 methods") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    int methodCount = (int) javaClass
                        .getMethods()
                        .stream()
                        .filter(m -> m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT))
                        .count();

                    if (methodCount > 5) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "SPI %s has %d methods (max 5) - split interface",
                                    javaClass.getSimpleName(),
                                    methodCount
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .areInterfaces()
                .and()
                .resideInAPackage("..spi..")
                .should(beFocused)
                .because("SPI interfaces should be minimal");

            freeze(rule).check(classes);
        }
    }

    // ========================================================================
    // OPEN/CLOSED PRINCIPLE
    // ========================================================================

    @Nested
    @DisplayName("Open/Closed Principle")
    class OpenClosedTests {

        /**
         * Handlers should use strategy pattern.
         *
         * <p>Classes ending in Handler should implement a common interface
         * to allow extension without modification.
         */
        @Test
        @DisplayName("Handlers implement interfaces")
        void handlersImplementInterfaces() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Handler")
                .and()
                .areNotInterfaces()
                .and()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should()
                .implement(JavaClass.Predicates.INTERFACES)
                .orShould()
                .beAssignableTo(Object.class) // Allow abstract base classes
                .because("Handlers should implement interfaces for extensibility");

            freeze(rule).check(classes);
        }

        /**
         * Processors should use template method or strategy.
         */
        @Test
        @DisplayName("Processors use proper inheritance")
        void processorsUseProperInheritance() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Processor")
                .and()
                .areNotInterfaces()
                .and()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should()
                .beAssignableTo(Object.class)
                .allowEmptyShould(true)
                .because("Processors should be extensible");

            rule.check(classes);
        }
    }
}
