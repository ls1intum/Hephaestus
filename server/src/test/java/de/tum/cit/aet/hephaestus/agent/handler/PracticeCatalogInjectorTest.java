package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * Locks the lifecycle phase-correctness gate in {@link PracticeCatalogInjector#inject}: a job carrying a
 * {@code trigger_event} materialises ONLY the practices whose {@code triggerEvents} include that event, so
 * an authoring practice is not re-litigated on a fixup push and a retrospective practice runs only at
 * close. The two safety behaviours — no-trigger keeps the full set, and a mis-seeded no-match falls THROUGH
 * to the full set rather than failing the job — are the load-bearing cases and are pinned here.
 */
@ExtendWith(MockitoExtension.class)
class PracticeCatalogInjectorTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private PracticeRepository practiceRepository;

    private PracticeCatalogInjector injector;

    @BeforeEach
    void setUp() {
        injector = new PracticeCatalogInjector(objectMapper, practiceRepository);
    }

    private Practice practice(String slug, String... triggerEvents) {
        Practice p = new Practice();
        p.setSlug(slug);
        p.setName(slug);
        p.setCriteria("criteria for " + slug);
        ArrayNode arr = objectMapper.createArrayNode();
        for (String e : triggerEvents) {
            arr.add(e);
        }
        p.setTriggerEvents(arr);
        return p;
    }

    private AgentJob job(String triggerEvent) {
        Workspace ws = new Workspace();
        ws.setId(1L);
        AgentJob j = new AgentJob();
        j.setWorkspace(ws);
        if (triggerEvent != null) {
            var meta = objectMapper.createObjectNode();
            meta.put("trigger_event", triggerEvent);
            j.setMetadata(meta);
        }
        return j;
    }

    private static String md(String slug) {
        return WorkspaceAbi.PRACTICES_PREFIX + slug + ".md";
    }

    @Test
    @DisplayName("a trigger event materialises only the practices that declare it")
    void filtersToTriggerMatchingPractices() {
        when(
            practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(1L, WorkArtifact.PULL_REQUEST)
        ).thenReturn(
            List.of(
                practice("authoring", "PullRequestCreated"),
                practice("retrospective", "PullRequestMerged"),
                practice("reviewer", "ReviewSubmitted")
            )
        );
        Map<String, byte[]> files = new HashMap<>();

        injector.inject(files, job("PullRequestMerged"), WorkArtifact.PULL_REQUEST);

        assertThat(files).containsKey(md("retrospective"));
        assertThat(files).doesNotContainKey(md("authoring"));
        assertThat(files).doesNotContainKey(md("reviewer"));
    }

    @Test
    @DisplayName("a job with no trigger event keeps the full focus set (legacy / bot-command path)")
    void noTriggerEventKeepsFullSet() {
        when(
            practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(1L, WorkArtifact.PULL_REQUEST)
        ).thenReturn(
            List.of(practice("authoring", "PullRequestCreated"), practice("retrospective", "PullRequestMerged"))
        );
        Map<String, byte[]> files = new HashMap<>();

        injector.inject(files, job(null), WorkArtifact.PULL_REQUEST);

        assertThat(files).containsKey(md("authoring"));
        assertThat(files).containsKey(md("retrospective"));
    }

    @Test
    @DisplayName("a trigger event that matches nothing falls THROUGH to the full set rather than failing the job")
    void noMatchFallsThroughToFullSet() {
        when(
            practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(1L, WorkArtifact.PULL_REQUEST)
        ).thenReturn(
            List.of(practice("authoring", "PullRequestCreated"), practice("retrospective", "PullRequestMerged"))
        );
        Map<String, byte[]> files = new HashMap<>();

        injector.inject(files, job("SomeEventNobodyDeclares"), WorkArtifact.PULL_REQUEST);

        assertThat(files).containsKey(md("authoring"));
        assertThat(files).containsKey(md("retrospective"));
    }
}
