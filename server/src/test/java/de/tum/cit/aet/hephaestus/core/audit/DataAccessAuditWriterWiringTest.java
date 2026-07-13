package de.tum.cit.aet.hephaestus.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Regression guard for a boot-failure class: the only {@link java.time.Clock} bean in the application
 * ({@code authClock}) is server-role-gated, while this writer is injected by the ungated
 * {@code PracticesWorkspacePurgeAdapter} and therefore must wire on the worker and webhook runtimes too.
 * If the writer ever goes back to constructor-injecting {@code Clock}, this context slice (which has no
 * Clock bean, like a worker/webhook pod) fails.
 */
class DataAccessAuditWriterWiringTest extends BaseUnitTest {

    @Test
    @DisplayName("wires without a Clock bean in the context (worker/webhook runtime shape)")
    void wiresWithoutClockBean() {
        new ApplicationContextRunner()
            .withPropertyValues("hephaestus.runtime.server.enabled=false")
            // Satisfies the writer's @PersistenceContext field; the wiring under test is the Clock.
            .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
            .withBean(DataAccessEventRepository.class, () -> mock(DataAccessEventRepository.class))
            .withBean(DataAccessAuditWriter.class)
            .run(context -> assertThat(context).hasSingleBean(DataAccessAuditWriter.class));
    }
}
