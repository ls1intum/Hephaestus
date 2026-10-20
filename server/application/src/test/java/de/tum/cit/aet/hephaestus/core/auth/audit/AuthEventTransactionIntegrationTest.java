package de.tum.cit.aet.hephaestus.core.auth.audit;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AuthEventTransactionIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private AuthEventLogger logger;

    @Autowired
    private AuthEventRepository events;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shouldCommitOneResearchAuditWithItsOwningTransaction() {
        String details = details();

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> assertThat(release(details).recordInCurrentTransaction())
                        .isTrue());

        assertThat(count(details)).isOne();
    }

    @Test
    void shouldRollBackResearchAuditWhenItsOwningTransactionRollsBack() {
        String details = details();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(release(details).recordInCurrentTransaction()).isTrue();
            events.flush();
            assertThat(count(details)).isOne();
            status.setRollbackOnly();
        });

        assertThat(count(details)).isZero();
    }

    @Test
    void shouldKeepExistingIndependentAuditWhenTheCallerRollsBack() {
        String details = details();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(release(details).record()).isTrue();
            status.setRollbackOnly();
        });

        assertThat(count(details)).isOne();
    }

    @Test
    void shouldRefuseJoinedAuditWithoutAnOwningTransaction() {
        String details = details();

        assertThat(release(details).recordInCurrentTransaction()).isFalse();

        assertThat(count(details)).isZero();
    }

    private AuthEventLogger.Draft release(String details) {
        return logger.event(AuthEvent.EventType.RESEARCH_DATASET_RELEASED, AuthEvent.Result.SUCCESS)
                .details(details);
    }

    private long count(String details) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT count(*) FROM auth_event WHERE event_type='RESEARCH_DATASET_RELEASED' AND details=?",
                Long.class,
                details));
    }

    private static String details() {
        return "{\"disclosureId\":\"" + UUID.randomUUID() + "\"}";
    }
}
