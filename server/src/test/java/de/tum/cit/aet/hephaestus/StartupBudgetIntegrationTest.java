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
import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;
import org.springframework.context.annotation.Import;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
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

    /**
     * Spring Boot's own JPA warm-up, exempt from the ceiling. What it spends is a JDBC handshake and a
     * schema export, so its wall clock is set by how busy the database and the machine are rather than by
     * anything in this application: 2.7s here on idle cores and 5.4s on busy ones, with no code changed
     * between the two. Budgeting it meant carrying that spread as slack inside the ceiling, and a
     * {@code verify} run spends the slack — the OpenAPI application starts in the pre-integration-test
     * phase and is still finishing its own boot when this context begins. Naming the exemption keeps the
     * ceiling measuring what it is for, which is application beans.
     */
    private static final String JPA_WARM_UP_BEAN = "&entityManagerFactory";

    // Per-bean wall-clock budget: flag a bean doing egregious synchronous work on the boot critical
    // path (blocking I/O, eager warm-up, a migration in bean init). 6s ≈ 4x the slowest application
    // bean, so CI CPU contention can't trip it without a real regression. Absolute, not relative:
    // a ratio gate over a flat distribution can't tell a regression from a busy machine.
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

        var beans = events
            .stream()
            .filter(e -> BEAN_INSTANTIATE.equals(e.getStartupStep().getName()))
            .filter(e -> e.getEndTime() != null)
            .toList();

        assertThat(beans.stream().map(StartupBudgetIntegrationTest::beanNameOf))
            .as(
                "%s is exempt below, so it has to be a bean that is really built — a renamed one would widen the exemption to nothing in silence",
                JPA_WARM_UP_BEAN
            )
            .contains(JPA_WARM_UP_BEAN);

        var slowest = beans
            .stream()
            .filter(e -> !JPA_WARM_UP_BEAN.equals(beanNameOf(e)))
            .max((a, b) -> a.getDuration().compareTo(b.getDuration()))
            .orElseThrow(() -> new AssertionError("no " + BEAN_INSTANTIATE + " events captured"));

        assertThat(slowest.getDuration())
            .as(
                "slowest bean instantiation %s (%s) exceeded the %s budget — that bean is doing egregious " +
                    "synchronous work on the startup path; check its constructor/@PostConstruct.",
                slowest.getDuration(),
                beanNameOf(slowest),
                PER_BEAN_CEILING
            )
            .isLessThan(PER_BEAN_CEILING);
    }

    /** Without the name the failure says a bean is slow but not which, and the timeline is not kept. */
    private static String beanNameOf(StartupTimeline.TimelineEvent event) {
        for (StartupStep.Tag tag : event.getStartupStep().getTags()) {
            if ("beanName".equals(tag.getKey())) {
                return tag.getValue();
            }
        }
        return "bean name not tagged";
    }
}
