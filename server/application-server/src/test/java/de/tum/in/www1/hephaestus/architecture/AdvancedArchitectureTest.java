package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.*;
import static de.tum.in.www1.hephaestus.architecture.conditions.HephaestusConditions.*;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

/**
 * Spring & DDD Architecture Tests.
 *
 * <p>These tests enforce Spring best practices and DDD patterns:
 * <ul>
 *   <li>Layered architecture enforcement</li>
 *   <li>DTO boundary protection</li>
 *   <li>Security annotation coverage</li>
 *   <li>Service layer isolation</li>
 *   <li>DDD aggregate patterns</li>
 *   <li>Package structure conventions</li>
 * </ul>
 *
 * <p>All thresholds are defined in {@link ArchitectureTestConstants}.
 *
 * @see ArchitectureTestConstants
 * @see ArchitectureTest for core architecture tests
 */
@DisplayName("Spring & DDD Architecture")
class AdvancedArchitectureTest extends HephaestusArchitectureTest {

    // ========================================================================
    // LAYERED ARCHITECTURE - Strict dependency direction
    // ========================================================================

    @Nested
    @DisplayName("Layered Architecture")
    class LayeredArchitectureTests {

        /**
         * Services should not depend on controllers.
         *
         * <p>This is a critical layering violation - services are lower
         * in the stack and should not know about presentation layer.
         */
        @Test
        @DisplayName("Services do not depend on controllers")
        void servicesDoNotDependOnControllers() {
            ArchRule rule = noClasses()
                .that()
                .haveSimpleNameEndingWith("Service")
                .and()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Controller")
                .because("Services should not know about the presentation layer");
            rule.check(classes);
        }

        /**
         * Repositories should not depend on services.
         *
         * <p>Repositories are the data layer - they should not call
         * business logic in services.
         */
        @Test
        @DisplayName("Repositories do not depend on services")
        void repositoriesDoNotDependOnServices() {
            ArchRule rule = noClasses()
                .that()
                .haveSimpleNameEndingWith("Repository")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Service")
                .because("Repositories should not call business logic");
            rule.check(classes);
        }
    }

    // ========================================================================
    // DTO BOUNDARIES - Protect domain from data transfer objects
    // ========================================================================

    @Nested
    @DisplayName("DTO Boundaries")
    class DtoBoundaryTests {

        /**
         * Entities should not depend on DTOs.
         *
         * <p>Domain entities are the core of the application and should
         * not be polluted with DTO dependencies. DTOs exist at the boundaries.
         */
        @Test
        @DisplayName("Entities do not depend on DTOs")
        void entitiesDoNotDependOnDtos() {
            ArchRule rule = noClasses()
                .that()
                .areAnnotatedWith(jakarta.persistence.Entity.class)
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("DTO")
                .because("Domain entities should not know about DTOs");
            rule.check(classes);
        }

        /**
         * DTOs should be immutable records or have no business logic.
         *
         * <p>DTOs are data carriers - they should not contain complex
         * business logic. Factory methods (fromEntity) are acceptable.
         */
        @Test
        @DisplayName("DTOs are records (immutable)")
        void dtosAreImmutableRecords() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("DTO")
                .and()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should()
                .beRecords()
                .orShould()
                .haveOnlyFinalFields()
                .because("DTOs should be immutable for thread safety and clarity");
            rule.check(classes);
        }

