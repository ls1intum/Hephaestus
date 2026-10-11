package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.handler.ObservationAdmissionService.AdmissionIdentity;
import de.tum.cit.aet.hephaestus.agent.handler.ObservationAdmissionService.StaleAttemptException;
import de.tum.cit.aet.hephaestus.agent.handler.inapp.InAppRoutingDecision;
import de.tum.cit.aet.hephaestus.agent.handler.inapp.InAppSupportContext;
import de.tum.cit.aet.hephaestus.agent.handler.inapp.InAppSupportReader;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedObservations;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCopyFence;
import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@MockitoSpyBean(types = {PullRequestReviewHandler.class, InAppSupportReader.class, JobEvidenceFiles.class})
class ObservationAdmissionConcurrencyIntegrationTest extends BaseIntegrationTest {

    private static final int TIMEOUT_SECONDS = 30;

    @Autowired
    private JobEvidenceFiles evidenceFiles;

    @Autowired
    private PersonDataCopyFence copyFence;

    @Autowired
    private FabricLayout fabricLayout;

    @Autowired
    private PullRequestReviewHandler reviewHandler;

    @Autowired
    private InAppSupportReader supportReader;

    @Autowired
    private ObservationAdmissionService admission;

    @Autowired
    private AgentJobRepository jobs;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper mapper;

    @ParameterizedTest
    @EnumSource(InAppSupportContext.State.class)
    void shouldSerializeSupportThroughTheConfiguredOptionalFieldContract(InAppSupportContext.State state) {
        var occurrence = new InAppSupportContext.Occurrence(
                UUID.randomUUID(),
                "scm.pull_request",
                21L,
                "NOT_MET",
                "LIVE",
                "An observed problem",
                null,
                null,
                null,
                "2026-10-08T00:00:00Z");
        var context = new InAppSupportContext(
                state,
                "2026-10-08T00:00:01Z",
                null,
                state == InAppSupportContext.State.COMPLETE
                        ? List.of(new InAppSupportContext.PracticeSupport(
                                "handles-errors", InAppRoutingDecision.ADMIT, List.of(occurrence)))
                        : List.of());

        var wire = mapper.valueToTree(context);

        assertThat(wire.path("state").asString()).isEqualTo(state.name());
        assertThat(wire.path("readAt").asString()).isEqualTo(context.readAt());
        assertThat(wire.has("refusal")).isFalse();
        if (state == InAppSupportContext.State.COMPLETE) {
            assertThat(wire.path("practices").path(0).path("decision").asString())
                    .isEqualTo("ADMIT");
            var selected = wire.path("practices").path(0).path("occurrences").path(0);
            assertThat(selected.path("observationId").asString())
                    .isEqualTo(occurrence.observationId().toString());
            assertThat(selected.path("artifactId").asLong()).isEqualTo(21L);
            assertThat(selected.path("outcome").asString()).isEqualTo("NOT_MET");
            assertThat(selected.has("evidenceRationale")).isFalse();
            assertThat(selected.has("evidence")).isFalse();
            assertThat(selected.has("practiceRevisionId")).isFalse();
        }
    }

    @Test
    void shouldKeepCommittedAdmissionWhenItsSupportReadFails() {
        var workspace = workspaces.save(WorkspaceTestFixtures.activeWorkspace(
                "support-failure-" + UUID.randomUUID().toString().substring(0, 8)));
        var job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(mapper.createObjectNode());
        job.setStatus(AgentJobStatus.RUNNING);
        job.setWorkerId("support-worker");
        job.setMetadata(mapper.createObjectNode().put("preserved", "value"));
        var saved = jobs.saveAndFlush(job);
        PreparedObservations publication = admitted -> {
            admitted.setMetadata(mapper.createObjectNode().put("admitted", true));
            jobs.saveAndFlush(admitted);
        };
        doReturn(publication)
                .when(reviewHandler)
                .prepareObservations(argThat(candidate -> candidate.getId().equals(saved.getId())), any());
        doThrow(new IllegalStateException("support unavailable"))
                .when(supportReader)
                .snapshot(argThat(candidate -> candidate.getId().equals(saved.getId())), any());

        var response = admission.admit(
                new AdmissionIdentity(saved.getId(), workspace.getId(), 0, "support-worker"), mapper.createArrayNode());

        var persisted = jobs.findById(saved.getId()).orElseThrow();
        var metadata = Objects.requireNonNull(persisted.getMetadata());
        assertThat(metadata.path("admitted").asBoolean()).isTrue();
        assertThat(metadata.path(ObservationAdmissionService.DIGEST_METADATA_KEY)
                        .asString())
                .isEqualTo(response.path("admissionDigest").asString())
                .isNotBlank();
        assertThat(persisted.getStatus()).isEqualTo(AgentJobStatus.RUNNING);
        assertThat(response.path("inAppSupport").path("state").asString()).isEqualTo("UNAVAILABLE");
        assertThat(response.path("observations").isArray()).isTrue();
    }

