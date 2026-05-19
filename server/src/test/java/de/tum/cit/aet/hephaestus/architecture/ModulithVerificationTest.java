package de.tum.cit.aet.hephaestus.architecture;

import de.tum.cit.aet.hephaestus.Application;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies the Spring Modulith application-module structure.
 *
 * <p>Runs in the {@code architecture} surefire group on every PR. Fails the build if:
 * <ul>
 *   <li>Two modules form a source-level dependency cycle</li>
 *   <li>A module reaches into another module's internal (non-API) packages without a
 *       {@link org.springframework.modulith.NamedInterface} grant</li>
 *   <li>A module declares {@code allowedDependencies} that don't match its actual imports</li>
 * </ul>
 *
 * <p>Also generates PlantUML / C4 / Application Module Canvas diagrams under
 * {@code target/modulith-docs/} so reviewers can see the topology in CI artifacts.
 * Diagrams are not committed to git (avoids review churn).
 *
 * <p>When this test fails in CI, read the error carefully — Modulith reports the exact
 * source/target packages and suggests the named-interface or {@code allowedDependencies}
 * fix. Resist the urge to "break the cycle" via events: that changes semantics. Prefer
 * named interfaces or moving code.
 *
 * @see org.springframework.modulith.core.ApplicationModules#verify()
 * @see <a href="https://docs.spring.io/spring-modulith/reference/verification.html">Modulith Verification</a>
 */
@Tag("architecture")
class ModulithVerificationTest {

    private static final ApplicationModules modules = ApplicationModules.of(Application.class);

    @Test
    void modulesAreCycleFreeAndRespectNamedInterfaces() {
        modules.verify();
    }

    @Test
    void writeDocumentation() {
        new Documenter(modules, Path.of("target/modulith-docs").toString())
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases();
    }
}
