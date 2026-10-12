package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

class AgentJobRetentionResultIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeResultRepository practiceResultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewCompositionBasisRepository basisRepository;

    @PersistenceContext
    private @Nullable EntityManager entityManager;

    @Test
    @Transactional
    void keepsAJobThatIsTheOnlyProvenanceForAStoredResult() {
        var owner = persistUser("retention-owner");
        Workspace workspace = createWorkspace("retention-result", "Retention", "retention-org", AccountType.ORG, owner);
        Practice practice = new Practice();
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        practice.setWorkspace(workspace);
        practice.setSlug("review-quality");
        practice.setName("Review quality");
        practice.setCriteria("Review the change");
        PracticeTestEvidence.configure(practice, ScmSignals.PULL_REQUEST_OPENED);
        practice.setAutonomy(PracticeAutonomy.AUTOMATIC);
        practice = practiceRepository.save(practice);

        AgentJob referenced = oldTerminalJob(workspace);
        AgentJob unreferenced = oldTerminalJob(workspace);
        jobRepository.saveAllAndFlush(List.of(referenced, unreferenced));
        UUID practiceResultId = UUID.randomUUID();
        practiceResultRepository.insertIfAbsent(
                practiceResultId,
                "retention-" + practiceResultId,
                referenced.getId(),
                referenced.getWorkspace().getId(),
                practice.getId(),
                null,
                ArtifactKinds.PULL_REQUEST.value(),
                7L,
                owner.getId(),
                "Stored result",
                "NOT_MET",
                "MAJOR",
                "{}",
                "Reasoning",
                "test-scope",
                null,
                null,
                null,
                Instant.now(),
                "LIVE");

        int deleted = jobRepository.deleteUnreferencedTerminalRowsOlderThan(
                Instant.now().minus(Duration.ofDays(90)), 100);

        assertThat(deleted).isEqualTo(1);
        assertThat(jobRepository.existsById(referenced.getId())).isTrue();
        assertThat(jobRepository.existsById(unreferenced.getId())).isFalse();
    }

    /**
     * Original writer inputs live only while a writer could still use them, and only their own workspace reads them.
     */
    @Test
    @Transactional
    void removesOriginalCompositionInputsOnceNoWriterCanUseThem() {
        var owner = persistUser("basis-owner");
        Workspace workspace = createWorkspace("basis-retention", "Basis", "basis-org", AccountType.ORG, owner);
        Workspace other = createWorkspace("basis-other", "Other", "basis-other-org", AccountType.ORG, owner);
        AgentJob ended = oldTerminalJob(workspace);
        AgentJob running = oldJob(workspace, AgentJobStatus.RUNNING);
        AgentJob expired = oldJob(workspace, AgentJobStatus.RUNNING);
        jobRepository.saveAllAndFlush(List.of(ended, running, expired));
        Instant now = Instant.now();
        basisRepository.save(basis(ended, now.plus(Duration.ofHours(1))));
        basisRepository.save(basis(running, now.plus(Duration.ofHours(1))));
        basisRepository.save(basis(expired, now.minus(Duration.ofSeconds(1))));

        assertThat(basisRepository.deleteUnusable(now, 100)).isEqualTo(2);

        assertThat(basisRepository.findByWorkspaceIdAndJobId(workspace.getId(), running.getId()))
                .isPresent();
        assertThat(basisRepository.findByWorkspaceIdAndJobId(other.getId(), running.getId()))
                .isEmpty();
        assertThat(basisRepository.findByWorkspaceIdAndJobId(workspace.getId(), ended.getId()))
                .isEmpty();
        assertThat(basisRepository.findByWorkspaceIdAndJobId(workspace.getId(), expired.getId()))
                .isEmpty();
    }

    @Test
    @Transactional
    void shouldPreserveTheOriginalInputDigestThroughPostgresJsonb() {
        var owner = persistUser("basis-json-owner");
        Workspace workspace = createWorkspace("basis-json", "Basis", "basis-json-org", AccountType.ORG, owner);
        AgentJob job = jobRepository.saveAndFlush(oldTerminalJob(workspace));
        var original = Map.of(
                "z.md",
                "Original α\n".getBytes(StandardCharsets.UTF_8),
                "a.json",
                "{\"original\":true}".getBytes(StandardCharsets.UTF_8));
        var payload = objectMapper.createObjectNode();
        var files = payload.putObject("files");
        original.forEach((path, bytes) -> files.put(path, Base64.getEncoder().encodeToString(bytes)));
        var kept = basis(job, Instant.now().plus(Duration.ofHours(1)));
        kept.setPayload(payload);
        kept.setBasisDigest(ReviewCompositionBasis.digest(original));
        kept.setByteSize(objectMapper.writeValueAsBytes(payload).length);
        basisRepository.save(kept);
        var persistence = Objects.requireNonNull(entityManager);
        persistence.flush();
        persistence.clear();

        var reloaded = basisRepository
                .findByWorkspaceIdAndJobId(workspace.getId(), job.getId())
                .orElseThrow();
        var decoded = new TreeMap<String, byte[]>();
        reloaded.getPayload()
                .path("files")
                .properties()
                .forEach(entry -> decoded.put(
                        entry.getKey(),
                        Base64.getDecoder().decode(entry.getValue().asString())));
        assertThat(ReviewCompositionBasis.digest(decoded)).isEqualTo(kept.getBasisDigest());
        original.forEach((path, bytes) -> assertThat(decoded.get(path)).containsExactly(bytes));
    }

    private ReviewCompositionBasis basis(AgentJob job, Instant expiresAt) {
        var basis = new ReviewCompositionBasis();
        basis.setJobId(job.getId());
        basis.setWorkspaceId(job.getWorkspace().getId());
        basis.setAdmissionAttempt(0);
        basis.setAdmissionDigest("a".repeat(64));
        basis.setBasisVersion(1);
        basis.setBasisDigest("b".repeat(64));
        basis.setCopyId(UUID.randomUUID());
        basis.setByteSize(2);
        basis.setCreatedAt(expiresAt.minus(Duration.ofHours(2)));
        basis.setExpiresAt(expiresAt);
        basis.setPayload(objectMapper.createObjectNode());
        return basis;
    }

    private AgentJob oldTerminalJob(Workspace workspace) {
        return oldJob(workspace, AgentJobStatus.COMPLETED);
    }

    private AgentJob oldJob(Workspace workspace, AgentJobStatus status) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(status);
        if (status == AgentJobStatus.COMPLETED) {
            job.setDeliveryStatus(DeliveryStatus.DELIVERED);
            job.setCompletedAt(Instant.now().minus(Duration.ofDays(100)));
        }
        job.setConfigSnapshot(objectMapper.valueToTree(Map.of("model", "test")));
        return job;
    }
}
