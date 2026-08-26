package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchInsert;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchRepository;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PracticeFeedbackDispatchRepositoryIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private FeedbackDispatchRepository dispatchRepository;

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Workspace workspace;
    private UUID jobId;

    @BeforeEach
    void seedDispatchOwner() {
        User owner = persistUser("dispatch-owner");
        workspace = createWorkspace("dispatch", "Dispatch", "dispatch-org", AccountType.ORG, owner);
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.COMPLETED);
        job.setConfigSnapshot(tools.jackson.databind.node.JsonNodeFactory.instance.objectNode());
        jobId = jobRepository.saveAndFlush(job).getId();
    }

    @Test
    void shouldAllowExactlyOneConcurrentClaim() throws Exception {
        UUID dispatchId = insertDispatch(workspace.getId(), jobId, "claim-race");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claimAfter(start, dispatchId, "first"));
            var second = executor.submit(() -> claimAfter(start, dispatchId, "second"));
            start.countDown();

            assertThat(
                List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(0, 1);
        }
    }

    @Test
    void shouldFenceTheOldOwnerAfterExpiredLeaseTakeover() {
        UUID dispatchId = insertDispatch(workspace.getId(), jobId, "lease-takeover");
        assertThat(claim(dispatchId, "first", Instant.now().plusSeconds(60))).isEqualTo(1);
        assertThat(claim(dispatchId, "second", Instant.now().plusSeconds(60))).isZero();

        jdbcTemplate.update(
            "UPDATE feedback_dispatch SET lease_expires_at = ? WHERE id = ?",
            Timestamp.from(Instant.now().minusSeconds(1)),
            dispatchId
        );

        assertThat(claim(dispatchId, "second", Instant.now().plusSeconds(60))).isEqualTo(1);
        assertThat(beginWrite(dispatchId, "first")).isZero();
        assertThat(beginWrite(dispatchId, "second")).isEqualTo(1);
    }

    @Test
    void shouldRejectAJobFromAnotherWorkspace() {
        User otherOwner = persistUser("other-dispatch-owner");
        Workspace other = createWorkspace("other-dispatch", "Other", "other-org", AccountType.ORG, otherOwner);

        assertThat(tryInsertDispatch(other.getId(), jobId, "cross-tenant")).isNull();
    }

    @Test
    void shouldNeverReclaimTerminalFailure() {
        UUID dispatchId = insertDispatch(workspace.getId(), jobId, "terminal-failure");
        jdbcTemplate.update(
            "UPDATE feedback_dispatch SET state = 'FAILED', lease_expires_at = ? WHERE id = ?",
            Timestamp.from(Instant.now().minusSeconds(1)),
            dispatchId
        );

        assertThat(claim(dispatchId, "late-redelivery", Instant.now().plusSeconds(60))).isZero();
    }

    @Test
    void shouldKeepReconcilingAnAmbiguousWriteAfterTheRetryBudget() {
        UUID dispatchId = insertDispatch(workspace.getId(), jobId, "ambiguous-write");
        jdbcTemplate.update(
            "UPDATE feedback_dispatch SET state = 'UNCERTAIN', write_started = TRUE, attempt_count = 8 WHERE id = ?",
            dispatchId
        );

        assertThat(claim(dispatchId, "reconciler", Instant.now().plusSeconds(60))).isEqualTo(1);
    }

    @Test
    void recoveryQueriesNeverExhaustAnAmbiguousWrite() {
        UUID safeToFail = insertDispatch(workspace.getId(), jobId, "safe-to-fail");
        UUID ambiguous = insertDispatch(workspace.getId(), jobId, "ambiguous-beyond-budget");
        jdbcTemplate.update(
            "UPDATE feedback_dispatch SET state = 'UNCERTAIN', attempt_count = 8 WHERE id = ?",
            safeToFail
        );
        jdbcTemplate.update(
            "UPDATE feedback_dispatch SET state = 'UNCERTAIN', write_started = TRUE, attempt_count = 8 WHERE id = ?",
            ambiguous
        );

        Instant now = Instant.now().plusSeconds(1);
        assertThat(dispatchRepository.findRecoverable(now, 8, PageRequest.of(0, 10)))
            .extracting(dispatch -> dispatch.getId())
            .contains(ambiguous)
            .doesNotContain(safeToFail);
        assertThat(dispatchRepository.findExhausted(now, 8, PageRequest.of(0, 10)))
            .extracting(dispatch -> dispatch.getId())
            .contains(safeToFail)
            .doesNotContain(ambiguous);
    }

    @Test
    void shouldResetOnlyFailuresKnownToPrecedeTheProviderWrite() {
        UUID safe = insertDispatch(workspace.getId(), jobId, "safe-retry");
        UUID ambiguous = insertDispatch(workspace.getId(), jobId, "ambiguous-retry");
        jdbcTemplate.update(
            "UPDATE feedback_dispatch SET state = 'FAILED', attempt_count = 8, last_error = 'failed' WHERE id = ?",
            safe
        );
        jdbcTemplate.update(
            "UPDATE feedback_dispatch SET state = 'FAILED', write_started = TRUE, attempt_count = 8 WHERE id = ?",
            ambiguous
        );

        Integer reset = transactions.execute(status ->
            dispatchRepository.resetFailedBeforeWrite(jobId, workspace.getId())
        );
        assertThat(reset).isEqualTo(1);
        assertThat(
            jdbcTemplate.queryForObject("SELECT state FROM feedback_dispatch WHERE id = ?", String.class, safe)
        ).isEqualTo("PENDING");
        assertThat(
            jdbcTemplate.queryForObject("SELECT attempt_count FROM feedback_dispatch WHERE id = ?", Integer.class, safe)
        ).isZero();
        assertThat(
            jdbcTemplate.queryForObject("SELECT state FROM feedback_dispatch WHERE id = ?", String.class, ambiguous)
        ).isEqualTo("FAILED");
    }

    private int claimAfter(CountDownLatch start, UUID dispatchId, String owner) {
        try {
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return claim(dispatchId, owner, Instant.now().plusSeconds(60));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private int claim(UUID dispatchId, String owner, Instant leaseUntil) {
        return transactions.execute(status ->
            dispatchRepository.claim(dispatchId, workspace.getId(), owner, leaseUntil, 8)
        );
    }

    private int beginWrite(UUID dispatchId, String owner) {
        return transactions.execute(status -> dispatchRepository.beginWrite(dispatchId, workspace.getId(), owner));
    }

    private UUID insertDispatch(long workspaceId, UUID owningJobId, String key) {
        return java.util.Objects.requireNonNull(
            tryInsertDispatch(workspaceId, owningJobId, key),
            "the insert this test builds on must land"
        );
    }

    private @Nullable UUID tryInsertDispatch(long workspaceId, UUID owningJobId, String key) {
        UUID id = UUID.randomUUID();
        Integer inserted = transactions.execute(status ->
            dispatchRepository.insertIfAbsent(
                new FeedbackDispatchInsert(
                    id,
                    key,
                    workspaceId,
                    owningJobId,
                    null,
                    "ARTIFACT_SUMMARY",
                    "body",
                    null,
                    "[]"
                )
            )
        );
        return inserted != null && inserted == 1 ? id : null;
    }
}
