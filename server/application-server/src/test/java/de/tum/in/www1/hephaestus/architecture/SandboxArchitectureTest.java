package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Architecture tests for the sandbox module.
 *
 * <p>Enforces module boundaries:
 *
 * <ul>
 *   <li>SPI package has no Docker or Spring dependencies
 *   <li>Docker implementation is not imported outside sandbox package
 *   <li>DockerClient is only used in the docker subpackage
 * </ul>
 */
@DisplayName("Sandbox Architecture")
class SandboxArchitectureTest extends HephaestusArchitectureTest {

  @Nested
  @DisplayName("SPI isolation")
  class SpiIsolation {

    @Test
    @DisplayName("SPI package should not depend on docker-java")
    void spiShouldNotDependOnDockerJava() {
      noClasses()
          .that()
          .resideInAPackage("..agent.sandbox.spi..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.github.dockerjava..")
          .because("SPI types must remain Docker-agnostic for future remote runner support")
          .check(classes);
    }

    @Test
    @DisplayName("SPI package should not depend on Spring")
    void spiShouldNotDependOnSpring() {
      noClasses()
          .that()
          .resideInAPackage("..agent.sandbox.spi..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..")
          .because("SPI types must be framework-agnostic — no Spring annotations or imports")
          .check(classes);
    }

    @Test
    @DisplayName("SPI package should not depend on Docker implementation")
    void spiShouldNotDependOnDockerImpl() {
      noClasses()
          .that()
          .resideInAPackage("..agent.sandbox.spi..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..agent.sandbox.docker..")
          .because("SPI must not reference implementation details")
          .check(classes);
    }
  }

  @Nested
  @DisplayName("Docker implementation isolation")
  class DockerIsolation {

    @Test
    @DisplayName("Docker implementation should not be imported outside sandbox package")
    void dockerImplNotImportedOutside() {
      noClasses()
          .that()
          .resideOutsideOfPackage("..agent.sandbox..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..agent.sandbox.docker..")
          .because("Only the SPI should be used outside the sandbox package")
          .check(classes);
    }

    @Test
    @DisplayName("DockerClient should only be used in docker subpackage")
    void dockerClientOnlyInDockerPackage() {
      noClasses()
          .that()
          .resideOutsideOfPackage("..agent.sandbox.docker..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.github.dockerjava..")
          .because("Docker SDK access must be confined to the docker subpackage")
          .check(classes);
    }
  }
}
