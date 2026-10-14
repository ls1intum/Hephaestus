package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
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
        assertThat(transactions.execute(status -> jobs.transitionStatusOwnedBy(
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
        assertThat(transactions.execute(status -> jobs.transitionStatusOwnedBy(
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
        assertThat(transactions.execute(status -> jobs.failAdmittedOwnedBy(
                        job.getId(), "owner", 0, Instant.now(), ResultAdmissionService.INTERRUPTED_AFTER_ADMISSION)))
                .isEqualTo(1);
        var stored = jobs.findById(job.getId()).orElseThrow();
        assertThat(stored.getAdmissionAttempt()).isZero();
        assertThat(stored.getAdmissionDigest()).isEqualTo("a".repeat(64));
        assertThat(stored.getAttempt()).isZero();
        assertThat(stored.getStatus()).isEqualTo(ReviewJobStatus.FAILED);
    }
}
