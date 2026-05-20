package de.tum.cit.aet.hephaestus.architecture;

import de.tum.cit.aet.hephaestus.Application;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

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
 * <p>When this test fails in CI, read the error carefully — Modulith reports the exact
 * source/target packages and suggests the named-interface or {@code allowedDependencies}
 * fix. Resist the urge to "break the cycle" via events; prefer named interfaces or moving
 * code.
 *
 * <p>Documenter (PlantUML / C4 / Application Module Canvas) output is generated locally
 * via {@code mvn spring-boot:run -Pdocumenter} (not in the test path — writing diagrams
 * adds ~57s of CI time with no assertions).
 *
 * @see org.springframework.modulith.core.ApplicationModules#verify()
 * @see <a href="https://docs.spring.io/spring-modulith/reference/verification.html">Modulith Verification</a>
 */
@Tag("architecture")
class ModulithVerificationTest {

    @Test
    void modulesAreCycleFreeAndRespectNamedInterfaces() {
        ApplicationModules.of(Application.class).verify();
    }
}
