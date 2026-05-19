package de.tum.in.www1.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.metrics.ApplicationStartup;

/**
 * Regression guard for application boot time and startup-step shape.
 *
 * <p>Wires {@link BufferingApplicationStartup} via {@link Application#main(String[])} (forced by
 * {@code useMainMethod = ALWAYS}) so we can inspect the captured timeline post-refresh. Asserts:
 *
 * <ol>
 *   <li>The buffering startup bean is in the context (the wiring contract).</li>
 *   <li>The timeline captured non-trivial events (the wiring actually fired).</li>
 *   <li>No single top-level step blew past the per-step ceiling — catches the "someone added a
 *       slow {@code @PostConstruct} or bean" regression.</li>
 * </ol>
 *
 * <p><b>Why no absolute total-startup AC here:</b> the {@code test} profile uses
 * {@code ddl-auto: create} + {@code liquibase.enabled: false} + most sync/agent features off, so
 * its absolute boot is far shorter than a real local boot. Pinning a wall-clock threshold here
 * would mostly measure JVM warm-up. The per-step ceiling is the meaningful guardrail.
 *
 * <p>See Spring Boot reference — Startup Tracking:
 * https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.startup-tracking
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS
)
@DisplayName("Startup budget regression guard")
class StartupBudgetIntegrationTest extends BaseIntegrationTest {

    /**
     * Per-step ceiling. Calibrated for the {@code test} profile: each top-level event should
     * finish well under 5 s. Anything over 8 s is a signal that a new slow bean was added to the
     * critical path. The threshold is intentionally loose — its job is to fail loudly on an order
     * of magnitude regression, not nudge for marginal slowdowns.
     */
    private static final Duration PER_STEP_CEILING = Duration.ofSeconds(8);

    @Autowired
    private ApplicationStartup applicationStartup;

    @Test
    @DisplayName("BufferingApplicationStartup is wired and captured at least the boot sequence")
    void bufferingApplicationStartup_isWired() {
        assertThat(applicationStartup)
            .as(
                "Application.main() must invoke setApplicationStartup(new BufferingApplicationStartup(...));" +
                    " if this fails, the /actuator/startup endpoint is broken in local profile too"
            )
            .isInstanceOf(BufferingApplicationStartup.class);

        var buffering = (BufferingApplicationStartup) applicationStartup;
        var events = buffering.getBufferedTimeline().getEvents();
        assertThat(events)
            .as(
                "BufferingApplicationStartup captured no events — buffer capacity may be too small, or wiring is broken"
            )
            .isNotEmpty();
    }

    @Test
    @DisplayName("No single top-level startup step exceeds the per-step ceiling")
    void perStepCeiling_isRespected() {
        var buffering = (BufferingApplicationStartup) applicationStartup;
        var events = buffering.getBufferedTimeline().getEvents();

        var slowest = events
            .stream()
            .filter(e -> e.getStartupStep().getParentId() == null)
            .filter(e -> e.getEndTime() != null)
            .max((a, b) -> a.getDuration().compareTo(b.getDuration()))
            .orElse(null);

        assertThat(slowest)
            .as("Timeline has no completed top-level events — startup did not finish before assertions ran")
            .isNotNull();

        assertThat(slowest.getDuration())
            .as(
                "Top-level startup step '%s' took %s, exceeding the %s ceiling." +
                    " A new bean or @PostConstruct likely landed on the critical path." +
                    " Inspect /actuator/startup in the local profile to identify the culprit.",
                slowest.getStartupStep().getName(),
                slowest.getDuration(),
                PER_STEP_CEILING
            )
            .isLessThan(PER_STEP_CEILING);
    }
}
