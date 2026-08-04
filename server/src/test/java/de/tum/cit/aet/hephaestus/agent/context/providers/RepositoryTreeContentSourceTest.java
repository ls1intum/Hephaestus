package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceCollectionException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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

    @Test
    void shouldMaterializePinnedTreeWithoutGitDirectory() {
        AgentJob job = job(17L, "0123456789012345678901234567890123456789");
        when(gitRepositoryManager.isRepositoryCloned(17L)).thenReturn(true);
        when(
            gitRepositoryManager.readTreeSnapshot(
                17L,
                "0123456789012345678901234567890123456789",
                RepositoryTreeContentSource.MAX_TOTAL_BYTES
            )
        ).thenReturn(
            new GitRepositoryManager.GitTreeSnapshot(
                "0123456789012345678901234567890123456789",
                "1123456789012345678901234567890123456789",
                Map.of("src/App.java", "class App {}".getBytes(StandardCharsets.UTF_8)),
                12,
                1,
                true,
                Set.of()
            )
        );

        Map<String, byte[]> files = new LinkedHashMap<>();
        source.contribute(new ContextRequest.PracticeReviewRequest(job), files);

        assertThat(files).containsOnlyKeys("inputs/sources/scm/repo/src/App.java");
        assertThat(files).doesNotContainKey("inputs/sources/scm/repo/.git/HEAD");
        verify(gitRepositoryManager).readTreeSnapshot(
            17L,
            "0123456789012345678901234567890123456789",
            RepositoryTreeContentSource.MAX_TOTAL_BYTES
        );
    }

    @Test
    void shouldRejectUnpinnedTree() {
        assertThatThrownBy(() ->
            source.contribute(new ContextRequest.PracticeReviewRequest(job(17L, null)), new LinkedHashMap<>())
        )
            .isInstanceOf(JobPreparationException.class)
            .hasMessageContaining("commit_sha");
    }

    @Test
    void shouldReportOperationalGitFailureAsCollectionError() {
        AgentJob job = job(17L, "0123456789012345678901234567890123456789");
        when(gitRepositoryManager.isRepositoryCloned(17L)).thenReturn(true);
        when(
            gitRepositoryManager.readTreeSnapshot(
                17L,
                "0123456789012345678901234567890123456789",
                RepositoryTreeContentSource.MAX_TOTAL_BYTES
            )
        ).thenThrow(new GitRepositoryManager.GitOperationException("unreadable commit", new java.io.IOException()));

        assertThatThrownBy(() -> source.capture(new ContextRequest.PracticeReviewRequest(job), source.sourceKinds()))
            .isInstanceOf(EvidenceCollectionException.class)
            .hasMessageContaining("Could not capture repository tree");
    }

    private static AgentJob job(long repositoryId, String commitSha) {
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
