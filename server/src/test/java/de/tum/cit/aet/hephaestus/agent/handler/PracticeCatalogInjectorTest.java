package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewPolicy;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceLimitation;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

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

    private Practice practice(String slug, SignalName... signals) {
        Practice p = new Practice();
        p.setSlug(slug);
        p.setName(slug);
        p.setCriteria("criteria for " + slug);
        p.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
        var revision = new PracticeRevision();
        ReflectionTestUtils.setField(revision, "id", Math.abs((long) slug.hashCode()) + 1);
        p.setCurrentRevision(revision);
        p.setBindings(PracticeTestEvidence.bindings(signals));
        return p;
    }

    private AgentJob job(@Nullable SignalName signal) {
        Workspace ws = new Workspace();
        ws.setId(1L);
        AgentJob j = new AgentJob();
        j.setWorkspace(ws);
        if (signal != null) {
            var meta = objectMapper.createObjectNode();
            meta.put("signal", signal.value());
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
            practiceRepository.findByWorkspaceIdAndReviewTierNotAndArtifactKind(
                1L,
                PracticeReviewTier.OFF,
                ArtifactKinds.PULL_REQUEST
            )
        ).thenReturn(
            List.of(
                practice("authoring", ScmSignals.PULL_REQUEST_OPENED),
                practice("retrospective", ScmSignals.PULL_REQUEST_MERGED),
                practice("reviewer", ScmSignals.PULL_REQUEST_REVIEWED)
            )
        );
        Map<String, byte[]> files = new HashMap<>();

        injector.inject(files, job(ScmSignals.PULL_REQUEST_MERGED), ArtifactKinds.PULL_REQUEST);

        assertThat(files).containsKey(md("retrospective"));
        assertThat(files).doesNotContainKey(md("authoring"));
        assertThat(files).doesNotContainKey(md("reviewer"));
    }

    @Test
    @DisplayName("a job with no trigger event keeps the full focus set (legacy / bot-command path)")
    void noTriggerEventKeepsFullSet() {
        when(
            practiceRepository.findByWorkspaceIdAndReviewTierNotAndArtifactKind(
                1L,
                PracticeReviewTier.OFF,
                ArtifactKinds.PULL_REQUEST
            )
        ).thenReturn(
            List.of(
                practice("authoring", ScmSignals.PULL_REQUEST_OPENED),
                practice("retrospective", ScmSignals.PULL_REQUEST_MERGED)
            )
        );
        Map<String, byte[]> files = new HashMap<>();

        injector.inject(files, job(null), ArtifactKinds.PULL_REQUEST);

        assertThat(files).containsKey(md("authoring"));
        assertThat(files).containsKey(md("retrospective"));
    }

    @Test
    @DisplayName("a trigger event that matches nothing fails closed")
    void noMatchFailsClosed() {
        when(
            practiceRepository.findByWorkspaceIdAndReviewTierNotAndArtifactKind(
                1L,
                PracticeReviewTier.OFF,
                ArtifactKinds.PULL_REQUEST
            )
        ).thenReturn(
            List.of(
                practice("authoring", ScmSignals.PULL_REQUEST_OPENED),
                practice("retrospective", ScmSignals.PULL_REQUEST_MERGED)
            )
        );
        Map<String, byte[]> files = new HashMap<>();

        assertThatThrownBy(() ->
            injector.inject(files, job(SignalName.of("scm.pull_request.rebased")), ArtifactKinds.PULL_REQUEST)
        )
            .isInstanceOf(JobPreparationException.class)
            .hasMessageContaining("No active scm.pull_request practices");
        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("a slug that violates the ABI pattern is rejected before it can escape the practices/ prefix")
    void abiSlugViolationThrows() {
        // Defense-in-depth: slugs are interpolated into filesystem paths, so a mis-seeded slug with a path
        // traversal must be rejected, not written to disk.
        when(
            practiceRepository.findByWorkspaceIdAndReviewTierNotAndArtifactKind(
                1L,
                PracticeReviewTier.OFF,
                ArtifactKinds.PULL_REQUEST
            )
        ).thenReturn(List.of(practice("../escape", ScmSignals.PULL_REQUEST_OPENED)));

        assertThatThrownBy(() -> injector.inject(new HashMap<>(), job(null), ArtifactKinds.PULL_REQUEST))
            .isInstanceOf(JobPreparationException.class)
            .hasMessageContaining("ABI pattern");
    }

    @Test
    @DisplayName("a job with no workspace throws JobPreparationException")
    void noWorkspaceThrows() {
        AgentJob job = new AgentJob();

        assertThatThrownBy(() -> injector.inject(new HashMap<>(), job, ArtifactKinds.PULL_REQUEST))
            .isInstanceOf(JobPreparationException.class)
            .hasMessageContaining("no workspace");
    }

    @Test
    @DisplayName("an empty active-practice set throws JobPreparationException")
    void emptyFocusSetThrows() {
        when(
            practiceRepository.findByWorkspaceIdAndReviewTierNotAndArtifactKind(
                1L,
                PracticeReviewTier.OFF,
                ArtifactKinds.PULL_REQUEST
            )
        ).thenReturn(List.of());

        assertThatThrownBy(() -> injector.inject(new HashMap<>(), job(null), ArtifactKinds.PULL_REQUEST))
            .isInstanceOf(JobPreparationException.class)
            .hasMessageContaining("No active");
    }

    @Test
    @DisplayName("inject writes index.json, the per-slug + bundled criteria, and skips blank precompute scripts")
    void injectWritesCatalogArtifactsAndSkipsBlankPrecompute() {
        Practice withScript = practice("authoring", ScmSignals.PULL_REQUEST_OPENED);
        withScript.setPrecomputeScript("export default () => ({});");
        Practice blankScript = practice("retrospective", ScmSignals.PULL_REQUEST_MERGED);
        blankScript.setPrecomputeScript("   "); // blank → no .ts written
        when(
            practiceRepository.findByWorkspaceIdAndReviewTierNotAndArtifactKind(
                1L,
                PracticeReviewTier.OFF,
                ArtifactKinds.PULL_REQUEST
            )
        ).thenReturn(List.of(withScript, blankScript));
        Map<String, byte[]> files = new HashMap<>();

        injector.inject(files, job(null), ArtifactKinds.PULL_REQUEST);

        // index.json lists both practices (area falls back to the slug when ungrouped).
        String index = new String(files.get(SandboxLayout.PRACTICES_PREFIX + "index.json"), StandardCharsets.UTF_8);
        assertThat(index).contains("authoring").contains("retrospective");
        // A pointer to where this practice's author expects the answer — not a fence. What may be cited
        // is what the run staged, and inputs/manifest.json is where that is stated, once.
        assertThat(index).contains("readsSources").contains("scm.pull-request.diff");
        assertThat(index).doesNotContain("allowedSources");
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
    @DisplayName("staged criteria tell the model which claims the evidence cannot support")
    void injectAppendsKnownLimitationsToCriteria() {
        Practice limited = practice("authoring", ScmSignals.PULL_REQUEST_OPENED);
        PracticeAutomatedReviewPolicy policy = limited.getAutomatedReviewPolicy();
        limited.setAutomatedReviewPolicy(
            new PracticeAutomatedReviewPolicy(
                policy.sourceContractVersion(),
                policy.automatedReview(),
                policy.whenEvidenceIsInsufficient(),
                List.of(
                    new PracticeEvidenceLimitation(
                        "RUNTIME_BEHAVIOR_NOT_OBSERVED",
                        "Repository evidence does not establish behavior in a deployed runtime."
                    )
                ),
                null
            )
        );
        Practice unlimited = practice("retrospective", ScmSignals.PULL_REQUEST_OPENED);
        when(
            practiceRepository.findByWorkspaceIdAndReviewTierNotAndArtifactKind(
                1L,
                PracticeReviewTier.OFF,
                ArtifactKinds.PULL_REQUEST
            )
        ).thenReturn(List.of(limited, unlimited));
        Map<String, byte[]> files = new HashMap<>();

        injector.inject(files, job(null), ArtifactKinds.PULL_REQUEST);

        String staged = new String(files.get(md("authoring")), StandardCharsets.UTF_8);
        assertThat(staged)
            .contains("criteria for authoring")
            .contains("## What this evidence cannot show")
            .contains("Repository evidence does not establish behavior in a deployed runtime.");
        assertThat(new String(files.get(md("retrospective")), StandardCharsets.UTF_8)).doesNotContain(
            "## What this evidence cannot show"
        );
    }

    @Test
    @DisplayName("whyBySlug keeps populated principles and omits blank ones")
    void whyBySlugOmitsBlankPrinciples() {
        Practice withWhy = practice("authoring", ScmSignals.PULL_REQUEST_OPENED);
        withWhy.setWhyItMatters("Clear descriptions help reviewers.");
        Practice blankWhy = practice("retrospective", ScmSignals.PULL_REQUEST_MERGED);
        blankWhy.setWhyItMatters("   ");
        when(
            practiceRepository.findByWorkspaceIdAndReviewTierNotAndArtifactKind(
                1L,
                PracticeReviewTier.OFF,
                ArtifactKinds.PULL_REQUEST
            )
        ).thenReturn(List.of(withWhy, blankWhy));

        Map<String, String> why = injector.whyBySlug(1L, ArtifactKinds.PULL_REQUEST);

        assertThat(why)
            .containsEntry("authoring", "Clear descriptions help reviewers.")
            .doesNotContainKey("retrospective");
    }

    @Test
    @DisplayName("defectDetectorSlugs uses the exact admitted practice snapshot")
    void defectDetectorSlugsUsesSnapshot() {
        AgentJob job = job(ScmSignals.PULL_REQUEST_OPENED);
        var snapshot = objectMapper.createObjectNode();
        var practices = snapshot.putArray("practices");
        practices.addObject().put("slug", "authoring").put("defectDetector", true);
        practices.addObject().put("slug", "retrospective").put("defectDetector", false);
        job.setEvidenceSnapshot(snapshot);

        Set<String> slugs = injector.defectDetectorSlugs(job);

        assertThat(slugs).containsExactly("authoring");
    }
}
