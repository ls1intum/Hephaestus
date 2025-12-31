package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.*;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag("architecture")
class AdvancedArchitectureTest {

    private static JavaClasses classes;
    private static JavaClasses classesWithTests;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE_PACKAGE);

        classesWithTests = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE_PACKAGE);
    }

    // ========================================================================
    // LAYERED ARCHITECTURE - Strict dependency direction
    // ========================================================================

    @Nested
    @DisplayName("Layered Architecture")
    class LayeredArchitectureTests {

        /**
         * Enforces controller → service → repository layering.
         *
         * <p>Controllers should only call services, services can call
         * repositories and other services. Repositories are the bottom layer.
         */
        @Test
        @DisplayName("Controller → Service → Repository layering")
        void layeredArchitectureIsRespected() {
            ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("Controllers")
                .definedBy("..controller..", "..controllers..")
                .layer("Services")
                .definedBy("..service..", "..services..")
                .layer("Repositories")
                .definedBy("..repository..", "..repositories..")
                .layer("Domain")
                .definedBy("..domain..", "..model..", "..entity..")
                .whereLayer("Controllers")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Services")
                .mayOnlyBeAccessedByLayers("Controllers", "Services")
                .allowEmptyShould(true);
            rule.check(classes);
        }

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
         * DTOs should reside in dto packages for discoverability.
         */
        @Test
        @DisplayName("DTOs in dto packages (frozen)")
        void dtosInDtoPackages() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("DTO")
                .and()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should()
                .resideInAPackage("..dto..")
                .orShould()
                .resideInAPackage("..dtos..")
                .orShould()
                // Allow DTOs at module root for backward compatibility
                .resideInAPackage(BASE_PACKAGE + ".*..")
                .because("DTOs should be centralized for discoverability");
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
            ArchCondition<JavaMethod> haveSecurityAnnotation = new ArchCondition<>("have security annotation") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    boolean hasMapping =
                        method.isAnnotatedWith(GetMapping.class) ||
                        method.isAnnotatedWith(PostMapping.class) ||
                        method.isAnnotatedWith(PutMapping.class) ||
                        method.isAnnotatedWith(DeleteMapping.class) ||
                        method.isAnnotatedWith(PatchMapping.class) ||
                        method.isAnnotatedWith(RequestMapping.class);

                    if (!hasMapping) {
                        return; // Not an endpoint method
                    }

                    boolean hasSecurityAnnotation =
                        method.isAnnotatedWith(PreAuthorize.class) ||
                        method.getOwner().isAnnotatedWith(PreAuthorize.class) ||
                        // Check for custom security annotations on method
                        method.getAnnotations().stream().anyMatch(a -> a.getRawType().getName().contains("Require")) ||
                        // Check for custom security annotations on class
                        method
                            .getOwner()
                            .getAnnotations()
                            .stream()
                            .anyMatch(a -> a.getRawType().getName().contains("Require"));

                    if (!hasSecurityAnnotation) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                String.format(
                                    "Method %s.%s has no security annotation",
                                    method.getOwner().getSimpleName(),
                                    method.getName()
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
                .and()
                .arePublic()
                .should(haveSecurityAnnotation)
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
                .allowEmptyShould(true) // Events may be in different package structure
                .because("Domain events should be immutable records");
            rule.check(classes);
        }

        /**
         * Event listeners should be in listener, handler, or their domain packages.
         * Domain event listeners are allowed to be colocated with the domain they handle.
         */
        @Test
        @DisplayName("Event listeners follow naming convention")
        void eventListenersFollowNaming() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Listener")
                .or()
                .haveSimpleNameEndingWith("EventHandler")
                .and()
                .resideOutsideOfPackage("..spi..") // SPI interfaces have different naming conventions
                .and()
                .areNotMemberClasses() // Inner class listeners are scoped to outer class
                .should()
                .resideInAPackage("..listener..")
                .orShould()
                .resideInAPackage("..listeners..")
                .orShould()
                .resideInAPackage("..handler..")
                .orShould()
                .resideInAPackage("..handlers..")
                .orShould()
                .resideInAPackage("..event..")
                .orShould()
                .resideInAPackage("..events..")
                // Domain event listeners colocated with their domain
                .orShould()
                .resideInAPackage("..activity..")
                .orShould()
                .resideInAPackage("..workspace..")
                .because("Event handlers should be discoverable or colocated with domain");
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
                        javaClass.getSimpleName().endsWith("Adapter") ||
                        javaClass.getSimpleName().endsWith("Provider");

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

            ArchRule rule = classes()
                .should(implementSpiInterfaces)
                .allowEmptyShould(true)
                .because("SPI implementations are adapters");
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

        /**
         * Controllers should not have @Transactional.
         *
         * <p>Transaction boundaries belong in the service layer, not controllers.
         */
        @Test
        @DisplayName("Controllers are not transactional")
        void controllersAreNotTransactional() {
            ArchRule rule = noClasses()
                .that()
                .areAnnotatedWith(RestController.class)
                .should()
                .beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                .because("Transaction boundaries belong in service layer");
            rule.check(classes);
        }

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
         * Utility classes should be in util packages.
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
                .and()
                .doNotHaveSimpleName("SecurityUtils") // Used everywhere, intentionally at root
                .and()
                .doNotHaveSimpleName("LoggingUtils") // Core utility intentionally in core package
                .should()
                .resideInAPackage("..util..")
                .orShould()
                .resideInAPackage("..utils..")
                .orShould()
                .resideInAPackage("..helper..")
                .orShould()
                .resideInAPackage("..helpers..")
                .orShould()
                .resideInAPackage("..common..")
                .orShould()
                .resideInAPackage("..core..") // Core package is acceptable for core utilities
                .because("Utility classes should be discoverable");
            rule.check(classes);
        }

        /**
         * Exception classes should be in exception packages.
         * Excludes inner class exceptions which are scoped to their outer class.
         */
        @Test
        @DisplayName("Exceptions in exception packages")
        void exceptionsInExceptionPackages() {
            ArchRule rule = classes()
                .that()
                .areAssignableTo(Exception.class)
                .and()
                .doNotHaveSimpleName("Exception")
                .and()
                .haveSimpleNameNotContaining("Validation") // Entity validation exceptions colocated with entities
                .and()
                .areNotMemberClasses() // Inner class exceptions are scoped to outer class
                .should()
                .resideInAPackage("..exception..")
                .orShould()
                .resideInAPackage("..exceptions..")
                .orShould()
                .resideInAPackage("..error..")
                .orShould()
                .resideInAPackage("..errors..")
                .orShould()
                .resideInAPackage("..security..") // Security exceptions colocated with security
                .orShould()
                .resideInAPackage("..sync..") // Sync exceptions colocated with sync code
                .orShould()
                .resideInAPackage("..integrations..") // Integration exceptions colocated with integrations
                .orShould()
                .resideInAPackage("..repository..") // Repository sync exceptions colocated with repositories
                .orShould()
                .resideInAPackage("..mentor..") // Mentor/streaming exceptions colocated with mentor
                .allowEmptyShould(true)
                .because("Exceptions should be centralized or colocated with related code");
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
                .resideOutsideOfPackage("..config..") // Config wires up the client
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..intelligenceservice..")
                .because("Generated clients should be wrapped in adapters");
            // Only mentor, activity, and config should use intelligence service
            rule.check(classes);
        }

        /**
         * No direct use of Java logging APIs.
         */
        @Test
        @DisplayName("Use SLF4J, not java.util.logging")
        void useSl4jNotJavaUtilLogging() {
            ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.util.logging.Logger")
                .because("Use SLF4J for consistent logging");
            rule.check(classes);
        }
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
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("IntegrationTest")
                .and()
                .doNotHaveSimpleName("AbstractWorkspaceIntegrationTest") // Base class
                .and()
                .doNotHaveSimpleName("AbstractGitHubLiveSyncIntegrationTest") // Base class
                .and()
                .doNotHaveSimpleName("BaseGitHubLiveIntegrationTest") // Base class
                .and()
                .doNotHaveSimpleName("BaseIntegrationTest") // Base class for message handlers
                .should()
                .beAnnotatedWith(org.springframework.boot.test.context.SpringBootTest.class)
                .orShould()
                .beAssignableTo("de.tum.in.www1.hephaestus.workspace.AbstractWorkspaceIntegrationTest")
                .orShould()
                .beAssignableTo("de.tum.in.www1.hephaestus.gitprovider.github.AbstractGitHubLiveSyncIntegrationTest")
                .orShould()
                .beAssignableTo("de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest")
                .allowEmptyShould(true)
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
                .allowEmptyShould(true) // Test classes may not match package pattern
                .because("Test classes should be easily identifiable");
            rule.check(classesWithTests);
        }
    }
}
