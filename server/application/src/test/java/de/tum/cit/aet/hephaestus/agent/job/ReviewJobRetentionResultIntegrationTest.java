package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionService;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ProcessingStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
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

class ReviewJobRetentionResultIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    private PracticeVersionService runtimePracticeVersions;

    @Autowired
    private ReviewJobRepository jobRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private ResultRepository practiceResultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewCompositionBasisRepository basisRepository;

    @PersistenceContext
    private @Nullable EntityManager entityManager;

    /**
     * Original writer inputs live only while a writer could still use them, and only their own workspace reads them.
     */
    @Test
    @Transactional
    void removesOriginalCompositionInputsOnceNoWriterCanUseThem() {
        var owner = persistUser("basis-owner");
        Workspace workspace = createWorkspace("basis-retention", "Basis", "basis-org", AccountType.ORG, owner);
        Workspace other = createWorkspace("basis-other", "Other", "basis-other-org", AccountType.ORG, owner);
        ReviewJob ended = oldTerminalJob(workspace);
        ReviewJob running = oldJob(workspace, ReviewJobStatus.RUNNING);
        ReviewJob expired = oldJob(workspace, ReviewJobStatus.RUNNING);
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
        ReviewJob job = jobRepository.saveAndFlush(oldTerminalJob(workspace));
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

    private ReviewCompositionBasis basis(ReviewJob job, Instant expiresAt) {
        var basis = new ReviewCompositionBasis();
        basis.setJobId(job.getId());
        basis.setWorkspaceId(job.getWorkspaceId());
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

    private ReviewJob oldTerminalJob(Workspace workspace) {
        return oldJob(workspace, ReviewJobStatus.COMPLETED);
    }

    private ReviewJob oldJob(Workspace workspace, ReviewJobStatus status) {
        ReviewJob job = new ReviewJob();
        RuntimeJobTestData.initialize(job, (workspace).getId());

        job.setStatus(status);
        if (status == ReviewJobStatus.COMPLETED) {
            job.setProcessingStatus(ProcessingStatus.DONE);
            job.setCompletedAt(Instant.now().minus(Duration.ofDays(100)));
        }
        job.setConfigSnapshot(objectMapper.valueToTree(Map.of("model", "test")));
        return job;
    }
}
