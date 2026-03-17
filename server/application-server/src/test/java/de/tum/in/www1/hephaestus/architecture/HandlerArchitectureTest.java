package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Architecture tests for the handler module.
 *
 * <p>Enforces module boundaries:
 * <ul>
 *   <li>SPI package has no Spring or gitprovider dependencies
 *   <li>Handler implementations do not reach into sandbox internals
 * </ul>
 *
 * <p>Analogous to {@link SandboxArchitectureTest} for sandbox SPI isolation.
 */
@DisplayName("Handler Architecture")
class HandlerArchitectureTest extends HephaestusArchitectureTest {

    @Nested
    @DisplayName("SPI isolation")
    class SpiIsolation {

        @Test
        @DisplayName("SPI package should not depend on Spring")
        void spiShouldNotDependOnSpring() {
            noClasses()
                .that()
                .resideInAPackage("..agent.handler.spi..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .because("SPI types must be framework-agnostic — no Spring annotations or imports")
                .check(classes);
        }

        @Test
        @DisplayName("SPI package should not depend on gitprovider")
        void spiShouldNotDependOnGitProvider() {
            noClasses()
                .that()
                .resideInAPackage("..agent.handler.spi..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..gitprovider..")
                .because("SPI types must remain domain-agnostic — handler implementations bridge to gitprovider")
                .check(classes);
        }
    }

    @Nested
    @DisplayName("Handler implementation isolation")
    class HandlerImplIsolation {

        @Test
        @DisplayName("Handlers should not depend on sandbox Docker internals")
        void handlersShouldNotDependOnDockerInternals() {
            noClasses()
                .that()
                .resideInAPackage("..agent.handler..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..agent.sandbox.docker..")
                .because("Handlers prepare context but never interact with Docker directly")
                .check(classes);
        }

        @Test
        @DisplayName("Handlers should not depend on adapter SPI")
        void handlersShouldNotDependOnAdapterSpi() {
            noClasses()
                .that()
                .resideInAPackage("..agent.handler..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..agent.adapter..")
                .because("Handlers and adapters are independent modules — the executor bridges them")
                .check(classes);
        }
    }
}