    @Test
    void shouldKeepPublicationRefusalAfterItsWritesRollBack() {
        var workspace = workspaces.save(WorkspaceTestFixtures.activeWorkspace(
                "publish-refusal-" + UUID.randomUUID().toString().substring(0, 8)));
        var job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(mapper.createObjectNode());
        job.setStatus(AgentJobStatus.RUNNING);
        job.setWorkerId("publishing-worker");
        job.setMetadata(mapper.createObjectNode().put("preserved", "value"));
        var saved = jobs.saveAndFlush(job);
        var identity = new AdmissionIdentity(saved.getId(), workspace.getId(), 0, "publishing-worker");
        PreparedObservations publication = admitted -> {
            admitted.setMetadata(mapper.createObjectNode().put("rolled_back", true));
            jobs.saveAndFlush(admitted);
            throw new JobDeliveryException("Reviewed work changed after submission");
        };
        doReturn(publication)
                .when(reviewHandler)
                .prepareObservations(argThat(candidate -> candidate.getId().equals(saved.getId())), any());

        assertThatThrownBy(() -> admission.admit(identity, mapper.createArrayNode()))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessage("Reviewed work changed after submission");

        var reloaded = jobs.findById(saved.getId()).orElseThrow();
        var metadata = Objects.requireNonNull(reloaded.getMetadata());
        assertThat(metadata.path("preserved").asString()).isEqualTo("value");
        assertThat(metadata.has("rolled_back")).isFalse();
        assertThat(metadata.path(ObservationAdmissionService.REFUSAL_METADATA_KEY)
                        .path("reasonCode")
                        .asString())
                .isEqualTo(ObservationAdmissionService.INADMISSIBLE_REASON_CODE);
        assertThat(metadata.path(ObservationAdmissionService.REFUSAL_METADATA_KEY)
                        .path("reason")
                        .asString())
                .isEqualTo("Reviewed work changed after submission");
        assertThat(ObservationAdmissionService.isAdmitted(reloaded)).isFalse();
        assertThat(reloaded.getStatus()).isEqualTo(AgentJobStatus.RUNNING);
    }

