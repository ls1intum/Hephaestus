package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceContribution;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceSource;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Materialises a pinned commit tree without exposing the host clone, {@code .git}, history, symlinks, or
 * submodules to the sandbox. Exclusions and size bounds make the reported capture partial.
 */
@Component
@Order(1_000)
@RequiredArgsConstructor
public class RepositoryTreeContentSource implements EvidenceSource {

    private static final SourceKind KIND = new SourceKind("scm.repository.tree");

    @Override
    public Set<SourceKind> sourceKinds() {
        return Set.of(KIND);
    }

    @Override
    public SourceKind sourceKindFor(String path) {
        return KIND;
    }

    static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;

    private final GitRepositoryManager gitRepositoryManager;

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof ContextRequest.PracticeReviewRequest;
    }

    @Override
    public String originId() {
        return "scm";
    }

    @Override
    public boolean ownsPath(String path) {
        return path.startsWith(SandboxLayout.REPO_MOUNT_RELATIVE);
    }

    @Override
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        snapshot(request)
            .files()
            .forEach((path, bytes) -> files.put(SandboxLayout.REPO_MOUNT_RELATIVE + path, bytes));
    }

    @Override
    public EvidenceContribution capture(ContextRequest request, Set<SourceKind> selectedKinds) {
        if (!selectedKinds.contains(KIND)) {
            return new EvidenceContribution(Map.of(), Map.of());
        }
        GitRepositoryManager.GitTreeSnapshot snapshot = snapshot(request);
        Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        snapshot.files().forEach((path, bytes) -> files.put(SandboxLayout.REPO_MOUNT_RELATIVE + path, bytes));
        SourceCompleteness completeness = snapshot.complete()
            ? SourceCompleteness.COMPLETE
            : SourceCompleteness.PARTIAL;
        return new EvidenceContribution(
            files,
            Map.of(KIND, completeness),
            Map.of(KIND, snapshot.commitSha() + ":" + snapshot.treeSha())
        );
    }

    private GitRepositoryManager.GitTreeSnapshot snapshot(ContextRequest request) {
        if (!(request instanceof ContextRequest.PracticeReviewRequest review)) {
            throw new IllegalStateException("Repository-tree capture requires a pull request review");
        }
        JsonNode metadata = review.job().getMetadata();
        if (metadata == null || !metadata.path("repository_id").isNumber()) {
            throw new JobPreparationException("Pull request job has no repository_id: jobId=" + review.job().getId());
        }
        String commitSha = metadata.path("commit_sha").asString();
        if (commitSha == null || commitSha.isBlank()) {
            throw new JobPreparationException("Pull request job has no commit_sha: jobId=" + review.job().getId());
        }
        long repositoryId = metadata.path("repository_id").asLong();
        if (!gitRepositoryManager.isRepositoryCloned(repositoryId)) {
            throw new JobPreparationException(
                "Repository not cloned: repoId=" + repositoryId + ", jobId=" + review.job().getId()
            );
        }
        return gitRepositoryManager.readTreeSnapshot(repositoryId, commitSha, MAX_TOTAL_BYTES);
    }
}