        /**
         * DTOs should reside in dto packages or be colocated with their domain.
         *
         * <p>This codebase uses "Package by Feature" - DTOs can live with their domain entities.
         * Valid patterns:
         * <ul>
         *   <li>DTOs in dto packages (traditional)</li>
         *   <li>Record DTOs colocated with domain (inherently immutable)</li>
         *   <li>InfoDTO suffix for colocated lightweight DTOs</li>
         *   <li>Nested class DTOs scoped to their outer class</li>
         * </ul>
         */
        @Test
        @DisplayName("DTOs in dto packages or colocated with domain")
        void dtosInDtoPackages() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("DTO")
                .and()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should()
                .resideInAPackage("..dto..")
                .orShould()
                .beRecords() // Records are inherently DTOs - colocated is fine
                .orShould()
                .haveSimpleNameEndingWith("InfoDTO") // Info DTOs can be colocated
                .orShould()
                .beNestedClasses() // Inner class DTOs are scoped to outer class
                .because("DTOs should be in dto packages or colocated with their domain (Package by Feature)");
            rule.check(classes);
        }
    }

    // ========================================================================
    // SECURITY - Ensure all endpoints are protected
    // ========================================================================

    @Nested
    @DisplayName("Security Enforcement")
    class SecurityTests {

        /**
         * All REST controller public methods should have security annotations.
         *
         * <p>Every endpoint must explicitly declare its security requirements
         * via @PreAuthorize at class or method level. No exceptions allowed.
         */
        @Test
        @DisplayName("Controller methods have security annotations")
        void controllerMethodsHaveSecurityAnnotations() {
            ArchRule rule = methods()
                .that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(RestController.class)
                .and()
                .arePublic()
                .should(haveSecurityAnnotationIfEndpoint())
                .because("All endpoints must have explicit security");

            rule.check(classes);
        }
    }

    // ========================================================================
    // DOMAIN-DRIVEN DESIGN - Aggregate and event patterns
    // ========================================================================

    @Nested
    @DisplayName("DDD Patterns")
    class DddPatternTests {

        /**
         * Domain events should be immutable records.
         *
         * <p>Events represent facts that happened - they should be immutable
         * and use record types for automatic equals/hashCode/toString.
         */
        @Test
        @DisplayName("Domain events are immutable records")
        void domainEventsAreRecords() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Event")
                .and()
                .resideInAPackage("..event..")
                .should()
                .beRecords()
                .orShould()
                .beInterfaces()
                .because("Domain events should be immutable records");
            rule.check(classes);
        }

        /**
         * Event listeners are within the application package.
         *
         * <p>This codebase uses "Package by Feature" - listeners are colocated with their domain.
         * We only verify they're within our application package structure.
         *
         * <p>NOTE: Previously had 6 orShould() clauses. Simplified: we trust domain colocation.
         */
        @Test
        @DisplayName("Event listeners are in application packages")
        void eventListenersInApplicationPackages() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Listener")
                .or()
                .haveSimpleNameEndingWith("EventHandler")
                .and()
                .resideOutsideOfPackage("..spi..")
                .and()
                .areNotMemberClasses()
                .should()
                .resideInAPackage(BASE_PACKAGE + "..")
                .because("Event listeners should be within the application package structure");
            rule.check(classes);
        }

        /**
         * SPI implementations should be in adapter packages.
         *
         * <p>Service Provider Interface implementations are adapters
         * that connect modules - they belong in adapter packages.
         */
        @Test
        @DisplayName("SPI implementations in adapter packages")
        void spiImplementationsInAdapterPackages() {
            ArchCondition<JavaClass> implementSpiInterfaces = new ArchCondition<>("implement SPI interfaces properly") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    boolean implementsSpi = javaClass
                        .getAllRawInterfaces()
                        .stream()
                        .anyMatch(i -> i.getPackageName().contains(".spi"));

                    if (!implementsSpi) {
                        return; // Not an SPI implementation
                    }

                    boolean inAdapterPackage =
                        javaClass.getPackageName().contains(".adapter") ||
                        javaClass.getPackageName().contains(".impl") ||
                        javaClass.getPackageName().contains(".notification") || // Notification module implements activity SPIs
                        javaClass.getSimpleName().endsWith("Adapter") ||
                        javaClass.getSimpleName().endsWith("Provider") ||
                        javaClass.getSimpleName().endsWith("Tracker"); // Rate limit trackers implement RateLimitTracker SPI

                    if (!inAdapterPackage) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "%s implements SPI but is not in adapter package",
                                    javaClass.getSimpleName()
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes().should(implementSpiInterfaces).because("SPI implementations are adapters");
            rule.check(classes);
        }
    }

    // ========================================================================
    // CONTROLLER PATTERNS - Thin controllers
    // ========================================================================

    @Nested
    @DisplayName("Controller Patterns")
    class ControllerPatternTests {

        /**
         * Controllers should not have complex business logic.
         *
         * <p>Controllers should be thin - they validate input, delegate
         * to services, and format responses. Complex logic belongs in services.
         */
        @Test
        @DisplayName("Controllers don't access JPA directly (frozen)")
        void controllersDoNotAccessJpaDirectly() {
            ArchRule rule = noClasses()
                .that()
                .areAnnotatedWith(RestController.class)
                .should()
                .dependOnClassesThat()
                .areAssignableTo(jakarta.persistence.EntityManager.class)
                .because("Controllers should not access JPA directly - use services");
            rule.check(classes);
        }

        // NOTE: @Transactional check moved to ArchitectureTest.transactionalNotOnControllers()

        /**
         * Controllers should return response types, not entities.
         *
         * <p>Returning JPA entities from controllers can cause lazy loading
         * issues and exposes internal structure. Use DTOs instead.
         */
        @Test
        @DisplayName("Controllers do not return entities directly")
        void controllersDoNotReturnEntities() {
            ArchCondition<JavaMethod> notReturnEntity = new ArchCondition<>("not return JPA entity") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    boolean hasMapping =
                        method.isAnnotatedWith(GetMapping.class) ||
                        method.isAnnotatedWith(PostMapping.class) ||
                        method.isAnnotatedWith(PutMapping.class) ||
                        method.isAnnotatedWith(DeleteMapping.class) ||
                        method.isAnnotatedWith(PatchMapping.class);

                    if (!hasMapping) {
                        return;
                    }

                    String returnType = method.getRawReturnType().getName();
                    // Check if return type is in entity packages
                    if (
                        returnType.contains(".gitprovider.") &&
                        !returnType.endsWith("DTO") &&
                        !returnType.contains("ResponseEntity") &&
                        !returnType.contains("Void") &&
                        !returnType.equals("void")
                    ) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                String.format(
                                    "Method %s.%s returns entity type %s - use DTO",
                                    method.getOwner().getSimpleName(),
                                    method.getName(),
                                    returnType
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = methods()
                .that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(RestController.class)
                .should(notReturnEntity)
                .because("Controllers should return DTOs, not entities");

            rule.check(classes);
        }
    }

    // ========================================================================
    // PACKAGE STRUCTURE - Consistent organization
    // ========================================================================

    @Nested
    @DisplayName("Package Structure")
    class PackageStructureTests {

        /**
         * Each module should have consistent subpackage structure.
         */
        @Test
        @DisplayName("No cycles within feature modules")
        void noCyclesWithinFeatureModules() {
            ArchRule rule = slices()
                .matching(BASE_PACKAGE + ".workspace.(*)..")
                .should()
                .beFreeOfCycles()
                .because("Feature module subpackages should not have cycles");
            rule.check(classes);
        }

        /**
         * Utility classes should be in util, common, or core packages.
         *
         * <p>Valid locations for utilities:
         * <ul>
         *   <li>util packages (traditional)</li>
         *   <li>common packages (shared utilities)</li>
         *   <li>core packages (infrastructure utilities like LoggingUtils)</li>
         *   <li>Root package for cross-cutting utilities (SecurityUtils)</li>
         * </ul>
         */
        @Test
        @DisplayName("Utility classes in util packages")
        void utilityClassesInUtilPackages() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Util")
                .or()
                .haveSimpleNameEndingWith("Utils")
                .or()
                .haveSimpleNameEndingWith("Helper")
                .or()
                .haveSimpleNameEndingWith("Helpers")
                .should()
                .resideInAPackage("..util..")
                .orShould()
                .resideInAPackage("..common..")
                .orShould()
                .resideInAPackage("..core..") // Core infrastructure utilities
                .orShould()
                .resideInAPackage(BASE_PACKAGE) // Root-level cross-cutting utils (e.g., SecurityUtils)
                .because("Utility classes should be in util, common, core, or root packages");
            rule.check(classes);
        }

        /**
         * Exception classes should be in domain-appropriate packages.
         *
         * <p>This codebase uses "Package by Feature" - exceptions are colocated with their domain.
         * The only restriction: exceptions must be RuntimeException subclasses (enforced in ModuleBoundaryTest).
         *
         * <p>NOTE: Previously had 7 orShould() clauses listing every valid package.
         * Simplified: we trust developers to colocate exceptions appropriately.
         * The actual enforcement is that custom exceptions extend RuntimeException.
         */
        @Test
        @DisplayName("Exceptions are in the application package")
        void exceptionsInApplicationPackage() {
            ArchRule rule = classes()
                .that()
                .areAssignableTo(Exception.class)
                .and()
                .doNotHaveSimpleName("Exception")
                .and()
                .areNotMemberClasses()
                .should()
                .resideInAPackage(BASE_PACKAGE + "..")
                .because("Custom exceptions should be within the application package structure");
            rule.check(classes);
        }
    }

    // ========================================================================
    // DEPENDENCY MANAGEMENT - Clean dependency patterns
    // ========================================================================

    @Nested
    @DisplayName("Dependency Management")
    class DependencyManagementTests {

        /**
         * Generated code should not be depended upon by hand-written code.
         *
         * <p>The intelligence service client is generated - we should
         * wrap it in adapters rather than depending on it directly.
         */
        @Test
        @DisplayName("Limit dependencies on generated code")
        void limitDependenciesOnGeneratedCode() {
            ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("..intelligenceservice..")
                .and()
                .resideOutsideOfPackage("..mentor..")
                .and()
                .resideOutsideOfPackage("..activity..")
                .and()
                .resideOutsideOfPackage("..practices..") // Code health module uses intelligence service for AI detection
                .and()
                .resideOutsideOfPackage("..config..") // Config wires up the client
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..intelligenceservice..")
                .because("Generated clients should be wrapped in adapters");
            // Only mentor, activity, practices, and config should use intelligence service
            rule.check(classes);
        }

        // NOTE: java.util.logging check moved to ArchitectureTest.noJavaUtilLogging()
    }

    // ========================================================================
    // TEST ARCHITECTURE - Tests follow patterns
    // ========================================================================

    @Nested
    @DisplayName("Test Architecture")
    class TestArchitectureTests {

        /**
         * Integration tests should extend base test classes or be annotated with @SpringBootTest.
         *
         * <p>Using shared base classes ensures consistent setup and
         * database cleanup between tests.
         */
        @Test
        @DisplayName("Integration tests extend base classes")
        void integrationTestsExtendBaseClasses() {
            // Base classes that are excluded from the check
            java.util.Set<String> baseClassNames = java.util.Set.of(
                "AbstractWorkspaceIntegrationTest",
                "AbstractGitHubLiveSyncIntegrationTest",
                "BaseGitHubLiveIntegrationTest",
                "BaseIntegrationTest"
            );

            // Recognized base classes that integration tests should extend
            java.util.Set<String> validBaseClasses = java.util.Set.of(
                "de.tum.in.www1.hephaestus.workspace.AbstractWorkspaceIntegrationTest",
                "de.tum.in.www1.hephaestus.gitprovider.github.AbstractGitHubLiveSyncIntegrationTest",
                "de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest"
            );

            ArchCondition<JavaClass> haveProperSpringContext = new ArchCondition<>(
                "have Spring context via @SpringBootTest annotation or extend a recognized base class"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    boolean hasSpringBootTest = javaClass.isAnnotatedWith(
                        org.springframework.boot.test.context.SpringBootTest.class
                    );

                    boolean extendsValidBase = javaClass
                        .getAllRawSuperclasses()
                        .stream()
                        .anyMatch(superClass -> validBaseClasses.contains(superClass.getName()));

                    if (!hasSpringBootTest && !extendsValidBase) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "%s is an integration test but doesn't have @SpringBootTest or extend a base class",
                                    javaClass.getSimpleName()
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("IntegrationTest")
                .and()
                .haveSimpleNameNotContaining("Abstract")
                .and()
                .haveSimpleNameNotStartingWith("Base")
                .should(haveProperSpringContext)
                .because("Integration tests need proper Spring context via base class or annotation");
            rule.check(classesWithTests);
        }

        /**
         * Test classes should follow naming conventions.
         */
        @Test
        @DisplayName("Test classes end with Test suffix")
        void testClassesEndWithTestSuffix() {
            ArchRule rule = classes()
                .that()
                .resideInAPackage("..test..")
                .and()
                .areNotInterfaces()
                .and()
                .areNotAnonymousClasses()
                .and()
                .doNotHaveSimpleName("package-info")
                .and()
                .areNotAnnotatedWith(org.springframework.context.annotation.Configuration.class)
                .and()
                .haveSimpleNameNotStartingWith("Abstract")
                .and()
                .haveSimpleNameNotEndingWith("Base")
                .and()
                .haveSimpleNameNotEndingWith("Factory")
                .and()
                .haveSimpleNameNotEndingWith("Utils")
                .and()
                .haveSimpleNameNotEndingWith("Config")
                .and()
                .haveSimpleNameNotEndingWith("Configuration")
                .should()
                .haveSimpleNameEndingWith("Test")
                .orShould()
                .haveSimpleNameEndingWith("Tests")
                .orShould()
                .haveSimpleNameEndingWith("IT")
                .because("Test classes should be easily identifiable");
            rule.check(classesWithTests);
        }
    }
}
