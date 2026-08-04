package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceContract;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceManifest;
import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.MissingnessKind;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUseAudience;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import de.tum.cit.aet.hephaestus.practices.EvidenceCompletenessRequirement;
import de.tum.cit.aet.hephaestus.practices.EvidenceFreshnessRequirement;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceDeclaration;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceRefusal;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceRequirement;
import de.tum.cit.aet.hephaestus.practices.PracticeObservability;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ContextManifestBuilderTest extends BaseUnitTest {

    private static final SourceKind DIFF = new SourceKind("scm.pull-request.diff");
    private static final SourceKind CORE = new SourceKind("scm.pull-request.core");
    private static final SourceKind COMMENTS = new SourceKind("scm.pull-request.comments");
    private static final SourceKind CONVERSATION = new SourceKind("slack.conversation.thread");
    private static final SourceKind LINKED_ITEMS = new SourceKind("scm.linked-work-items");
    private static final SourceKind REPOSITORY_TREE = new SourceKind("scm.repository.tree");
    private static final SourceKind OUTLINE = new SourceKind("outline.documents");
    private static final Instant NOW = Instant.parse("2026-08-03T10:00:00Z");

    @TempDir
    Path root;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private FabricLayout layout;
    private ContentAddressedStore cas;
    private ContextManifestBuilder builder;

    @BeforeEach
    void setUp() {
        layout = new FabricLayout(root.toString());
        cas = new ContentAddressedStore(layout);
        builder = builderAt(NOW);
    }

    @Test
    void shouldExposeCitationDigestsWithoutInternalMetadata() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        byte[] diff = "diff --git a b".getBytes(StandardCharsets.UTF_8);
        files.put("inputs/context/diff.patch", diff);
        EvidencePlan plan = plan(Set.of(DIFF));

        builder.augment(
            files,
            Map.of("inputs/context/diff.patch", DIFF),
            "job-42",
            plan,
            new ContextManifestBuilder.CaptureMetadata(
                Map.of(DIFF, SourceCompleteness.COMPLETE),
                Map.of(DIFF, "abc123"),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(DIFF)
            )
        );

        JsonNode visible = mapper.readTree(files.get("inputs/manifest.json"));
        assertThat(visible.path("contractVersion").asString()).isEqualTo("1.0.0");
        assertThat(visible.toString()).doesNotContain("job-42").doesNotContain("workspaceId");
        JsonNode diffSource = findSource(visible, DIFF.value());
        assertThat(diffSource.path("availability").asString()).isEqualTo("AVAILABLE");
        assertThat(diffSource.path("paths").get(0).asString()).isEqualTo("inputs/context/diff.patch");
        assertThat(diffSource.path("artifacts").get(0).path("sha256").asString()).matches("[0-9a-f]{64}");

        Path internalPath = layout.jobDir("job-42").resolve("artifact-source-manifest.json");
        assertThat(internalPath).exists();
        JsonNode internal = mapper.readTree(internalPath.toFile());
        String sha = findSource(internal, DIFF.value()).path("artifacts").get(0).path("sha256").asString();
        assertThat(cas.get(sha)).contains(diff);
    }

    @Test
    void shouldRepresentMinimizedSourcesAsNotCollected() {
        Map<String, byte[]> files = new LinkedHashMap<>();

        builder.augment(files, Map.of(), "job-7", plan(Set.of(COMMENTS)), metadata(COMMENTS, NOW));

        JsonNode visible = mapper.readTree(files.get("inputs/manifest.json"));
        JsonNode diff = findSource(visible, DIFF.value());
        assertThat(diff.path("availability").asString()).isEqualTo("NOT_COLLECTED");
        assertThat(diff.has("paths")).isFalse();
        JsonNode comments = findSource(visible, COMMENTS.value());
        assertThat(comments.path("content").asString()).isEqualTo("EMPTY");
        assertThat(comments.path("completeness").asString()).isEqualTo("COMPLETE");
    }

    @Test
    void shouldAuthorizeCaptureForTheDetectionAudience() {
        ArtifactSourceCatalogRegistry catalogs = mock(ArtifactSourceCatalogRegistry.class);
        ContextManifestBuilder target = new ContextManifestBuilder(cas, layout, mapper, catalogs, Clock.systemUTC());
        SourceContractVersion version = new SourceContractVersion("1.0.0");

        target.isSourceUsePermitted(version, DIFF);

        verify(catalogs).isSourceUsePermitted(version, DIFF, SourceUseAudience.PRACTICE_DETECTION);
    }

    @Test
    void shouldRepresentAWhollyWithheldSourceAsRedactedWithoutLeakingArtifacts() {
        Map<String, byte[]> files = new LinkedHashMap<>();

        builder.augment(
            files,
            Map.of(),
            "job-redacted",
            plan(Set.of(COMMENTS)),
            new ContextManifestBuilder.CaptureMetadata(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(COMMENTS, new SourceCaptureState.Redacted("PRIVACY_POLICY")),
                Set.of(COMMENTS)
            )
        );

        JsonNode source = findSource(mapper.readTree(files.get("inputs/manifest.json")), COMMENTS.value());
        assertThat(source.path("availability").asString()).isEqualTo("REDACTED");
        assertThat(source.has("paths")).isFalse();
    }

    @Test
    void shouldDeclineWhenRequiredEvidenceIsStale() {
        var manifest = coreManifest(builder, "job-stale", Instant.EPOCH);

        assertThat(
            builder.assessPractices(manifest, List.of(practiceRequiring(CORE, "pr-core"))).readyPractices()
        ).isEmpty();
    }

    @Test
    void shouldReassessFreshnessAtReplayTime() {
        ArtifactSourceManifest manifest = coreManifest(builder, "job-replay", NOW);

        ContextManifestBuilder replayBuilder = builderAt(NOW.plusSeconds(301));

        assertThat(
            replayBuilder.assessPractices(manifest, List.of(practiceRequiring(CORE, "pr-core"))).readyPractices()
        ).isEmpty();
    }

    @Test
    void shouldPersistEvidenceRefusalsAsTypedDecisions() {
        ArtifactSourceManifest manifest = coreManifest(builder, "job-refused", Instant.EPOCH);

        assertThat(
            builder
                .prepareReadiness(manifest, List.of(practiceRequiring(CORE, "pr-core")), "job-refused", NOW)
                .readyPractices()
        ).isEmpty();
        JsonNode report = mapper.readTree(
            layout.jobDir("job-refused").resolve("practice-readiness-report.json").toFile()
        );
        JsonNode decision = report.path("decisions").get(0);
        assertThat(decision.path("ready").asBoolean()).isFalse();
        assertThat(decision.path("assessments").get(0).path("reasonCodes").get(0).asString()).isEqualTo(
            "FRESHNESS_UNSATISFIED"
        );
    }

    @Test
    void shouldAcceptCompleteCurrentEmptyEvidence() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        var manifest = builder.augment(files, Map.of(), "job-empty", plan(Set.of(COMMENTS)), metadata(COMMENTS, NOW));
        Practice practice = practiceRequiringComments();

        assertThat(builder.assessPractices(manifest, List.of(practice)).readyPractices()).containsExactly(practice);
    }

    @Test
    void shouldRejectEmptyContentWhenTheSourceContractForbidsIt() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        builder.augment(files, Map.of(), "job-invalid-empty", plan(Set.of(CORE)), metadata(CORE, NOW));

        JsonNode core = findSource(mapper.readTree(files.get("inputs/manifest.json")), CORE.value());
        assertThat(core.path("availability").asString()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void shouldRejectAReplayAgainstDifferentContractBytes() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        ArtifactSourceManifest manifest = builder.augment(
            files,
            Map.of(),
            "job-old-contract",
            plan(Set.of(COMMENTS)),
            metadata(COMMENTS, NOW)
        );
        ArtifactSourceManifest changedContract = new ArtifactSourceManifest(
            manifest.contractVersion(),
            "0".repeat(64),
            manifest.profileId(),
            manifest.capturedAt(),
            manifest.sources(),
            manifest.viewTransformations()
        );

        assertThatThrownBy(() -> builder.assessPractices(changedContract, List.of(practiceRequiringComments())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exact source contract");
    }

    @Test
    void shouldAssessEventTimeWithoutRequiringAnObservedAtWatermark() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Instant eventTime = NOW.minusSeconds(1);
        ArtifactSourceManifest manifest = builder.augment(
            files,
            Map.of(),
            "job-event-time",
            conversationPlan(),
            new ContextManifestBuilder.CaptureMetadata(
                Map.of(CONVERSATION, SourceCompleteness.COMPLETE),
                Map.of(),
                Map.of(),
                Map.of(CONVERSATION, eventTime),
                Map.of(),
                Set.of(CONVERSATION)
            )
        );
        Practice practice = practiceRequiring(CONVERSATION, "conversation");

        assertThat(builder.assessPractices(manifest, List.of(practice)).readyPractices()).containsExactly(practice);
    }

    @Test
    void shouldAcceptMirrorWatermarkThatCoversTheRequestedSnapshot() {
        ContextManifestBuilder laterBuilder = builderAt(NOW.plusSeconds(60));
        ArtifactSourceManifest manifest = coreManifest(laterBuilder, "job-future-watermark", NOW.plusSeconds(60));

        Practice practice = practiceRequiring(CORE, "pr-core");
        assertThat(
            laterBuilder.prepareReadiness(manifest, List.of(practice), "job-future-watermark", NOW).readyPractices()
        ).containsExactly(practice);
    }

    @Test
    void shouldRejectMirrorWatermarkAfterCaptureTime() {
        ArtifactSourceManifest manifest = coreManifest(builder, "job-invalid-watermark", NOW.plusSeconds(60));

        assertThat(
            builder.assessPractices(manifest, List.of(practiceRequiring(CORE, "pr-core"))).readyPractices()
        ).isEmpty();
    }

    @Test
    void shouldAssessDelayedWorkAgainstSubmissionTime() {
        ContextManifestBuilder delayedBuilder = builderAt(NOW.plusSeconds(3_600));
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("inputs/context/metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
        ArtifactSourceManifest manifest = delayedBuilder.augment(
            files,
            Map.of("inputs/context/metadata.json", CORE),
            "job-delayed",
            plan(Set.of(CORE)),
            metadata(CORE, NOW)
        );
        Practice practice = practiceRequiring(CORE, "pr-core");

        assertThat(
            delayedBuilder.prepareReadiness(manifest, List.of(practice), "job-delayed", NOW).readyPractices()
        ).containsExactly(practice);
        JsonNode assessment = mapper
            .readTree(layout.jobDir("job-delayed").resolve("practice-readiness-report.json").toFile())
            .path("decisions")
            .get(0)
            .path("assessments")
            .get(0);
        assertThat(assessment.path("assessedAt").asString()).isEqualTo(NOW.plusSeconds(3_600).toString());
        assertThat(assessment.path("temporalAnchor").asString()).isEqualTo(NOW.toString());
    }

    @Test
    void shouldNotInferCompleteFromSourceCapabilityAlone() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("inputs/context/linked_work_items.json", "{\"workItems\":[{}]}".getBytes(StandardCharsets.UTF_8));
        ArtifactSourceManifest manifest = builder.augment(
            files,
            Map.of("inputs/context/linked_work_items.json", LINKED_ITEMS),
            "job-unreported-completeness",
            plan(Set.of(LINKED_ITEMS)),
            new ContextManifestBuilder.CaptureMetadata(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(LINKED_ITEMS)
            )
        );

        JsonNode source = findSource(mapper.readTree(files.get("inputs/manifest.json")), LINKED_ITEMS.value());
        assertThat(source.path("completeness").asString()).isEqualTo("PARTIAL");
    }

    @Test
    void shouldRejectSourcesOutsideTheSelectedProfile() {
        assertThatThrownBy(() ->
            builder.augment(
                new LinkedHashMap<>(),
                Map.of(),
                "job-invalid-plan",
                plan(Set.of(CONVERSATION)),
                metadata(CONVERSATION, NOW)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outside profile");
    }

    @Test
    void shouldRejectGeneratedMissingnessForbiddenByTheSourceContract() {
        var realCatalogs = new ClasspathArtifactSourceCatalogRegistry(mapper, Clock.systemUTC(), "");
        ArtifactSourceContract diff = realCatalogs.requireSource(plan(Set.of(DIFF)).contractVersion(), DIFF);
        ArtifactSourceContract restrictedDiff = new ArtifactSourceContract(
            diff.kind(),
            diff.description(),
            diff.selectionScope(),
            diff.artifactTypes(),
            diff.authority(),
            diff.captureTime(),
            diff.freshnessPolicy(),
            diff.completenessPolicy(),
            diff.privacyClass(),
            Set.of(MissingnessKind.UNAVAILABLE),
            diff.purpose(),
            diff.retentionPolicy(),
            diff.erasurePolicy(),
            diff.useDecisionIds()
        );
        ArtifactSourceCatalogRegistry catalogs = mock(ArtifactSourceCatalogRegistry.class);
        when(catalogs.requireProfile(any(), any())).thenAnswer(invocation ->
            realCatalogs.requireProfile(invocation.getArgument(0), invocation.getArgument(1))
        );
        when(catalogs.requireSource(any(), any())).thenAnswer(invocation -> {
            SourceKind kind = invocation.getArgument(1);
            return kind.equals(DIFF) ? restrictedDiff : realCatalogs.requireSource(invocation.getArgument(0), kind);
        });
        ContextManifestBuilder restrictedBuilder = new ContextManifestBuilder(
            cas,
            layout,
            mapper,
            catalogs,
            Clock.systemUTC()
        );

        assertThatThrownBy(() ->
            restrictedBuilder.augment(
                new LinkedHashMap<>(),
                Map.of(),
                "job-unsupported-missingness",
                plan(Set.of(COMMENTS)),
                metadata(COMMENTS, NOW)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not support missingness state NOT_COLLECTED");
    }

    @Test
    void shouldRejectPracticeEvidenceFromAnotherProfile() {
        ArtifactSourceManifest manifest = builder.augment(
            new LinkedHashMap<>(),
            Map.of(),
            "job-profile-mismatch",
            plan(Set.of(COMMENTS)),
            metadata(COMMENTS, NOW)
        );

        assertThatThrownBy(() ->
            builder.assessPractices(manifest, List.of(practiceRequiring(CONVERSATION, "conversation")))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match manifest");
    }

    @Test
    void shouldTreatAnEmptyRepositoryTreeAsValidCompleteEvidence() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        builder.augment(
            files,
            Map.of(),
            "job-empty-tree",
            plan(Set.of(REPOSITORY_TREE)),
            new ContextManifestBuilder.CaptureMetadata(
                Map.of(REPOSITORY_TREE, SourceCompleteness.COMPLETE),
                Map.of(REPOSITORY_TREE, "commit:tree"),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(REPOSITORY_TREE)
            )
        );

        JsonNode source = findSource(mapper.readTree(files.get("inputs/manifest.json")), REPOSITORY_TREE.value());
        assertThat(source.path("availability").asString()).isEqualTo("AVAILABLE");
        assertThat(source.path("content").asString()).isEqualTo("EMPTY");
        assertThat(source.path("completeness").asString()).isEqualTo("COMPLETE");
    }

    @Test
    void shouldTreatEmptyOutlineEvidenceAsPartial() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        builder.augment(
            files,
            Map.of(),
            "job-empty-outline",
            plan(Set.of(OUTLINE)),
            new ContextManifestBuilder.CaptureMetadata(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(OUTLINE)
            )
        );

        JsonNode source = findSource(mapper.readTree(files.get("inputs/manifest.json")), OUTLINE.value());
        assertThat(source.path("availability").asString()).isEqualTo("AVAILABLE");
        assertThat(source.path("content").asString()).isEqualTo("EMPTY");
        assertThat(source.path("completeness").asString()).isEqualTo("PARTIAL");
    }

    @Test
    void shouldRejectUndocumentedRuntimeSourceKinds() {
        EvidenceSource undocumented = new EvidenceSource() {
            private final SourceKind kind = new SourceKind("scm.undocumented");

            @Override
            public Set<SourceKind> sourceKinds() {
                return Set.of(kind);
            }

            @Override
            public SourceKind sourceKindFor(String path) {
                return kind;
            }

            @Override
            public boolean supports(ContextRequest request) {
                return true;
            }

            @Override
            public void contribute(ContextRequest request, Map<String, byte[]> files) {}
        };

        assertThatThrownBy(() -> builder.validateEvidenceSources(List.of(undocumented)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown source kind");
    }

    private static EvidencePlan plan(Set<SourceKind> sources) {
        return new EvidencePlan(
            new SourceContractVersion("1.0.0"),
            new EvidenceProfileId("pull-request-review"),
            sources
        );
    }

    private ContextManifestBuilder builderAt(Instant instant) {
        return new ContextManifestBuilder(
            cas,
            layout,
            mapper,
            new ClasspathArtifactSourceCatalogRegistry(mapper, Clock.systemUTC(), ""),
            Clock.fixed(instant, java.time.ZoneOffset.UTC)
        );
    }

    private ArtifactSourceManifest coreManifest(ContextManifestBuilder target, String jobId, Instant observedAt) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        String path = "inputs/context/metadata.json";
        files.put(path, "{}".getBytes(StandardCharsets.UTF_8));
        return target.augment(files, Map.of(path, CORE), jobId, plan(Set.of(CORE)), metadata(CORE, observedAt));
    }

    private static EvidencePlan conversationPlan() {
        return new EvidencePlan(
            new SourceContractVersion("1.0.0"),
            new EvidenceProfileId("conversation-review"),
            Set.of(CONVERSATION)
        );
    }

    private static ContextManifestBuilder.CaptureMetadata metadata(SourceKind kind, Instant observedAt) {
        return new ContextManifestBuilder.CaptureMetadata(
            Map.of(kind, SourceCompleteness.COMPLETE),
            Map.of(),
            Map.of(kind, observedAt),
            Map.of(),
            Map.of(),
            Set.of(kind)
        );
    }

    private static Practice practiceRequiringComments() {
        return practiceRequiring(COMMENTS, "review-comments", EvidenceFreshnessRequirement.ANY);
    }

    private static Practice practiceRequiring(SourceKind sourceKind, String slug) {
        return practiceRequiring(sourceKind, slug, EvidenceFreshnessRequirement.CURRENT);
    }

    private static Practice practiceRequiring(
        SourceKind sourceKind,
        String slug,
        EvidenceFreshnessRequirement freshness
    ) {
        Practice practice = new Practice();
        practice.setSlug(slug);
        practice.setEvidence(
            new PracticeEvidenceDeclaration(
                new SourceContractVersion("1.0.0"),
                sourceKind.equals(CONVERSATION)
                    ? new EvidenceProfileId("conversation-review")
                    : new EvidenceProfileId("pull-request-review"),
                PracticeObservability.SEMANTIC,
                List.of(
                    new PracticeEvidenceRequirement(sourceKind, EvidenceCompletenessRequirement.COMPLETE, freshness)
                ),
                List.of(),
                PracticeEvidenceRefusal.DECLINE_SEMANTIC_JUDGMENT,
                List.of()
            )
        );
        return practice;
    }

    private static JsonNode findSource(JsonNode root, String kind) {
        for (JsonNode source : root.path("sources")) {
            if (kind.equals(source.path("kind").asString())) return source;
        }
        throw new AssertionError("Missing source " + kind);
    }
}
