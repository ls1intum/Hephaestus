package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enforces Spring naming conventions and basic coding standards.
 * Catches architectural violations early in CI/CD pipeline.
 */
@DisplayName("Architecture Compliance")
@Tag("architecture")
class ArchitectureTest {

    private static JavaClasses applicationClasses;

    @BeforeAll
    static void setUp() {
        applicationClasses = new ClassFileImporter().importPackages("de.tum.in.www1.hephaestus");
    }

    @Test
    @DisplayName("Should follow Spring component naming conventions")
    void shouldFollowSpringComponentNamingConventions() {
        classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .haveSimpleNameEndingWith("Controller")
            .check(applicationClasses);

        classes()
            .that()
            .areAnnotatedWith(Service.class)
            .should()
            .haveSimpleNameEndingWith("Service")
            .orShould()
            .haveSimpleNameEndingWith("Scheduler")
            .because("Services can be either business services or scheduled components")
            .check(applicationClasses);

        classes()
            .that()
            .areAnnotatedWith(Repository.class)
            .should()
            .haveSimpleNameEndingWith("Repository")
            .check(applicationClasses);
    }

    @Test
    @DisplayName("Repository interfaces should extend Spring Data repositories")
    void repositoryInterfacesShouldExtendSpringDataRepositories() {
        classes()
            .that()
            .areInterfaces()
            .and()
            .areAnnotatedWith(Repository.class)
            .should()
            .beAssignableTo("org.springframework.data.repository.Repository")
            .check(applicationClasses);
    }

    @Test
    @DisplayName("Should not use System.out or System.err for logging")
    void shouldNotUseSystemOutForLogging() {
        noClasses()
            .that()
            .resideInAPackage("de.tum.in.www1.hephaestus..")
            .and()
            .areNotAssignableFrom("de.tum.in.www1.hephaestus.Application")
            .should()
            .callMethod("java.lang.System", "out")
            .orShould()
            .callMethod("java.lang.System", "err")
            .because("Use proper logging framework instead of System.out/err")
            .check(applicationClasses);
    }

    @Test
    @DisplayName("Test classes should follow naming conventions")
    void testClassesShouldFollowNamingConventions() {
        classes()
            .that()
            .haveSimpleNameEndingWith("Test")
            .should()
            .bePackagePrivate()
            .orShould()
            .bePublic()
            .because("Test classes should be accessible for testing")
            .check(applicationClasses);
    }
}
