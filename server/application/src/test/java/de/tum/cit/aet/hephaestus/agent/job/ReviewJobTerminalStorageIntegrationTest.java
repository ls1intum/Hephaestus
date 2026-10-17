package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult.LlmUsage;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageEventRepository;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.agent.usage.MeasurementVerification;
import de.tum.cit.aet.hephaestus.agent.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.UsageSourceType;
import de.tum.cit.aet.hephaestus.integration.core.spi.UsagePricingState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class ReviewJobTerminalStorageIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private ReviewJobRepository jobs;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private LlmUsageRecorder usageRecorder;

    @Autowired
    private LlmUsageEventRepository usage;

    @Autowired
    private ReviewRuntimeRepository runtime;

    @Test
    void shouldKeepSpendPricedAndWriteNoUsageWhenASandboxFailsBeforeAnyModelCall() {
        var job = running("runtime-terminal-zero-calls");
        transactions.executeWithoutResult(status -> {
            job.setExecutionStartedAt(Instant.now());
            job.setStatus(ReviewJobStatus.FAILED);
            job.setExitCode(1);
            jobs.saveAndFlush(job);
            TerminalUsage.resolve(
                            new LlmUsage("model", 0, 0, 0, 0, 0, 0.0, 0),
                            jobs.findLlmUsageById(job.getId()).orElseThrow(),
                            List.of())
                    .appendTo(usageRecorder, jobs, job.getWorkspaceId(), job, null, priced());
        });

        var stored = jobs.findById(job.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReviewJobStatus.FAILED);
        assertThat(stored.getExitCode()).isEqualTo(1);
        assertThat(usage.findByWorkspaceIdAndSourceTypeAndSourceIdOrderBySourceAttemptAsc(
                        job.getWorkspaceId(), UsageSourceType.REVIEW_JOB, job.getId()))
                .isEmpty();
        var spend = runtime.spent(job.getWorkspaceId(), job.getReviewId());
        assertThat(spend.priced()).isTrue();
        assertThat(spend.cost()).isZero();
    }

    @Test
    void shouldKeepUnpricedUsageWhenAFailedSandboxReportsCallsWithoutTokens() {
        var job = running("runtime-terminal-unknown-usage");
        transactions.executeWithoutResult(status -> {
            job.setExecutionStartedAt(Instant.now());
            job.setStatus(ReviewJobStatus.FAILED);
            job.setExitCode(1);
            jobs.saveAndFlush(job);
            TerminalUsage.resolve(new LlmUsage("model", 0, 0, 0, 0, 0, 0.0, 2), null, List.of())
                    .appendTo(usageRecorder, jobs, job.getWorkspaceId(), job, null, priced());
        });

        assertThat(usage.findByWorkspaceIdAndSourceTypeAndSourceIdOrderBySourceAttemptAsc(
                        job.getWorkspaceId(), UsageSourceType.REVIEW_JOB, job.getId()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getTotalCalls()).isEqualTo(2);
                    assertThat(row.getMeasurementVerification()).isEqualTo(MeasurementVerification.UNVERIFIABLE);
                    assertThat(row.getPricingState()).isEqualTo(UsagePricingState.UNPRICED);
                    assertThat(row.getCostUsd()).isNull();
                });
        assertThat(runtime.spent(job.getWorkspaceId(), job.getReviewId()).priced())
                .isFalse();
    }

    private static LlmPriceSnapshot priced() {
        return new LlmPriceSnapshot(
                UsageFundingSource.INSTANCE,
                UsagePricingState.PRICED,
                null,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE);
    }

    private ReviewJob running(String slug) {
        var workspace = workspaces.saveAndFlush(WorkspaceTestFixtures.activeWorkspace(slug));
        var job = new ReviewJob();
        RuntimeJobTestData.initialize(job, workspace.getId());
        job.setConfigSnapshot(new JsonMapper().createObjectNode());
        job.setStatus(ReviewJobStatus.RUNNING);
        job.setWorkerId("owner");
        job.setStartedAt(Instant.now());
        job.prePersist();
        return jobs.saveAndFlush(RuntimeJobTestData.persistParent(jdbc, job));
    }

    @Test
    void shouldKeepTheReviewAndPinnedInputWhenRecordingTheTerminalState() {
        var job = running("runtime-terminal-owner");
        var input = job.getInput();
        assertThat(transactions.<Integer>execute(status -> jobs.transitionStatusOwnedBy(
                        job.getId(),
                        ReviewJobStatus.COMPLETED,
                        Instant.now(),
                        null,
                        Set.of(ReviewJobStatus.RUNNING),
                        "owner")))
                .isEqualTo(1);
        var stored =
                jobs.findByIdAndWorkspaceId(job.getId(), job.getWorkspaceId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReviewJobStatus.COMPLETED);
        assertThat(stored.getReviewId()).isEqualTo(job.getReviewId());
        assertThat(stored.getInput()).isEqualTo(input);
        assertThat(stored.getCompletedAt()).isNotNull();
    }

    @Test
    void shouldRefuseATerminalWriteFromAnotherWorker() {
        var job = running("runtime-terminal-foreign");
        assertThat(transactions.<Integer>execute(status -> jobs.transitionStatusOwnedBy(
                        job.getId(),
                        ReviewJobStatus.FAILED,
                        Instant.now(),
                        "failed",
                        Set.of(ReviewJobStatus.RUNNING),
                        "other")))
                .isZero();
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(ReviewJobStatus.RUNNING);
    }

    @Test
    void shouldPreserveAdmissionFactsWhenALostWriterFails() {
        var job = running("runtime-terminal-admitted");
        job.setAdmissionAttempt(0);
        job.setAdmissionDigest("a".repeat(64));
        jobs.saveAndFlush(job);
        assertThat(transactions.<Integer>execute(status -> jobs.failAdmittedOwnedBy(
                        job.getId(), "owner", 0, Instant.now(), ResultAdmissionService.INTERRUPTED_AFTER_ADMISSION)))
                .isEqualTo(1);
        var stored = jobs.findById(job.getId()).orElseThrow();
        assertThat(stored.getAdmissionAttempt()).isZero();
        assertThat(stored.getAdmissionDigest()).isEqualTo("a".repeat(64));
        assertThat(stored.getAttempt()).isZero();
        assertThat(stored.getStatus()).isEqualTo(ReviewJobStatus.FAILED);
    }
}
