package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceCollectionException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class RepositoryTreeContentSourceTest extends BaseUnitTest {

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    private RepositoryTreeContentSource source;

    @BeforeEach
    void setUp() {
        source = new RepositoryTreeContentSource(gitRepositoryManager);
    }

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path stagingDir;

    @Test
    void shouldMaterializePinnedTreeWithoutGitDirectory() {
        AgentJob job = job(17L, "0123456789012345678901234567890123456789");
        when(gitRepositoryManager.isEnabled()).thenReturn(true);
        when(gitRepositoryManager.isRepositoryCloned(17L)).thenReturn(true);
        when(gitRepositoryManager.readTreeSnapshot(17L, "0123456789012345678901234567890123456789"))
                .thenReturn(new GitRepositoryManager.GitTreeSnapshot(
                        stagingDir,
                        "0123456789012345678901234567890123456789",
                        "1123456789012345678901234567890123456789",
                        Map.of("src/App.java", stagingDir.resolve("src/App.java")),
                        12,
                        1,
                        true,
                        Set.of()));

        var contribution = source.capture(new ContextRequest.PracticeReviewRequest(job), source.sourceKinds());

        // Staged by path: the tree's bytes are never held by this process.
        assertThat(contribution.files()).isEmpty();
        assertThat(contribution.filesOnDisk()).containsOnlyKeys("inputs/sources/scm/repo/src/App.java");
        assertThat(contribution.filesOnDisk()).doesNotContainKey("inputs/sources/scm/repo/.git/HEAD");
        verify(gitRepositoryManager).readTreeSnapshot(17L, "0123456789012345678901234567890123456789");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("reports a bounded tree as PARTIAL and names the bound that stopped it")
    void shouldReportATruncatedTreeAsPartialWithItsLimitation() {
        AgentJob job = job(17L, "0123456789012345678901234567890123456789");
        when(gitRepositoryManager.isEnabled()).thenReturn(true);
        when(gitRepositoryManager.isRepositoryCloned(17L)).thenReturn(true);
        when(gitRepositoryManager.readTreeSnapshot(17L, "0123456789012345678901234567890123456789"))
                .thenReturn(new GitRepositoryManager.GitTreeSnapshot(
                        stagingDir,
                        "0123456789012345678901234567890123456789",
                        "1123456789012345678901234567890123456789",
                        Map.of("src/App.java", stagingDir.resolve("src/App.java")),
                        12,
                        40_000,
                        false,
                        Set.of(
                                GitRepositoryManager.TREE_LIMITATION_FILE_COUNT,
                                GitRepositoryManager.TREE_LIMITATION_FILE_TOO_LARGE)));

        var contribution = source.capture(new ContextRequest.PracticeReviewRequest(job), source.sourceKinds());

        // COMPLETE here would license a practice to say "this does not exist anywhere in the
        // repository" about a tree whose walk we cut short.
        assertThat(contribution.completeness())
                .containsEntry(
                        new SourceKind("scm.repository.tree"),
                        de.tum.cit.aet.hephaestus.evidence.SourceCompleteness.PARTIAL);
        assertThat(contribution.captureLimitations().get(new SourceKind("scm.repository.tree")))
                .containsExactlyInAnyOrder(
                        GitRepositoryManager.TREE_LIMITATION_FILE_COUNT,
                        GitRepositoryManager.TREE_LIMITATION_FILE_TOO_LARGE);
    }

    @Test
    void shouldRejectUnpinnedTree() {
        when(gitRepositoryManager.isEnabled()).thenReturn(true);
        when(gitRepositoryManager.isRepositoryCloned(17L)).thenReturn(true);
        assertThatThrownBy(() ->
                        source.capture(new ContextRequest.PracticeReviewRequest(job(17L, null)), source.sourceKinds()))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("commit_sha");
    }

    @Test
    void shouldReportOperationalGitFailureAsCollectionError() {
        AgentJob job = job(17L, "0123456789012345678901234567890123456789");
        when(gitRepositoryManager.isEnabled()).thenReturn(true);
        when(gitRepositoryManager.isRepositoryCloned(17L)).thenReturn(true);
        when(gitRepositoryManager.readTreeSnapshot(17L, "0123456789012345678901234567890123456789"))
                .thenThrow(
                        new GitRepositoryManager.GitOperationException("unreadable commit", new java.io.IOException()));

        assertThatThrownBy(() -> source.capture(new ContextRequest.PracticeReviewRequest(job), source.sourceKinds()))
                .isInstanceOf(EvidenceCollectionException.class)
                .hasMessageContaining("Could not capture repository tree");
    }

    @Test
    void shouldReportDisabledCheckoutAsNotCollectedRatherThanFailing() {
        AgentJob job = job(17L, "0123456789012345678901234567890123456789");
        when(gitRepositoryManager.isEnabled()).thenReturn(false);

        var contribution = source.capture(new ContextRequest.PracticeReviewRequest(job), source.sourceKinds());

        assertThat(contribution.files()).isEmpty();
        assertThat(contribution.stateOverrides())
                .containsExactly(entry(
                        new SourceKind("scm.repository.tree"),
                        new SourceCaptureState.NotCollected(SourceAbsenceReason.DISABLED)));
    }

    @Test
    void shouldReportAnUnclonedRepositoryAsUnavailableRatherThanFailing() {
        AgentJob job = job(17L, "0123456789012345678901234567890123456789");
        when(gitRepositoryManager.isEnabled()).thenReturn(true);
        when(gitRepositoryManager.isRepositoryCloned(17L)).thenReturn(false);

        var contribution = source.capture(new ContextRequest.PracticeReviewRequest(job), source.sourceKinds());

        assertThat(contribution.files()).isEmpty();
        assertThat(contribution.stateOverrides())
                .containsExactly(entry(
                        new SourceKind("scm.repository.tree"),
                        new SourceCaptureState.Unavailable(SourceAbsenceReason.NO_WORKING_COPY)));
    }

    private static AgentJob job(long repositoryId, @Nullable String commitSha) {
        JsonMapper mapper = JsonMapper.builder().build();
        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("repository_id", repositoryId);
        if (commitSha != null) {
            metadata.put("commit_sha", commitSha);
        }
        AgentJob job = new AgentJob();
        job.setMetadata(metadata);
        return job;
    }
}
