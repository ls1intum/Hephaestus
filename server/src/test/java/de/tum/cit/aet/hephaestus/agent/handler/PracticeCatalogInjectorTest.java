package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return SandboxLayout.PRACTICES_PREFIX + slug + ".md";
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

    @Test
    @DisplayName("a slug that violates the ABI pattern is rejected before it can escape the practices/ prefix")
    void abiSlugViolationThrows() {
        // Defense-in-depth: slugs are interpolated into filesystem paths, so a mis-seeded slug with a path
        // traversal must be rejected, not written to disk.
        when(
            practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(1L, WorkArtifact.PULL_REQUEST)
        ).thenReturn(List.of(practice("../escape", "PullRequestCreated")));

        assertThatThrownBy(() -> injector.inject(new HashMap<>(), job(null), WorkArtifact.PULL_REQUEST))
            .isInstanceOf(JobPreparationException.class)
            .hasMessageContaining("ABI pattern");
    }

    @Test
    @DisplayName("a job with no workspace throws JobPreparationException")
    void noWorkspaceThrows() {
        AgentJob job = new AgentJob();

        assertThatThrownBy(() -> injector.inject(new HashMap<>(), job, WorkArtifact.PULL_REQUEST))
            .isInstanceOf(JobPreparationException.class)
            .hasMessageContaining("no workspace");
    }

    @Test
    @DisplayName("an empty active-practice set throws JobPreparationException")
    void emptyFocusSetThrows() {
        when(
            practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(1L, WorkArtifact.PULL_REQUEST)
        ).thenReturn(List.of());

        assertThatThrownBy(() -> injector.inject(new HashMap<>(), job(null), WorkArtifact.PULL_REQUEST))
            .isInstanceOf(JobPreparationException.class)
            .hasMessageContaining("No active");
    }

    @Test
    @DisplayName("inject writes index.json, the per-slug + bundled criteria, and skips blank precompute scripts")
    void injectWritesCatalogArtifactsAndSkipsBlankPrecompute() {
        Practice withScript = practice("authoring", "PullRequestCreated");
        withScript.setPrecomputeScript("export default () => ({});");
        Practice blankScript = practice("retrospective", "PullRequestMerged");
        blankScript.setPrecomputeScript("   "); // blank → no .ts written
        when(
            practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(1L, WorkArtifact.PULL_REQUEST)
        ).thenReturn(List.of(withScript, blankScript));
        Map<String, byte[]> files = new HashMap<>();

        injector.inject(files, job(null), WorkArtifact.PULL_REQUEST);

        // index.json lists both practices (area falls back to the slug when ungrouped).
        String index = new String(files.get(SandboxLayout.PRACTICES_PREFIX + "index.json"), StandardCharsets.UTF_8);
        assertThat(index).contains("authoring").contains("retrospective");
        // Per-slug criteria + the all-criteria bundle are present.
        assertThat(files).containsKey(md("authoring")).containsKey(md("retrospective"));
        String bundle = new String(
            files.get(SandboxLayout.PRACTICES_PREFIX + "all-criteria.md"),
            StandardCharsets.UTF_8
        );
        assertThat(bundle).contains("# authoring").contains("# retrospective");
        // Only the populated precompute script is written; the blank one is skipped.
        assertThat(files).containsKey(SandboxLayout.PRECOMPUTE_PREFIX + "practices/authoring.ts");
        assertThat(files).doesNotContainKey(SandboxLayout.PRECOMPUTE_PREFIX + "practices/retrospective.ts");
    }

    @Test
    @DisplayName("whyBySlug keeps populated principles and omits blank ones")
    void whyBySlugOmitsBlankPrinciples() {
        Practice withWhy = practice("authoring", "PullRequestCreated");
        withWhy.setWhyItMatters("Clear descriptions help reviewers.");
        Practice blankWhy = practice("retrospective", "PullRequestMerged");
        blankWhy.setWhyItMatters("   ");
        when(
            practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(1L, WorkArtifact.PULL_REQUEST)
        ).thenReturn(List.of(withWhy, blankWhy));

        Map<String, String> why = injector.whyBySlug(1L, WorkArtifact.PULL_REQUEST);

        assertThat(why)
            .containsEntry("authoring", "Clear descriptions help reviewers.")
            .doesNotContainKey("retrospective");
    }

    @Test
    @DisplayName("defectDetectorSlugs returns only practices whose criteria declare DEFECT-DETECTOR DISCIPLINE")
    void defectDetectorSlugsFiltersByMarker() {
        Practice detector = practice("authoring", "PullRequestCreated");
        detector.setCriteria("Some rule. DEFECT-DETECTOR DISCIPLINE applies here.");
        Practice ordinary = practice("retrospective", "PullRequestMerged"); // plain criteria, no marker
        when(
            practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(1L, WorkArtifact.PULL_REQUEST)
        ).thenReturn(List.of(detector, ordinary));

        Set<String> slugs = injector.defectDetectorSlugs(1L, WorkArtifact.PULL_REQUEST);

        assertThat(slugs).containsExactly("authoring");
    }
}
