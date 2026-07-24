package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Locks the consumer surface in place.
 *
 * <ol>
 *   <li>{@code integration/scm/sync/} holds no non-{@code backfill} production code.
 *       The unified consumer fleet lives under {@code integration/consumer/}; per-kind
 *       sync drivers live under {@code integration/<kind>/sync/}. New code in the legacy
 *       package would re-grow the coupling this boundary exists to prevent.</li>
 *   <li>The {@code agent/} runtime role never imports {@code integration.consumer..}.
 *       Consumer beans are server-role-only ({@link de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole#SERVER_PROPERTY});
 *       letting the agent role link against them would re-introduce the bean-cluster
 *       leak that ADR 0008 separates by role.</li>
 * </ol>
 *
 * <p>Both rules are intentionally allow-list shaped: violations name the offending class so
 * accidental drift from a refactor surfaces as a precise failure, not a vague "package
 * boundary breached".
 */
class IntegrationConsumerBoundaryTest extends HephaestusArchitectureTest {

    /** Packages allowed to remain under {@code integration/scm/sync/}. */
    private static final List<String> ALLOWED_SYNC_SUBPACKAGES = List.of(
        // Historical-backfill scheduler + service stay here for the duration of this slice;
        // they are Slice-C territory and not in scope for the consumer dissolution.
        "de.tum.cit.aet.hephaestus.integration.scm.sync.backfill"
    );

    /**
     * Production code must not reside directly under {@code integration/scm/sync/}; only the
     * allowed sub-packages ({@code backfill}) remain populated. New consumer or sync
     * orchestration code belongs under {@code integration/consumer/} or
     * {@code integration/<kind>/sync/}.
     */
    @Test
    void scmSyncIsEmptyOfNonBackfillCode() {
        List<String> violations = classes
            .stream()
            .filter(c -> c.getPackageName().equals("de.tum.cit.aet.hephaestus.integration.scm.sync"))
            .map(JavaClass::getFullName)
            .collect(Collectors.toList());

        assertThat(violations)
            .as(
                "integration/scm/sync/ must be empty of production code — the unified " +
                    "consumer lives under integration/consumer/ and the per-kind sync drivers live " +
                    "under integration/<kind>/sync/. Sub-packages allowed: %s",
                ALLOWED_SYNC_SUBPACKAGES
            )
            .isEmpty();
    }

    /**
     * The agent runtime role must not link against the integration consumer fleet. Consumer
     * beans are server-role-only; the agent job queue (#1368) is the {@code agent_job} Postgres
     * table, polled by {@code AgentJobExecutor} — no NATS connection of its own at all. Mixing the
     * two would break role isolation.
     */
    @Test
    void agentDoesNotDependOnIntegrationConsumer() {
        noClasses()
            .that()
            .resideInAPackage("de.tum.cit.aet.hephaestus.agent..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("de.tum.cit.aet.hephaestus.integration.core.consumer..")
            .because(
                "the agent job queue (#1368) is Postgres-backed, not NATS-backed; depending on " +
                    "integration.consumer would mix bean clusters across roles and break the " +
                    "runtime-role isolation locked by RuntimeRoleBoundaryTest"
            )
            .check(classes);
    }

    /**
     * Strengthened boundary (#1368 NATS→Postgres cutover): the agent runtime role must not link
     * against jnats AT ALL anymore — not even its own connection. The queue is the {@code agent_job}
     * table; delivery is poll-based. A jnats import anywhere under {@code agent/} would mean the
     * cutover regressed (e.g. a stray NATS-based feature re-added without going through the queue).
     */
    @Test
    void agentDoesNotDependOnJnats() {
        noClasses()
            .that()
            .resideInAPackage("de.tum.cit.aet.hephaestus.agent..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.nats..")
            .because(
                "the agent job queue (#1368) is Postgres-backed (agent_job table, polled by " +
                    "AgentJobExecutor) — the agent package must have zero io.nats dependency"
            )
            .check(classes);
    }
}
