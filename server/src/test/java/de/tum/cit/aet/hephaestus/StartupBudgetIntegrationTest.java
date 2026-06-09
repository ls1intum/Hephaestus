package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.TestAsyncConfiguration;
import de.tum.cit.aet.hephaestus.testconfig.TestSecurityConfig;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;
import org.springframework.context.annotation.Import;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Asserts no single bean instantiation blows the per-bean budget — catches a slow
 * {@code @PostConstruct} or heavy synchronous work dragged onto the critical startup path. Does not
 * extend {@code BaseIntegrationTest} because {@code useMainMethod = ALWAYS} is required so the
 * {@link BufferingApplicationStartup} wired in {@link Application#main(String[])} is picked up, and
 * that produces a separate context-cache entry.
 */
@SpringBootTest(useMainMethod = UseMainMethod.ALWAYS)
@ActiveProfiles("test")
@Import({ TestSecurityConfig.class, TestAsyncConfiguration.class })
@Testcontainers
@Tag("integration")
class StartupBudgetIntegrationTest {

    // spring.beans.instantiate is the per-bean creation step; each fires once per bean. See
    // https://docs.spring.io/spring-framework/reference/core/aot.html#spring-startup-events
    private static final String BEAN_INSTANTIATE = "spring.beans.instantiate";

    // Per-bean wall-clock budget. INTENT: catch a bean whose constructor/@PostConstruct does
    // EGREGIOUS synchronous work on the boot critical path (e.g. a blocking network call, a heavy
    // eager cache warm, a migration shoved into bean init) — an order-of-magnitude regression, not a
    // few-hundred-ms creep.
    //
    // WHY 6s and not tighter: spring.beans.instantiate durations are wall time measured on the
    // single-threaded boot path, so they absorb scheduler preemption. Under CI CPU contention the
    // whole timeline dilates together. The legitimately slowest bean on a healthy boot is the
    // DataSource/Hikari + EntityManagerFactory warmup, observed at ~0.6-2.0s here (full healthy
    // distribution: ~1300 beans, median ~1ms, p90 ~27ms, p95 ~72ms, p99 ~280ms, top5
    // [619,621,854,879,1959]ms). The old 3s ceiling sat barely ~1s above that ~2s legitimate peak, so
    // contention on a 2s I/O wait routinely pushed it to 3.0-3.3s and the test flaked WITHOUT any
    // regression. 6s gives ~3x headroom over the legitimate peak: moderate contention inflates a 2s
    // bean toward ~3s, not 6s — crossing 6s means a bean is doing real, excessive work, which is
    // exactly what we want to flag.
    //
    // WHY NOT a relative gate (slowest vs median/p95): the distribution is intrinsically dominated by
    // one I/O-bound bean that is naturally ~27x the p95 bean on a HEALTHY boot, so a ratio gate cannot
    // separate "DataSource at 2s (fine)" from "DataSource at 5s (regressed)" — same shape. An absolute
    // floor with real headroom is the honest, non-flaky signal here.
    private static final Duration PER_BEAN_CEILING = Duration.ofSeconds(6);

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        var postgres = PostgreSQLTestContainer.getInstance();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ApplicationStartup applicationStartup;

    @Test
    void noBeanInstantiationExceedsCeiling() {
        var events = ((BufferingApplicationStartup) applicationStartup).getBufferedTimeline().getEvents();

        var slowest = events
            .stream()
            .filter(e -> BEAN_INSTANTIATE.equals(e.getStartupStep().getName()))
            .filter(e -> e.getEndTime() != null)
            .max((a, b) -> a.getDuration().compareTo(b.getDuration()))
            .orElseThrow(() -> new AssertionError("no " + BEAN_INSTANTIATE + " events captured"));

        assertThat(slowest.getDuration())
            .as(
                "slowest bean instantiation %s exceeded the %s per-bean budget — a bean is doing egregious " +
                    "synchronous work on the startup critical path. Investigate its constructor/@PostConstruct " +
                    "(blocking I/O, eager warm-up, migration). This is sized ~3x above the legitimate DataSource " +
                    "warmup peak, so it should NOT trip on CI contention alone.",
                slowest.getDuration(),
                PER_BEAN_CEILING
            )
            .isLessThan(PER_BEAN_CEILING);
    }
}
