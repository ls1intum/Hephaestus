package de.tum.cit.aet.hephaestus.integration.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Boot-time integration test that proves the unified
 * {@link IntegrationMessageHandlerRegistry} actually carries every production handler
 * after Slice F. Complements
 * {@link de.tum.cit.aet.hephaestus.architecture.IntegrationMessageHandlerArchTest}
 * (which only sees production bytecode, not the Spring runtime).
 *
 * <p>The registry constructor's duplicate-key fail-fast already protects boot from
 * misregistration; if the application context starts at all, the keys are unique by
 * definition. What this test adds:
 *
 * <ul>
 *   <li>Asserts the {@link IntegrationMessageHandlerRegistry#handlerCount()} value is
 *       at or above the floor we expect (30) — guards against a future refactor that
 *       accidentally drops the {@code @Component} annotation or moves handlers out of
 *       the component-scan path, which would not fail at boot but would silently route
 *       traffic to ACK-as-no-op.</li>
 *   <li>Asserts the count is at most 100, a safety upper-bound that flags accidental
 *       duplicates from over-eager component scans (the duplicate-key fast-fail covers
 *       same-key duplicates; this covers distinct-key bloat).</li>
 * </ul>
 */
@DisplayName("IntegrationMessageHandlerRegistry boot wiring")
class IntegrationMessageHandlerRegistryBootIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IntegrationMessageHandlerRegistry registry;

    @Test
    @DisplayName("registry carries every production handler at boot (>= 30)")
    void registryIsPopulated() {
        int count = registry.handlerCount();
        // Floor: 24 GitHub + 8 GitLab − a couple of conditional-on-property handlers
        // (some GitLab handlers are gated by hephaestus.gitlab.enabled). 30 is the
        // most permissive floor that still proves the registry is wired.
        assertThat(count).as("handlerCount must be >= 30 after Slice F").isGreaterThanOrEqualTo(30);
    }

    @Test
    @DisplayName("handler count stays inside a sane upper bound (no scan bloat)")
    void registryDoesNotOverpopulate() {
        int count = registry.handlerCount();
        assertThat(count)
            .as("handlerCount unexpectedly high — likely an over-eager component scan")
            .isLessThanOrEqualTo(100);
    }
}
