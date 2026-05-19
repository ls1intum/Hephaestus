package de.tum.in.www1.hephaestus.agent.sandbox;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import de.tum.in.www1.hephaestus.architecture.HephaestusArchitectureTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Architecture tests enforcing sandbox module boundaries.
 *
 * <ul>
 *   <li>{@code spi/} has zero external dependencies (no Spring, no docker-java)
 *   <li>{@code docker/} is not imported outside {@code agent.sandbox}
 *   <li>{@code DockerClient} stays inside {@code docker/}
 * </ul>
 */
@DisplayName("Sandbox Architecture")
class SandboxArchitectureTest extends HephaestusArchitectureTest {

    private static final String SANDBOX_SPI = "..agent.sandbox.spi..";
    private static final String SANDBOX_DOCKER = "..agent.sandbox.docker..";

    @Nested
    @DisplayName("SPI boundary")
    class SpiBoundary {

        @Test
        @DisplayName("SPI types must not depend on Spring framework")
        void spiHasNoSpringDeps() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage(SANDBOX_SPI)
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .because("SPI types must be framework-agnostic for portability");
            rule.check(classes);
        }

        @Test
        @DisplayName("SPI types must not depend on docker-java")
        void spiHasNoDockerDeps() {
            ArchRule rule = noClasses()
                .that()
                .resideInAPackage(SANDBOX_SPI)
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.github.dockerjava..")
                .because("SPI types must not leak implementation details");
            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("Docker implementation boundary")
    class DockerBoundary {

        @Test
        @DisplayName("Docker package must not be imported outside agent.sandbox")
        void dockerNotImportedOutsideSandbox() {
            ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("..agent.sandbox..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(SANDBOX_DOCKER)
                .because("Docker implementation is an internal detail of the sandbox module");
            rule.check(classes);
        }

        @Test
        @DisplayName("DockerClient must stay inside docker package")
        void dockerClientEncapsulated() {
            ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage(SANDBOX_DOCKER)
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.github.dockerjava.api.DockerClient")
                .because("DockerClient must be encapsulated within the docker implementation package");
            rule.check(classes);
        }
    }
}
