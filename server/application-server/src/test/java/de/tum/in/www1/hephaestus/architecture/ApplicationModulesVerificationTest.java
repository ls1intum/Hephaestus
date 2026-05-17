package de.tum.in.www1.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

/**
 * Smoke test for the Spring Modulith 2 module structure across the application.
 *
 * <h2>What this gates today</h2>
 * <ul>
 *   <li>The application's package graph is analyzable by Spring Modulith without throwing.
 *       (Module misdeclarations or unresolvable references blow up before {@code detectViolations}.)
 *   <li>Every package annotated with {@code @ApplicationModule} is discovered.
 * </ul>
 *
 * <h2>What this DOES NOT yet enforce</h2>
 * <p>The 11 packages declared in epic #1096 carry {@code @ApplicationModule} but deliberately leave
 * {@code allowedDependencies} open. The 3000+ cross-module references that exist today
 * (achievements depending on activity types, gitprovider/agent overlap, etc.) are an architectural
 * finding for the consolidation epic, not a regression introduced by this dependency upgrade. The
 * architecture epic owns narrowing {@code allowedDependencies} per module and emptying the violation
 * set; this test will be tightened to {@code modules.verify()} at that point.
 *
 * <p>Violations are logged at INFO level so they show up in CI output without blocking the build.
 */
@Tag("architecture")
@DisplayName("Spring Modulith 2 module verification")
class ApplicationModulesVerificationTest {

    private static final Logger log = LoggerFactory.getLogger(ApplicationModulesVerificationTest.class);

    @Test
    @DisplayName("Application's @ApplicationModule packages are analyzable and discoverable")
    void modulesAreDiscoverable() {
        ApplicationModules modules = ApplicationModules.of(Application.class);

        // Smoke: at least the 11 explicit @ApplicationModule packages must show up.
        // (Spring Modulith also discovers implicit packages; expect ≥ 11 but don't pin exactly.)
        assertThat(modules.stream().count())
            .as("Spring Modulith must discover all explicit @ApplicationModule packages")
            .isGreaterThanOrEqualTo(11);

        // Detect cross-module violations but DO NOT fail the build today — the architecture
        // epic is responsible for narrowing allowedDependencies. Log a one-line summary so CI
        // operators can see the current count and watch for drift.
        Violations violations = modules.detectViolations();
        int violationCount = violations.getMessages().size();
        log.info(
            "Spring Modulith analysis: {} violation(s) detected across {} module(s). " +
                "Tightening to ApplicationModules.verify() is deferred to the architecture epic.",
            violationCount,
            modules.stream().count()
        );

        // Ratchet: ensure the count doesn't somehow exceed a sane ceiling (catches dramatic
        // regressions if someone accidentally drops a module annotation). Today's count is ~3092;
        // 6000 is a safe upper bound that still catches a 2x explosion.
        assertThat(violationCount)
            .as("Modulith violation count must not regress dramatically (current ~3000; ceiling 6000)")
            .isLessThan(6000);
    }
}
