package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceCollectionException;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceContribution;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceSource;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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

    /**
     * The tree's share of the sandbox input budget.
     *
     * <p>Held below {@code SandboxWorkspaceManager.MAX_INPUT_BYTES} so the tree cannot consume the whole
     * of it. Sized to the budget, a tree that filled it would leave nothing for the diff and the
     * conversation and fail the job outright — for an optional source, whose absence is supposed to cost
     * only context. Exceeding this bound truncates the tree and reports the capture as partial instead.
     */
    static final long MAX_TOTAL_BYTES = 32L * 1024 * 1024;

    private final GitRepositoryManager gitRepositoryManager;

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof ContextRequest.PracticeReviewRequest;
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
        // The tree is optional context for every practice that selects it, and a deployment that keeps
        // no working copy is a supported configuration rather than a fault. Throwing here would record
        // a provider failure and emit a warning on every run of such a deployment, so report the
        // absence for what it is.
        SourceCaptureState absence = absenceState(request);
        if (absence != null) {
            return absent(absence);
        }
        GitRepositoryManager.GitTreeSnapshot snapshot = snapshot(request);
        Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        snapshot.files().forEach((path, bytes) -> files.put(SandboxLayout.REPO_MOUNT_RELATIVE + path, bytes));
        SourceCompleteness completeness = snapshot.complete()
            ? SourceCompleteness.COMPLETE
            : SourceCompleteness.PARTIAL;
        // With checkout disabled the snapshot carries no tree, and "<sha>:null" is a non-null
        // string that freshness assessment accepts as a pinned identity, reporting a tree that was
        // never read as current. Report the source as not collected instead.
        if (snapshot.treeSha() == null) {
            return absent(new SourceCaptureState.NotCollected(SourceAbsenceReason.DISABLED));
        }
        return new EvidenceContribution(
            files,
            Map.of(KIND, completeness),
            Map.of(KIND, snapshot.commitSha() + ":" + snapshot.treeSha())
        );
    }

    private static EvidenceContribution absent(SourceCaptureState state) {
        return new EvidenceContribution(
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(KIND, state)
        );
    }

    /** Why no tree can be read for this request, or {@code null} when one can. */
    @Nullable
    private SourceCaptureState absenceState(ContextRequest request) {
        if (!gitRepositoryManager.isEnabled()) {
            return new SourceCaptureState.NotCollected(SourceAbsenceReason.DISABLED);
        }
        if (!(request instanceof ContextRequest.PracticeReviewRequest review)) {
            return null;
        }
        JsonNode metadata = review.job().getMetadata();
        if (metadata == null || !metadata.path("repository_id").isNumber()) {
            return null;
        }
        if (gitRepositoryManager.isRepositoryCloned(metadata.path("repository_id").asLong())) {
            return null;
        }
        return new SourceCaptureState.Unavailable(SourceAbsenceReason.PINNED_HEAD_MISSING);
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
        try {
            return gitRepositoryManager.readTreeSnapshot(repositoryId, commitSha, MAX_TOTAL_BYTES);
        } catch (GitRepositoryManager.GitOperationException e) {
            throw new EvidenceCollectionException("Could not capture repository tree at " + commitSha, e);
        }
    }
}
