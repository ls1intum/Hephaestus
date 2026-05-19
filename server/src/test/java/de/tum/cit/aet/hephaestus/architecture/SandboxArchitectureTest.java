package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxSpec;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Architecture tests for the sandbox module boundaries. */
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

    /**
     * Rules pass trivially when the matched package is empty; the positive fixture tests below prove the rules
     * actually catch violations.
     */
    @Nested
    @DisplayName("Interactive sandbox isolation")
    class InteractiveIsolation {

        @Test
        @DisplayName("agent.mentor.** must not invoke SandboxManager.execute (use InteractiveSandboxService instead)")
        void mentorMustNotInvokeSandboxManagerExecute() {
            noClasses()
                .that()
                .resideInAPackage("..agent.mentor..")
                .should()
                .callMethod(SandboxManager.class, "execute", SandboxSpec.class)
                .because(
                    "The mentor flow is interactive by definition; SandboxManager.execute is the one-shot path. " +
                        "Use InteractiveSandboxService.attach instead."
                )
                .check(classes);
        }

        @Test
        @DisplayName("agent.practice.** must not invoke InteractiveSandboxService.attach (use SandboxManager instead)")
        void practiceMustNotInvokeInteractiveAttach() {
            noClasses()
                .that()
                .resideInAPackage("..agent.practice..")
                .should()
                .callMethod(InteractiveSandboxService.class, "attach", InteractiveSandboxSpec.class)
                .because("Practice review is a one-shot agent run; the interactive sandbox is reserved for tutoring.")
                .check(classes);
        }

        /** Guards against ArchUnit #324: callMethod silently passes when the parameter signature mismatches. */
        @Test
        @DisplayName("[positive fixture] mentor-must-not-call-execute rule actually catches violators")
        void mentorPositiveFixtureCatchesViolation() {
            JavaClasses fixtureClasses = importTestFixture("de.tum.cit.aet.hephaestus.agent.mentor");

            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..agent.mentor..")
                .should()
                .callMethod(SandboxManager.class, "execute", SandboxSpec.class);

            Assertions.assertThatThrownBy(() -> rule.check(fixtureClasses))
                .as("Rule must reject the deliberate violation fixture")
                .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("[positive fixture] practice-must-not-call-attach rule actually catches violators")
        void practicePositiveFixtureCatchesViolation() {
            JavaClasses fixtureClasses = importTestFixture("de.tum.cit.aet.hephaestus.agent.practice");

            ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..agent.practice..")
                .should()
                .callMethod(InteractiveSandboxService.class, "attach", InteractiveSandboxSpec.class);

            Assertions.assertThatThrownBy(() -> rule.check(fixtureClasses))
                .as("Rule must reject the deliberate violation fixture")
                .isInstanceOf(AssertionError.class);
        }

        private JavaClasses importTestFixture(String packageName) {
            JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages(packageName, "de.tum.cit.aet.hephaestus.agent.sandbox.spi");
            Assertions.assertThat(imported)
                .as("Architecture fixture under %s must be present on the test classpath", packageName)
                .isNotEmpty();
            return imported;
        }
    }
}