    @Test
    void shouldRejectStaleRefusalWhenOwnershipChangesWhileWaitingForTheJobLock() throws Exception {
        var workspace = workspaces.save(WorkspaceTestFixtures.activeWorkspace(
                "admission-race-" + UUID.randomUUID().toString().substring(0, 8)));
        var job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(mapper.createObjectNode());
        job.setStatus(AgentJobStatus.RUNNING);
        job.setWorkerId("original-worker");
        var metadata = mapper.createObjectNode().put("preserved", "value");
        job.setMetadata(metadata);
        var saved = jobs.saveAndFlush(job);
        var identity = new AdmissionIdentity(saved.getId(), workspace.getId(), 0, "original-worker");
        var locked = new CountDownLatch(1);
        var dispatched = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var owner = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                var current =
                        jobs.findByIdWithWorkspaceForUpdate(identity.jobId()).orElseThrow();
                locked.countDown();
                await(dispatched);
                awaitBlockedTransaction();
                current.setRetryCount(1);
                current.setWorkerId("replacement-worker");
                current.setStatus(AgentJobStatus.CANCELLED);
                jobs.save(current);
            }));
            var refusal = pool.submit(() -> {
                await(locked);
                dispatched.countDown();
                assertThatThrownBy(() -> admission.recordRefusal(
                                identity, "INVALID_EVIDENCE", "stale submission", mapper.createArrayNode()))
                        .isInstanceOf(StaleAttemptException.class);
            });
            owner.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            refusal.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
        var reloaded = jobs.findById(identity.jobId()).orElseThrow();
        assertThat(reloaded.getMetadata()).isEqualTo(metadata);
        assertThat(reloaded.getRetryCount()).isEqualTo(1);
        assertThat(reloaded.getWorkerId()).isEqualTo("replacement-worker");
        assertThat(reloaded.getStatus()).isEqualTo(AgentJobStatus.CANCELLED);
    }

    @Test
    void shouldReleaseTheJobLockDuringVerificationAndFenceItsLaterPublication() throws Exception {
        var workspace = workspaces.save(WorkspaceTestFixtures.activeWorkspace(
                "verification-race-" + UUID.randomUUID().toString().substring(0, 8)));
        var job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(mapper.createObjectNode());
        job.setStatus(AgentJobStatus.RUNNING);
        job.setWorkerId("verifying-worker");
        job.setMetadata(mapper.createObjectNode());
        var saved = jobs.saveAndFlush(job);
        var identity = new AdmissionIdentity(saved.getId(), workspace.getId(), 0, "verifying-worker");
        var verifying = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    verifying.countDown();
                    await(release);
                    return (PreparedObservations) admitted -> {};
                })
                .when(reviewHandler)
                .prepareObservations(argThat(candidate -> candidate.getId().equals(saved.getId())), any());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var admissionResult =
                    pool.submit(() -> assertThatThrownBy(() -> admission.admit(identity, mapper.createArrayNode()))
                            .isInstanceOf(StaleAttemptException.class));
            await(verifying);
            var reassignment = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                var current = jobs.findByIdWithWorkspaceForUpdate(saved.getId()).orElseThrow();
                current.setRetryCount(1);
                current.setWorkerId("replacement-worker");
                jobs.save(current);
            }));
            reassignment.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            release.countDown();
            admissionResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
        var reloaded = jobs.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getRetryCount()).isEqualTo(1);
        assertThat(reloaded.getMetadata()).isEqualTo(mapper.createObjectNode());
    }

    /**
     * An erasure that reaches the admitting attempt's receipt after its inputs were read but before the admission
     * commits leaves no kept inputs; one that begins after the receipt was rechecked waits for the commit and then
     * finds the kept inputs under the job it erases. Either way no raw input outlives the erasure unseen.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldNeverKeepOriginalInputsAnErasureCannotSee(boolean erasureFirst) throws Exception {
        var workspace = workspaces.save(WorkspaceTestFixtures.activeWorkspace(
                "basis-erasure-" + UUID.randomUUID().toString().substring(0, 8)));
        var job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(mapper.createObjectNode());
        job.setStatus(AgentJobStatus.RUNNING);
        job.setWorkerId("basis-worker");
        job.setMetadata(mapper.createObjectNode());
        var saved = jobs.saveAndFlush(job);
        var identity = new AdmissionIdentity(saved.getId(), workspace.getId(), 0, "basis-worker");
        UUID copyId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO person_evidence_copy(id,workspace_id,job_id,store_id,state,payload) "
                        + "VALUES (?,?,?,CAST(? AS uuid),'READY',CAST(? AS jsonb))",
                copyId,
                workspace.getId(),
                saved.getId(),
                localStoreId(),
                "{\"attempt\":0,\"identities\":[],\"repositories\":[],\"dependencies\":\"COMPLETE\"}");
        doReturn((PreparedObservations) admitted -> {})
                .when(reviewHandler)
                .prepareObservations(argThat(candidate -> candidate.getId().equals(saved.getId())), any());
        var pool = Executors.newFixedThreadPool(1);
        var seenByErasure = new AtomicReference<Integer>();
        Runnable erasure = () -> transactions.executeWithoutResult(tx -> {
            copyFence.holdForErasure();
            seenByErasure.set(Objects.requireNonNull(jdbc.queryForObject(
                    "SELECT count(*) FROM review_composition_basis WHERE job_id = ?", Integer.class, saved.getId())));
            jdbc.update("UPDATE person_evidence_copy SET state = 'ERASE_REQUESTED' WHERE id = ?", copyId);
        });
        var pending = new AtomicReference<Future<?>>();
        try {
            doAnswer(invocation -> {
                        if (erasureFirst) pool.submit(erasure).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        return Optional.of(Map.of(
                                SandboxLayout.FEEDBACK_COMPOSITION_PATH,
                                "{\"channels\":{\"IN_CONTEXT\":{\"enabled\":true}}}".getBytes(StandardCharsets.UTF_8)));
                    })
                    .when(evidenceFiles)
                    .originalPublicInputs(identity);
            doAnswer(invocation -> {
                        Object vouches = invocation.callRealMethod();
                        if (!erasureFirst) {
                            pending.set(pool.submit(erasure));
                            awaitBlockedTransaction();
                        }
                        return vouches;
                    })
                    .when(evidenceFiles)
                    .holdVouchingReceipt(identity, copyId);

            admission.admit(identity, mapper.createArrayNode());

            if (!erasureFirst) pending.get().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(ObservationAdmissionService.isAdmitted(
                        jobs.findById(saved.getId()).orElseThrow()))
                .isTrue();
        Integer kept = jdbc.queryForObject(
                "SELECT count(*) FROM review_composition_basis WHERE job_id = ?", Integer.class, saved.getId());
        if (erasureFirst) {
            assertThat(seenByErasure.get()).isZero();
            assertThat(kept).isZero();
        } else {
            assertThat(seenByErasure.get()).isEqualTo(1);
            assertThat(kept).isEqualTo(1);
        }
    }

    /** The mounted evidence store's identity, as the capture that wrote a receipt would have created it. */
    private String localStoreId() throws IOException {
        var file = fabricLayout.root().resolve(".person-evidence-store-id");
        Files.createDirectories(fabricLayout.root());
        if (!Files.exists(file)) Files.writeString(file, UUID.randomUUID().toString());
        return Files.readString(file).strip();
    }

    private void awaitBlockedTransaction() {
        Integer holderPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
        Awaitility.await()
                .pollInSameThread()
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> {
                    // Refresh activity statistics on the transaction holding the lock.
                    jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    return Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT EXISTS (SELECT FROM pg_stat_activity WHERE CAST(? AS integer) = ANY(pg_blocking_pids(pid)))",
                            Boolean.class,
                            holderPid));
                });
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
