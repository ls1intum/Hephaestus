package de.tum.in.www1.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.in.www1.hephaestus.testconfig.TestAsyncConfiguration;
import de.tum.in.www1.hephaestus.testconfig.TestSecurityConfig;
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
 * Asserts no single bean instantiation blows the per-bean ceiling — catches a slow
 * {@code @PostConstruct} or bean added to the critical path. Does not extend
 * {@code BaseIntegrationTest} because {@code useMainMethod = ALWAYS} is required so
 * {@link BufferingApplicationStartup} wired in {@link Application#main(String[])} is picked up,
 * and that produces a separate context-cache entry.
 */
@SpringBootTest(useMainMethod = UseMainMethod.ALWAYS)
@ActiveProfiles("test")
@Import({ TestSecurityConfig.class, TestAsyncConfiguration.class })
@Testcontainers
@Tag("integration")
class StartupBudgetIntegrationTest {

    private static final Duration PER_BEAN_CEILING = Duration.ofSeconds(3);
    // spring.beans.instantiate is the per-bean creation step; each fires once per bean. See
    // https://docs.spring.io/spring-framework/reference/core/aot.html#spring-startup-events
    private static final String BEAN_INSTANTIATE = "spring.beans.instantiate";

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

        assertThat(slowest.getDuration()).isLessThan(PER_BEAN_CEILING);
    }
}
