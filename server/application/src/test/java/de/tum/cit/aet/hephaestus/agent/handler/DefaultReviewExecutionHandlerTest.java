package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.InsufficientEvidenceException;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewHistoryContentSource;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobAssignments;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCapture;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureFacts;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeReadSource;
import de.tum.cit.aet.hephaestus.practices.PracticeReads;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ReadNeed;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    private final ItemCatalog items = new ItemCatalog() {
        @Override
        public Collection<ItemDescriptor> all() {
            return List.of(descriptor);
        }

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
            mapper,
            Clock.fixed(NOW, ZoneOffset.UTC));

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
                                                new SourceKind("hephaestus.result-history"), ReadNeed.OPTIONAL))
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
                                List.of()),
                        new SourceCapture(new SourceKind("hephaestus.result-history"), capturedState, List.of()))
                : List.of(new SourceCapture(new SourceKind("docs.document.core"), capturedState, List.of()));
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
        verify(catalog).inject(any(), any(), eq(ReviewKind.ITEM));
        assertDecision(job, ReviewPracticeStatus.READY);
    }

    @Test
    void captureRefusalRecordsWithheldAndNeverInventsAResult() {
        var job = prepare(false);
        assertThatThrownBy(() -> handler.prepareInputs(job)).isInstanceOf(InsufficientEvidenceException.class);
        assertDecision(job, ReviewPracticeStatus.WITHHELD);
        verify(files).abortPersonCapture(job);
    }

    private void assertDecision(ReviewJob job, ReviewPracticeStatus expected) {
        ArgumentCaptor<List<ReviewCaptureDecision>> decisions = ArgumentCaptor.captor();
        verify(members)
                .capture(eq(1L), eq(job.getReviewId()), eq(job.getId()), eq(job.getAttempt()), decisions.capture());
        assertThat(decisions.getValue().getFirst().status()).isEqualTo(expected);
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
            {"citations":[{"sourceKind":"docs.document.core","sourcePath":"context/body.txt",
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
}
