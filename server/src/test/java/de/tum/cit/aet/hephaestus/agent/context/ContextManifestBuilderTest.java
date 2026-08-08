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
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReason;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceState;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceReadinessCheck;
import de.tum.cit.aet.hephaestus.evidence.SourceReadinessReason;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import de.tum.cit.aet.hephaestus.practices.EvidenceStance;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReview;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewMode;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewPolicy;
import de.tum.cit.aet.hephaestus.practices.PracticeBinding;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceLimitation;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceRequirement;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceSufficiency;
import de.tum.cit.aet.hephaestus.practices.PracticeInsufficientEvidenceAction;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
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
        assertThat(diffSource.path("state").path("availability").asString()).isEqualTo("AVAILABLE");
        assertThat(diffSource.path("artifacts").get(0).path("path").asString()).isEqualTo("inputs/context/diff.patch");
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
        assertThat(diff.path("state").path("availability").asString()).isEqualTo("NOT_COLLECTED");
        assertThat(diff.has("paths")).isFalse();
        JsonNode comments = findSource(visible, COMMENTS.value());
        assertThat(comments.path("state").path("content").asString()).isEqualTo("EMPTY");
        assertThat(comments.path("state").path("completeness").asString()).isEqualTo("COMPLETE");
    }

    @Test
    void shouldAuthorizeCaptureForTheDetectionAudience() {
        ArtifactSourceCatalogRegistry catalogs = mock(ArtifactSourceCatalogRegistry.class);
        ContextManifestBuilder target = new ContextManifestBuilder(cas, layout, mapper, catalogs, Clock.systemUTC());
        SourceContractVersion version = new SourceContractVersion("1.0.0");

        target.isSourceUsePermitted(version, DIFF);

        verify(catalogs).isSourceUsePermitted(version, DIFF, SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW);
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
                Map.of(COMMENTS, new SourceCaptureState.Redacted(SourceAbsenceReason.PRIVACY_POLICY)),
                Set.of(COMMENTS)
            )
        );

        JsonNode source = findSource(mapper.readTree(files.get("inputs/manifest.json")), COMMENTS.value());
        assertThat(source.path("state").path("availability").asString()).isEqualTo("REDACTED");
        assertThat(source.has("paths")).isFalse();
    }

    @Test
    void shouldReviewAMirroredRecordWhoseCurrentnessCannotBeEstablished() {
        // A pull request unchanged for months is correctly mirrored, not stale. Reading the mirror's
        // last-written timestamp as a last-verified one would refuse exactly this case, which covers
        // every backfilled review and every established repository.
        var manifest = coreManifest(builder, "job-quiet", Instant.EPOCH);

        assertThat(
            builder
                .checkAutomatedReviewReadinessAsOfNow(manifest, List.of(practiceRequiring(CORE, "pr-core")))
                .readyPractices()
        ).hasSize(1);
    }

    @Test
    void shouldDeclineWhenASourceThatMustHoldSomethingCapturedNothing() {
        // A pull request really can produce an empty diff — a full revert, a force-push, a
        // merge-commit-only range. The contract allows it, so availability and completeness both pass.
        // Only the diff's declared COMPLETE_AND_NON_EMPTY separates "there is nothing here to judge"
        // from "I looked and it was fine", and it applies to every practice requiring the diff, not
        // only to the ones that thought to ask for it.
        Map<String, byte[]> files = new LinkedHashMap<>();
        String path = "inputs/context/diff.patch";
        files.put(path, new byte[0]);
        ArtifactSourceManifest manifest = builder.augment(
            files,
            Map.of(path, DIFF),
            "job-empty-diff",
            plan(Set.of(DIFF)),
            new ContextManifestBuilder.CaptureMetadata(
                Map.of(DIFF, SourceCompleteness.COMPLETE),
                Map.of(DIFF, SourceContentState.EMPTY),
                Map.of(DIFF, "abc123"),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(DIFF)
            )
        );

        AutomatedReviewReadinessResult refused = builder.checkAutomatedReviewReadinessAsOfNow(
            manifest,
            List.of(practiceRequiring(DIFF, "needs-substance"))
        );

        assertThat(refused.readyPractices()).isEmpty();
        assertThat(refused.decisions().getFirst().sourceChecks().getFirst().reasonCodes()).contains(
            SourceReadinessReason.SOURCE_EMPTY
        );
    }

    /**
     * The same empty capture of a source whose contract asks nothing of it is reviewable: emptiness is
     * the answer there, not a gap in it.
     */
    @Test
    void shouldReviewAnEmptyCaptureOfASourceThatMayBeEmpty() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        String path = "inputs/context/comments.json";
        files.put(path, "[]".getBytes(StandardCharsets.UTF_8));
        ArtifactSourceManifest manifest = builder.augment(
            files,
            Map.of(path, COMMENTS),
            "job-empty-comments",
            plan(Set.of(COMMENTS)),
            new ContextManifestBuilder.CaptureMetadata(
                Map.of(COMMENTS, SourceCompleteness.COMPLETE),
                Map.of(COMMENTS, SourceContentState.EMPTY),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(COMMENTS)
            )
        );

        assertThat(
            builder
                .checkAutomatedReviewReadinessAsOfNow(manifest, List.of(practiceRequiringComments()))
                .readyPractices()
        ).hasSize(1);
    }

    /**
     * A partial capture of a source the contract is content to take partially still refuses the one
     * practice whose claim is that something is not in it — a fragment that does not contain the
     * resolution is equally consistent with the resolution being in the part nobody fetched.
     */
    @Test
    void shouldRefuseAnAbsenceClaimOnAPartialCapture() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        String path = "inputs/context/comments.json";
        files.put(path, "[{}]".getBytes(StandardCharsets.UTF_8));
        ArtifactSourceManifest manifest = builder.augment(
            files,
            Map.of(path, COMMENTS),
            "job-partial-comments",
            plan(Set.of(COMMENTS)),
            new ContextManifestBuilder.CaptureMetadata(
                Map.of(COMMENTS, SourceCompleteness.PARTIAL),
                Map.of(COMMENTS, SourceContentState.NON_EMPTY),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(COMMENTS)
            )
        );

        // The same partial capture, read by a practice that only reads what is in front of it.
        assertThat(
            builder
                .checkAutomatedReviewReadinessAsOfNow(manifest, List.of(practiceRequiringComments()))
                .readyPractices()
        ).hasSize(1);

        AutomatedReviewReadinessResult refused = builder.checkAutomatedReviewReadinessAsOfNow(
            manifest,
            List.of(practiceRequiring(COMMENTS, "asserts-an-absence", EvidenceStance.EXHAUSTIVE))
        );

        assertThat(refused.readyPractices()).isEmpty();
        assertThat(refused.decisions().getFirst().sourceChecks().getFirst().reasonCodes()).contains(
            SourceReadinessReason.SOURCE_INCOMPLETE
        );
    }

    @Test
    void shouldSkipAutomatedReviewsThatCannotRun() {
        ArtifactSourceManifest manifest = coreManifest(builder, "job-unsupported-assessment", NOW);
        List<PracticeAutomatedReview> configurations = List.of(
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT
            ),
            new PracticeAutomatedReview(PracticeAutomatedReviewMode.NONE, PracticeEvidenceSufficiency.NONE)
        );
        List<AutomatedReviewReadinessReason> expectedReasons = List.of(
            AutomatedReviewReadinessReason.DECLARED_EVIDENCE_INSUFFICIENT,
            AutomatedReviewReadinessReason.NO_AUTOMATED_REVIEW
        );

        for (int index = 0; index < configurations.size(); index++) {
            Practice practice = practiceRequiring(CORE, "unsupported-assessment-" + index);
            boolean assessmentAbsent = configurations.get(index).mode() == PracticeAutomatedReviewMode.NONE;
            boolean needsAdditionalContext =
                configurations.get(index).evidenceSufficiency() ==
                PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT;
            practice.setAutomatedReviewPolicy(
                new PracticeAutomatedReviewPolicy(
                    practice.getAutomatedReviewPolicy().sourceContractVersion(),
                    configurations.get(index),
                    practice.getAutomatedReviewPolicy().whenEvidenceIsInsufficient(),
                    assessmentAbsent
                        ? List.of()
                        : needsAdditionalContext
                            ? List.of(
                                  new PracticeEvidenceLimitation(
                                      "ADDITIONAL_CONTEXT_NEEDED",
                                      "The available sources do not contain the context required for this assessment."
                                  )
                              )
                            : practice.getAutomatedReviewPolicy().knownLimitations(),
                    needsAdditionalContext
                        ? new PracticeEvidenceLimitation(
                              "ADDITIONAL_CONTEXT_NEEDED",
                              "The available sources do not contain the context required for this assessment."
                          )
                        : null
                )
            );
            if (assessmentAbsent) {
                // A practice nobody automates reads nothing, so its bindings carry no evidence — the
                // shape PracticeService leaves behind when automated review is switched off.
                practice.setBindings(
                    practice
                        .getBindings()
                        .stream()
                        .map(binding -> new PracticeBinding(binding.signals(), List.of(), binding.onDrafts()))
                        .toList()
                );
            }

            AutomatedReviewReadinessResult result = builder.checkAutomatedReviewReadinessAsOfNow(
                manifest,
                List.of(practice)
            );
            assertThat(result.readyPractices()).isEmpty();
            assertThat(result.decisions().getFirst().reasonCodes()).containsExactly(expectedReasons.get(index));
            if (assessmentAbsent) {
                assertThat(result.decisions().getFirst().sourceChecks()).isEmpty();
            } else {
                assertThat(result.decisions().getFirst().sourceChecks()).allMatch(
                    SourceReadinessCheck::meetsRequirements
                );
            }
        }
    }

    @Test
    void shouldReviewWorkUnchangedUpstreamSinceTheLastSynchronization() {
        // A mirrored record upstream has not touched is current, however old the last write is.
        ArtifactSourceManifest manifest = coreManifest(builder, "job-quiet-mirror", NOW.minusSeconds(14 * 86_400));

        assertThat(
            builder
                .checkAutomatedReviewReadiness(manifest, List.of(practiceRequiring(CORE, "pr-core")), NOW, null)
                .readyPractices()
        ).hasSize(1);
    }

    @Test
    void shouldProduceTheSameReadinessResultWhenReplayed() {
        ArtifactSourceManifest manifest = coreManifest(builder, "job-replay", NOW);
        List<Practice> practices = List.of(practiceRequiring(CORE, "pr-core"));

        var original = builder.checkAutomatedReviewReadiness(manifest, practices, NOW, null);
        // Re-evaluated much later against the recorded evidence and the recorded anchor. Readiness is
        // a pure function of those inputs, so the result must be identical.
        var replayed = builderAt(NOW.plusSeconds(90 * 86_400)).checkAutomatedReviewReadiness(
            manifest,
            practices,
            NOW,
            null
        );

        assertThat(replayed.readyPractices()).hasSameElementsAs(original.readyPractices());
        assertThat(replayed.decisions().getFirst().ready()).isEqualTo(original.decisions().getFirst().ready());
        assertThat(replayed.decisions().getFirst().reasonCodes()).isEqualTo(
            original.decisions().getFirst().reasonCodes()
        );
    }

    @Test
    void shouldPersistEvidenceRefusalsAsTypedDecisions() {
        ArtifactSourceManifest manifest = coreManifest(builder, "job-refused", NOW);

        assertThat(
            builder
                .prepareAutomatedReviewReadiness(
                    manifest,
                    // The manifest holds the pull-request record but not its comments, so this
                    // practice's required source is demonstrably absent.
                    List.of(practiceRequiringComments()),
                    "job-refused",
                    NOW,
                    null
                )
                .readyPractices()
        ).isEmpty();
        JsonNode report = mapper.readTree(
            layout.jobDir("job-refused").resolve("automated-review-readiness-report.json").toFile()
        );
        JsonNode decision = report.path("decisions").get(0);
        assertThat(decision.path("ready").asBoolean()).isFalse();
        assertThat(decision.path("sourceChecks").get(0).path("reasonCodes").get(0).asString()).isEqualTo(
            "SOURCE_NOT_AVAILABLE"
        );
    }

    @Test
    void shouldAcceptCompleteCurrentEmptyEvidence() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        var manifest = builder.augment(files, Map.of(), "job-empty", plan(Set.of(COMMENTS)), metadata(COMMENTS, NOW));
        Practice practice = practiceRequiringComments();

        assertThat(
            builder.checkAutomatedReviewReadinessAsOfNow(manifest, List.of(practice)).readyPractices()
        ).containsExactly(practice);
    }

    @Test
    void shouldRejectEmptyContentWhenTheSourceContractForbidsIt() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        builder.augment(files, Map.of(), "job-invalid-empty", plan(Set.of(CORE)), metadata(CORE, NOW));

        JsonNode core = findSource(mapper.readTree(files.get("inputs/manifest.json")), CORE.value());
        assertThat(core.path("state").path("availability").asString()).isEqualTo("UNAVAILABLE");
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
            manifest.artifactKind(),
            manifest.capturedAt(),
            manifest.sources()
        );

        assertThatThrownBy(() ->
            builder.checkAutomatedReviewReadinessAsOfNow(changedContract, List.of(practiceRequiringComments()))
        )
            .isInstanceOf(UnreplayableEvidenceException.class)
            .hasMessageContaining("no longer ships");
    }

    @Test
    void shouldReviewAConversationWithoutAFreshnessWatermark() {
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

        assertThat(
            builder.checkAutomatedReviewReadinessAsOfNow(manifest, List.of(practice)).readyPractices()
        ).containsExactly(practice);
    }

    @Test
    void shouldAcceptMirrorWatermarkThatCoversTheRequestedSnapshot() {
        ContextManifestBuilder laterBuilder = builderAt(NOW.plusSeconds(60));
        ArtifactSourceManifest manifest = coreManifest(laterBuilder, "job-future-watermark", NOW.plusSeconds(60));

        Practice practice = practiceRequiring(CORE, "pr-core");
        assertThat(
            laterBuilder
                .prepareAutomatedReviewReadiness(manifest, List.of(practice), "job-future-watermark", NOW, null)
                .readyPractices()
        ).containsExactly(practice);
    }

    @Test
    void shouldTreatAnIncoherentWatermarkAsUnknownRatherThanStale() {
        ArtifactSourceManifest manifest = coreManifest(builder, "job-invalid-watermark", NOW.plusSeconds(60));

        // Nothing here can show the copy is behind: the mirror records when a row last changed, not
        // when it was last checked. Refusing on that is refusing for a reason we cannot establish.
        assertThat(
            builder
                .checkAutomatedReviewReadinessAsOfNow(manifest, List.of(practiceRequiring(CORE, "pr-core")))
                .readyPractices()
        ).hasSize(1);
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
            delayedBuilder
                .prepareAutomatedReviewReadiness(manifest, List.of(practice), "job-delayed", NOW, null)
                .readyPractices()
        ).containsExactly(practice);
        JsonNode sourceCheck = mapper
            .readTree(layout.jobDir("job-delayed").resolve("automated-review-readiness-report.json").toFile())
            .path("decisions")
            .get(0)
            .path("sourceChecks")
            .get(0);
        assertThat(sourceCheck.path("checkedAt").asString()).isEqualTo(NOW.plusSeconds(3_600).toString());
        assertThat(sourceCheck.path("temporalAnchor").asString()).isEqualTo(NOW.toString());
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
        assertThat(source.path("state").path("completeness").asString()).isEqualTo("PARTIAL");
    }

    @Test
    void shouldRejectSourcesThatDoNotApplyToTheReviewedKind() {
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
            .hasMessageContaining("do not apply to scm.pull_request");
    }

    @Test
    void shouldRejectAbsenceStateForbiddenByTheSourceContract() {
        var realCatalogs = new ClasspathArtifactSourceCatalogRegistry(mapper, Clock.systemUTC());
        ArtifactSourceContract diff = realCatalogs.requireSource(plan(Set.of(DIFF)).contractVersion(), DIFF);
        ArtifactSourceContract restrictedDiff = new ArtifactSourceContract(
            diff.kind(),
            diff.displayName(),
            diff.description(),
            diff.selectionScope(),
            diff.artifactKinds(),
            diff.isDefaultRequirement(),
            diff.authority(),
            diff.identityPolicy(),
            diff.completenessPolicy(),
            diff.requiredQuality(),
            diff.privacyClass(),
            Set.of(SourceAbsenceState.UNAVAILABLE),
            diff.retentionPolicy(),
            diff.erasurePolicy(),
            diff.useDecisionIds()
        );
        ArtifactSourceCatalogRegistry catalogs = mock(ArtifactSourceCatalogRegistry.class);
        when(catalogs.requireSourcesFor(any(), any())).thenAnswer(invocation ->
            realCatalogs.requireSourcesFor(invocation.getArgument(0), invocation.getArgument(1))
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
                "job-unsupported-absence-state",
                plan(Set.of(COMMENTS)),
                metadata(COMMENTS, NOW)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not support absence state NOT_COLLECTED");
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
            builder.checkAutomatedReviewReadinessAsOfNow(
                manifest,
                List.of(practiceRequiring(CONVERSATION, "conversation"))
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match manifest");
    }

    @Test
    void shouldRejectManifestThatOmitsAnApplicableSource() {
        ArtifactSourceManifest manifest = coreManifest(builder, "job-incomplete-sources", NOW);
        ArtifactSourceManifest incomplete = new ArtifactSourceManifest(
            manifest.contractVersion(),
            manifest.catalogDigest(),
            manifest.artifactKind(),
            manifest.capturedAt(),
            manifest
                .sources()
                .stream()
                .filter(source -> !source.kind().equals(COMMENTS))
                .toList()
        );

        assertThatThrownBy(() ->
            builder.checkAutomatedReviewReadinessAsOfNow(incomplete, List.of(practiceRequiring(CORE, "pr-core")))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("do not match the sources its artifact kind applies to");
    }

    @Test
    void shouldPreserveExplicitAbsenceWhenMinimizingPreparedEvidence() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        ArtifactSourceManifest manifest = builder.augment(
            files,
            Map.of(),
            "job-minimized",
            plan(Set.of(CORE, COMMENTS)),
            new ContextManifestBuilder.CaptureMetadata(
                Map.of(CORE, SourceCompleteness.COMPLETE, COMMENTS, SourceCompleteness.COMPLETE),
                Map.of(CORE, "commit:core"),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(CORE, COMMENTS)
            )
        );

        PreparedEvidence restricted = builder.restrictTo(new PreparedEvidence(files, manifest), plan(Set.of(CORE)));

        assertThat(restricted.manifest().sources()).hasSameSizeAs(manifest.sources());
        assertThat(
            restricted
                .manifest()
                .sources()
                .stream()
                .filter(source -> source.kind().equals(COMMENTS))
                .findFirst()
                .orElseThrow()
                .state()
        ).isEqualTo(new SourceCaptureState.NotCollected(SourceAbsenceReason.MINIMIZED));
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
        assertThat(source.path("state").path("availability").asString()).isEqualTo("AVAILABLE");
        assertThat(source.path("state").path("content").asString()).isEqualTo("EMPTY");
        assertThat(source.path("state").path("completeness").asString()).isEqualTo("COMPLETE");
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
        assertThat(source.path("state").path("availability").asString()).isEqualTo("AVAILABLE");
        assertThat(source.path("state").path("content").asString()).isEqualTo("EMPTY");
        assertThat(source.path("state").path("completeness").asString()).isEqualTo("PARTIAL");
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
        return new EvidencePlan(new SourceContractVersion("1.0.0"), ArtifactKinds.PULL_REQUEST, sources);
    }

    private ContextManifestBuilder builderAt(Instant instant) {
        return new ContextManifestBuilder(
            cas,
            layout,
            mapper,
            new ClasspathArtifactSourceCatalogRegistry(mapper, Clock.systemUTC()),
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
            ArtifactKinds.CONVERSATION_THREAD,
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
        return practiceRequiring(COMMENTS, "review-comments");
    }

    private static Practice practiceRequiring(SourceKind sourceKind, String slug) {
        return practiceRequiring(sourceKind, slug, EvidenceStance.REQUIRED);
    }

    private static Practice practiceRequiring(SourceKind sourceKind, String slug, EvidenceStance stance) {
        boolean conversation = sourceKind.equals(CONVERSATION);
        Practice practice = new Practice();
        practice.setSlug(slug);
        practice.setBindings(
            List.of(
                PracticeBinding.on(
                    PracticeTestEvidence.defaultSignal(
                        conversation ? ArtifactKinds.CONVERSATION_THREAD : ArtifactKinds.PULL_REQUEST
                    ),
                    List.of(new PracticeEvidenceRequirement(sourceKind, stance))
                )
            )
        );
        practice.setAutomatedReviewPolicy(
            new PracticeAutomatedReviewPolicy(
                new SourceContractVersion("1.0.0"),
                new PracticeAutomatedReview(
                    PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                    PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
                ),
                PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
                List.of(),
                null
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
