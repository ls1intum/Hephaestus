package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.AppliesWhenEvaluator;
import de.tum.cit.aet.hephaestus.agent.context.InsufficientEvidenceException;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewHistoryContentSource;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobAssignments;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReason;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReport;
import de.tum.cit.aet.hephaestus.evidence.CapturedSource;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCapture;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureFacts;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceReadinessReason;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import de.tum.cit.aet.hephaestus.integration.core.framework.ItemDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanItemVersions;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.ScmCapturedApplicability;
import de.tum.cit.aet.hephaestus.practices.AppliesWhen;
import de.tum.cit.aet.hephaestus.practices.AppliesWhenClause;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeReadSource;
import de.tum.cit.aet.hephaestus.practices.PracticeReads;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ReadNeed;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewCaptureDecision;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewPracticeStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class DefaultReviewExecutionHandlerTest extends BaseUnitTest {
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private final ReviewJobAssignments assignments = mock(ReviewJobAssignments.class);
    private final ItemSource source = mock(ItemSource.class);
    private final WorkspaceContextBuilder context = mock(WorkspaceContextBuilder.class);
    private final JobEvidenceFiles files = mock(JobEvidenceFiles.class);
    private final PracticeCatalogInjector catalog = mock(PracticeCatalogInjector.class);
    private final ReviewHistoryContentSource history = mock(ReviewHistoryContentSource.class);
    private final ReviewAssignmentStore members = mock(ReviewAssignmentStore.class);
    private final ItemDescriptor descriptor = mock(ItemDescriptor.class);
    private final ItemDescriptorRegistry items = new ItemDescriptorRegistry(List.of()) {
        @Override
        public Optional<ItemDescriptor> descriptorFor(ItemKind kind) {
            return Optional.of(descriptor);
        }
    };
    private final JsonMapper mapper = new JsonMapper();
    private final DefaultReviewExecutionHandler handler = new DefaultReviewExecutionHandler(
            assignments,
            List.of(source),
            context,
            files,
            catalog,
            new PracticeScriptInputs(mapper),
            history,
            members,
            items,
            List.of(),
            mapper,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new AppliesWhenEvaluator(List.of(new ScmCapturedApplicability(mapper))),
            mock(ResultAdmissionService.class));

    private ReviewJob prepare(boolean available) {
        return prepare(
                ReadNeed.REQUIRED,
                available
                        ? new SourceCaptureState.Available(
                                SourceContentState.EMPTY,
                                SourceCompleteness.COMPLETE,
                                new SourceCaptureFacts(NOW, null, null, "docs:one"))
                        : null);
    }

    private ReviewJob prepare(ReadNeed need, @Nullable SourceCaptureState state) {
        var job = RuntimeReviewTestData.job();
        var original = RuntimeReviewTestData.assignment(5L, null);
        var old = original.practice();
        var d = old.definition();
        var definition = new PracticeDefinition(
                d.name(),
                d.reviewOn(),
                d.appliesWhen(),
                d.about(),
                new PracticeReads(
                        "2.0.0",
                        need == ReadNeed.OPTIONAL
                                ? List.of(
                                        new PracticeReadSource(new SourceKind("docs.document.core"), ReadNeed.REQUIRED),
                                        new PracticeReadSource(
                                                new SourceKind("scm.pull-request.comments"), ReadNeed.OPTIONAL))
                                : List.of(new PracticeReadSource(new SourceKind("docs.document.core"), need))),
                d.reviewedBy(),
                d.criteria(),
                d.script(),
                d.scan(),
                d.lineNotes(),
                d.guidance());
        var assignment = new ReviewAssignmentSnapshot(
                original.id(),
                original.item(),
                original.version(),
                original.developerId(),
                original.practiceSlug(),
                new PinnedPractice(
                        old.practiceId(), old.practiceVersionId(), definition, old.reviewDigest(), old.method()));
        var review = RuntimeReviewTestData.review(ReviewKind.ITEM, assignment);
        when(assignments.review(job)).thenReturn(review);
        when(assignments.load(job)).thenReturn(List.of(assignment));
        when(source.supports(assignment.item())).thenReturn(true);
        var pinned = mock(ItemSnapshot.class);
        when(source.pin(assignment.item(), assignment.version())).thenReturn(pinned);
        var capturedState = state == null
                ? new SourceCaptureState.NotCollected(SourceAbsenceReason.GOVERNANCE_NOT_EFFECTIVE)
                : state;
        List<SourceCapture> sources = need == ReadNeed.OPTIONAL
                ? List.of(
                        new SourceCapture(
                                new SourceKind("docs.document.core"),
                                new SourceCaptureState.Available(
                                        SourceContentState.EMPTY,
                                        SourceCompleteness.COMPLETE,
                                        new SourceCaptureFacts(NOW, null, null, "docs:one")),
                                EvidenceSnapshotFixtures.citation(new SourceKind("docs.document.core")),
                                List.of()),
                        new SourceCapture(
                                new SourceKind("scm.pull-request.comments"),
                                capturedState,
                                EvidenceSnapshotFixtures.citation(new SourceKind("scm.pull-request.comments")),
                                List.of()))
                : List.of(new SourceCapture(
                        new SourceKind("docs.document.core"),
                        capturedState,
                        EvidenceSnapshotFixtures.citation(new SourceKind("docs.document.core")),
                        List.of()));
        var manifest = new JobFolderIndex(
                new SourceContractVersion("2.0.0"),
                "a".repeat(64),
                assignment.item().kind().value(),
                NOW,
                sources,
                List.of(),
                List.of(),
                List.of());
        when(context.prepare(eq(pinned), any())).thenReturn(new PreparedEvidence(Map.of(), manifest));
        if (need == ReadNeed.OPTIONAL
                || (state instanceof SourceCaptureState.Available available
                        && (need != ReadNeed.COMPLETE || available.completeness() == SourceCompleteness.COMPLETE)))
            when(source.record(eq(pinned), any()))
                    .thenReturn(mapper.createObjectNode().put("title", "Captured document"));
        when(files.prepare(eq(job), any(), any()))
                .thenReturn(new PreparedJobInputs(Map.of(), List.of(), List.of(), manifest, null));
        return job;
    }

    @Test
    void capturesAndStagesAnItemWithoutAProviderSpecificHandler() {
        var job = prepare(true);
        try (var prepared = handler.prepareInputs(job)) {
            assertThat(prepared.folderIndex()).isNotNull();
        }
        verify(files).beginPersonCapture(job);
        verify(catalog).inject(any(), any(), eq(ReviewKind.ITEM), eq(Map.of()));
        assertDecision(job, ReviewPracticeStatus.READY);
    }

    @Test
    void scanStagesTheVersionBoundGrantInsteadOfExpandingCapturedFiles() {
        var job = prepare(true);
        var assignment = PracticeCatalogInjectorTest.scanAssignment();
        when(assignments.load(job)).thenReturn(List.of(assignment));
        when(assignments.review(job)).thenReturn(RuntimeReviewTestData.review(ReviewKind.SCAN, assignment));
        var composer = mock(ScanItemVersions.class);
        when(composer.kinds()).thenReturn(Set.of(assignment.item().kind()));
        when(composer.contextPaths(assignment.item(), assignment.version(), List.of("docs/*.md")))
                .thenReturn(List.of("docs/rules.md"));
        var scanHandler = new DefaultReviewExecutionHandler(
                assignments,
                List.of(source),
                context,
                files,
                new PracticeCatalogInjector(mapper),
                new PracticeScriptInputs(mapper),
                history,
                members,
                items,
                List.of(composer),
                mapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new AppliesWhenEvaluator(List.of(new ScmCapturedApplicability(mapper))),
                mock(ResultAdmissionService.class));
        try (var prepared = scanHandler.prepareInputs(job)) {
            assertThat(prepared.folderIndex()).isNotNull();
        }
        ArgumentCaptor<PreparedEvidence> captured = ArgumentCaptor.captor();
        verify(files).prepare(eq(job), captured.capture(), any());
        var staged = captured.getValue().files();
        var index = mapper.readTree(staged.get(SandboxLayout.PRACTICES_PREFIX + "index.json"))
                .get(0);
        assertThat(index.path("contextPaths")).isEqualTo(mapper.valueToTree(List.of("docs/rules.md")));
        ArgumentCaptor<CaptureRequest> request = ArgumentCaptor.captor();
        verify(context).prepare(any(), request.capture());
        assertThat(request.getValue().scanPaths()).containsExactly("docs/rules.md");
        var script =
                mapper.readTree(staged.get(SandboxLayout.PRACTICE_SCRIPT_PRACTICES_PREFIX + "clear-names.inputs.json"));
        assertThat(script.path("scope").path("version").asString())
                .isEqualTo(assignment.version().value());
        assertThat(script.path("about").isNull()).isTrue();
    }

    @Test
    void shouldRefuseAScanWithoutItsDomainVersionComposer() {
        var job = RuntimeReviewTestData.job();
        var assignment = PracticeCatalogInjectorTest.scanAssignment();
        when(assignments.load(job)).thenReturn(List.of(assignment));
        when(assignments.review(job)).thenReturn(RuntimeReviewTestData.review(ReviewKind.SCAN, assignment));
        when(source.supports(assignment.item())).thenReturn(true);
        assertThatThrownBy(() -> handler.prepareInputs(job))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The scan item needs exactly one version composer");
        verifyNoInteractions(context, files);
    }

    @Test
    void captureRefusalRecordsWithheldAndNeverInventsAResult() {
        var job = prepare(false);
        assertThatThrownBy(() -> handler.prepareInputs(job)).isInstanceOf(InsufficientEvidenceException.class);
        assertDecision(job, ReviewPracticeStatus.WITHHELD);
        verify(files).abortPersonCapture(job);
    }

    private ReviewJob prepareApplicability(boolean available, Set<String> paths) {
        var job = prepare(true);
        var oldAssignment = RuntimeReviewTestData.assignment(5L, null);
        var old = oldAssignment.practice();
        var kind = new ItemKind("scm.pull_request");
        var d = DefinitionTestFixtures.definition(
                kind, old.definition().name(), old.definition().criteria(), null);
        var oldItem = oldAssignment.item();
        var item = new ItemRef(
                oldItem.workspaceId(), oldItem.itemId(), kind, oldItem.sourceId(), oldItem.parentId(), oldItem.path());
        var definition = new PracticeDefinition(
                d.name(),
                d.reviewOn(),
                new AppliesWhen(
                        List.of(new AppliesWhenClause(List.of("**/*.swift"), null, null)),
                        "The change touches no Swift file."),
                d.about(),
                new PracticeReads(
                        "2.0.0",
                        List.of(new PracticeReadSource(new SourceKind("scm.pull-request.diff"), ReadNeed.REQUIRED))),
                d.reviewedBy(),
                d.criteria(),
                d.script(),
                d.scan(),
                d.lineNotes(),
                d.guidance());
        var assignment = new ReviewAssignmentSnapshot(
                oldAssignment.id(),
                item,
                oldAssignment.version(),
                oldAssignment.developerId(),
                oldAssignment.practiceSlug(),
                new PinnedPractice(
                        old.practiceId(), old.practiceVersionId(), definition, old.reviewDigest(), old.method()));
        when(assignments.load(job)).thenReturn(List.of(assignment));
        when(assignments.review(job)).thenReturn(RuntimeReviewTestData.review(ReviewKind.ITEM, assignment));
        when(source.supports(item)).thenReturn(true);
        var diff = new SourceKind("scm.pull-request.diff");
        SourceCaptureState state = available
                ? new SourceCaptureState.Available(
                        SourceContentState.NON_EMPTY,
                        SourceCompleteness.COMPLETE,
                        new SourceCaptureFacts(NOW, null, null, "base:head"))
                : new SourceCaptureState.Unavailable(SourceAbsenceReason.NO_PROVIDER);
        var manifest = new JobFolderIndex(
                new SourceContractVersion("2.0.0"),
                "a".repeat(64),
                assignment.item().kind().value(),
                NOW,
                List.of(new SourceCapture(
                        diff,
                        state,
                        EvidenceSnapshotFixtures.citation(diff),
                        available
                                ? List.of(new CapturedSource(
                                        "context/change.json", "application/json", "a".repeat(64), 1))
                                : List.of())),
                List.of(),
                List.of(),
                List.of());
        var pinned = mock(ItemSnapshot.class);
        when(source.pin(assignment.item(), assignment.version())).thenReturn(pinned);
        var captured = available
                ? Map.of(
                        "context/change.json",
                        mapper.writeValueAsBytes(mapper.createObjectNode()
                                .put("base_sha", "a".repeat(40))
                                .put("head_sha", "b".repeat(40))
                                .put("diff", "+changed();\n")
                                .set("changed_paths", mapper.valueToTree(paths))))
                : Map.<String, byte[]>of();
        when(context.prepare(eq(pinned), any())).thenReturn(new PreparedEvidence(captured, manifest));
        when(source.record(eq(pinned), any()))
                .thenReturn(mapper.createObjectNode().put("title", "Captured change"));
        when(files.prepare(eq(job), any(), any()))
                .thenAnswer(
                        call -> new PreparedJobInputs(Map.of(), List.of(), List.of(), manifest, call.getArgument(2)));
        return job;
    }

    @Test
    void shouldExcludeAProvenNonmatchingPracticeFromTheSandboxWithoutRefusingCapture() {
        var job = prepareApplicability(true, Set.of("src/Main.java"));
        try (var prepared = handler.prepareInputs(job)) {
            var report = prepared.automatedReviewReadinessReport();
            assertThat(report).isNotNull();
            assertThat(report.decisions().getFirst().ready()).isTrue();
            assertThat(Objects.requireNonNull(report.applicability().get("clear-names"))
                            .absent())
                    .isTrue();
            assertThat(Objects.requireNonNull(report.applicability().get("clear-names"))
                            .otherwise())
                    .isEqualTo("The change touches no Swift file.");
        }
        verify(catalog).inject(any(), eq(List.of()), eq(ReviewKind.ITEM), eq(Map.of()));
        assertDecision(job, ReviewPracticeStatus.READY);
    }

    @Test
    void shouldReviewThePracticeWhenAChangedPathMatches() {
        var job = prepareApplicability(true, Set.of("src/App.swift"));
        try (var prepared = handler.prepareInputs(job)) {
            var report = prepared.automatedReviewReadinessReport();
            assertThat(report).isNotNull();
            assertThat(Objects.requireNonNull(report.applicability().get("clear-names"))
                            .absent())
                    .isFalse();
        }
        ArgumentCaptor<List<ReviewAssignmentSnapshot>> staged = ArgumentCaptor.captor();
        verify(catalog).inject(any(), staged.capture(), eq(ReviewKind.ITEM), eq(Map.of()));
        assertThat(staged.getValue())
                .singleElement()
                .satisfies(assignment -> assertThat(assignment.practiceSlug()).isEqualTo("clear-names"));
    }

    @Test
    void shouldKeepTheCaptureFailureWhenTheApplicabilitySourceIsUnavailable() {
        var job = prepareApplicability(false, Set.of());
        assertThatThrownBy(() -> handler.prepareInputs(job)).isInstanceOf(InsufficientEvidenceException.class);
        assertDecision(job, ReviewPracticeStatus.WITHHELD);
        var facts = ArgumentCaptor.forClass(PreparedEvidence.class);
        var report = ArgumentCaptor.forClass(AutomatedReviewReadinessReport.class);
        verify(files).prepare(eq(job), facts.capture(), report.capture());
        assertThat(report.getValue().applicability()).isEmpty();
        assertThat(report.getValue().decisions().getFirst().reasonCodes())
                .contains(AutomatedReviewReadinessReason.DECLARED_EVIDENCE_INSUFFICIENT);
    }

    private void assertDecision(ReviewJob job, ReviewPracticeStatus expected) {
        ArgumentCaptor<List<ReviewCaptureDecision>> decisions = ArgumentCaptor.captor();
        verify(members)
                .capture(eq(1L), eq(job.getReviewId()), eq(job.getId()), eq(job.getAttempt()), decisions.capture());
        assertThat(decisions.getValue().getFirst().status()).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(
            value = SourceAbsenceReason.class,
            names = {"REPOSITORY_FETCH_FAILED", "REVIEW_VERSION_UNAVAILABLE"})
    void shouldRunAvailablePracticesAndWithholdOnlyRequiredRepositoryReads(SourceAbsenceReason reason) {
        var job = RuntimeReviewTestData.job();
        var available = RuntimeReviewTestData.assignment(5L, null);
        var original = available.practice();
        var d = original.definition();
        var tree = new SourceKind("scm.repository.tree");
        var definition = new PracticeDefinition(
                d.name(),
                d.reviewOn(),
                d.appliesWhen(),
                d.about(),
                new PracticeReads("2.0.0", List.of(new PracticeReadSource(tree, ReadNeed.REQUIRED))),
                d.reviewedBy(),
                d.criteria(),
                d.script(),
                d.scan(),
                d.lineNotes(),
                d.guidance());
        var withheld = new ReviewAssignmentSnapshot(
                new ReviewAssignmentId(job.getReviewId(), 41L, available.item().itemId()),
                available.item(),
                available.version(),
                available.developerId(),
                "repository-practice",
                new PinnedPractice(41L, 42L, definition, original.reviewDigest(), original.method()));
        when(assignments.load(job)).thenReturn(List.of(available, withheld));
        when(assignments.review(job)).thenReturn(RuntimeReviewTestData.review(ReviewKind.ITEM, available));
        when(source.supports(available.item())).thenReturn(true);
        var snapshot = mock(ItemSnapshot.class);
        when(source.pin(available.item(), available.version())).thenReturn(snapshot);
        when(source.record(eq(snapshot), any()))
                .thenReturn(mapper.createObjectNode().put("title", "Captured work"));
        var manifest = new JobFolderIndex(
                new SourceContractVersion("2.0.0"),
                "a".repeat(64),
                available.item().kind().value(),
                NOW,
                List.of(
                        new SourceCapture(
                                new SourceKind("docs.document.core"),
                                new SourceCaptureState.Available(
                                        SourceContentState.EMPTY,
                                        SourceCompleteness.COMPLETE,
                                        new SourceCaptureFacts(NOW, null, null, "docs:one")),
                                EvidenceSnapshotFixtures.citation(new SourceKind("docs.document.core")),
                                List.of()),
                        new SourceCapture(
                                tree,
                                new SourceCaptureState.Unavailable(reason),
                                EvidenceSnapshotFixtures.citation(tree),
                                List.of())),
                List.of(),
                List.of(),
                List.of());
        when(context.prepare(eq(snapshot), any())).thenReturn(new PreparedEvidence(Map.of(), manifest));
        when(files.prepare(eq(job), any(), any()))
                .thenReturn(new PreparedJobInputs(Map.of(), List.of(), List.of(), manifest, null));

        try (var prepared = handler.prepareInputs(job)) {
            assertThat(prepared.folderIndex()).isNotNull();
        }

        ArgumentCaptor<List<ReviewCaptureDecision>> decisions = ArgumentCaptor.captor();
        verify(members)
                .capture(eq(1L), eq(job.getReviewId()), eq(job.getId()), eq(job.getAttempt()), decisions.capture());
        assertThat(decisions.getValue())
                .satisfiesExactly(
                        decision -> assertThat(decision.status()).isEqualTo(ReviewPracticeStatus.READY), decision -> {
                            assertThat(decision.status()).isEqualTo(ReviewPracticeStatus.WITHHELD);
                            assertThat(decision.facts()
                                            .path("sourceChecks")
                                            .get(0)
                                            .path("reasonCodes"))
                                    .isEqualTo(mapper.valueToTree(
                                            List.of(SourceReadinessReason.SOURCE_NOT_AVAILABLE.name(), reason.name())));
                        });
        verify(catalog).inject(any(), eq(List.of(available)), eq(ReviewKind.ITEM), eq(Map.of()));
    }

    @Test
    void optionalCaptureAbsenceDoesNotWithholdAnOtherwiseReadyPractice() {
        var job = prepare(ReadNeed.OPTIONAL, null);
        try (var prepared = handler.prepareInputs(job)) {
            assertThat(prepared.folderIndex()).isNotNull();
        }
        assertDecision(job, ReviewPracticeStatus.READY);
    }

    @Test
    void aCompleteReadCannotTurnAPartialCaptureIntoACleanClaim() {
        var partial = new SourceCaptureState.Available(
                SourceContentState.EMPTY,
                SourceCompleteness.PARTIAL,
                new SourceCaptureFacts(NOW, null, null, "docs:one"),
                List.of("The capture did not include every comment."));
        var job = prepare(ReadNeed.COMPLETE, partial);
        assertThatThrownBy(() -> handler.prepareInputs(job)).isInstanceOf(InsufficientEvidenceException.class);
        assertDecision(job, ReviewPracticeStatus.WITHHELD);
    }

    @Test
    void shouldStageThePreviousResultsActualCitationCoordinates() {
        var job = prepare(true);
        var prior = mock(ResultHistoryEntry.class);
        when(prior.resultId()).thenReturn(UUID.fromString("10000000-0000-4000-8000-000000000099"));
        when(prior.recordedAt()).thenReturn(NOW.minusSeconds(60));
        when(prior.origin()).thenReturn(ReviewOrigin.REQUEST);
        when(prior.itemVersion()).thenReturn("docs:previous");
        when(prior.outcome()).thenReturn(ResultOutcome.MET);
        when(prior.evidence()).thenReturn(mapper.readTree("""
            {"openQuestions":[],"citations":[{"sourceKind":"docs.document.core","sourcePath":"context/body.txt",
              "path":"document/body","startLine":2,"endLine":3,"quote":"Exact words",
              "verification":{"status":"VERIFIED","itemSha256":"digest","quoteSha256":"digest"}}]}
            """));
        when(history.previousResults(any())).thenReturn(List.of(prior));
        try (var prepared = handler.prepareInputs(job)) {
            assertThat(prepared.folderIndex()).isNotNull();
        }
        var captured = ArgumentCaptor.forClass(PreparedEvidence.class);
        verify(files).prepare(eq(job), captured.capture(), any());
        var staged = captured.getValue().files().entrySet().stream()
                .filter(file -> file.getKey().endsWith(".inputs.json"))
                .findFirst()
                .orElseThrow();
        var citations = mapper.readTree(staged.getValue()).path("previous").path("citations");
        assertThat(citations.size()).isEqualTo(1);
        assertThat(citations.get(0).path("startLine").asInt()).isEqualTo(2);
        assertThat(citations.get(0).path("endLine").asInt()).isEqualTo(3);
        assertThat(citations.get(0).path("path").asString()).isEqualTo("document/body");
        assertThat(citations.get(0).has("quote")).isFalse();
        assertThat(citations.get(0).has("verification")).isFalse();
    }

    @Test
    void shouldRejectMalformedStoredCitationHistory() {
        var job = prepare(true);
        var prior = mock(ResultHistoryEntry.class);
        when(prior.resultId()).thenReturn(UUID.fromString("10000000-0000-4000-8000-000000000099"));
        when(prior.recordedAt()).thenReturn(NOW.minusSeconds(60));
        when(prior.origin()).thenReturn(ReviewOrigin.REQUEST);
        when(prior.itemVersion()).thenReturn("docs:previous");
        when(prior.outcome()).thenReturn(ResultOutcome.MET);
        when(prior.evidence()).thenReturn(mapper.readTree("""
            {"citations":[{"sourceKind":"docs.document.core","sourcePath":"context/body.txt",
              "path":"document/body","startLine":"corrupt","endLine":3,"quote":"Exact words"}],
              "openQuestions":[]}
            """));
        when(history.previousResults(any())).thenReturn(List.of(prior));
        assertThatThrownBy(() -> handler.prepareInputs(job)).isInstanceOf(JacksonException.class);
        verify(files).abortPersonCapture(job);
    }
}
