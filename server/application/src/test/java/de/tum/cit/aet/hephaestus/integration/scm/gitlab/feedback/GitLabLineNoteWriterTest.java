package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static de.tum.cit.aet.hephaestus.integration.scm.GraphQlResponseStubValidator.Vendor.GITLAB;
import static de.tum.cit.aet.hephaestus.integration.scm.GraphQlResponseStubValidator.assertVendorCouldReturn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGuard;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressSuppressedException;
import de.tum.cit.aet.hephaestus.integration.core.egress.SilentModeGraphQlClientFactory;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyDisposition;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyForm;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyReceipt;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyWriteFence;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyWriteResult;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNote;
import de.tum.cit.aet.hephaestus.integration.scm.feedback.ScmFeedbackFixtures;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabRateLimitTracker;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabTokenService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.credentials.GitLabCredentialHealthFilter;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

class GitLabLineNoteWriterTest extends BaseUnitTest {

    private static final String MARKER = "<!-- hephaestus-review-package:job-1 -->";
    private static final String REVIEWED = "3f2b9c1d0e8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c";
    private static final String SERVER = "https://gitlab.example.com";
    private static final String BOT = "gid://gitlab/User/1";
    private static final String PERSON = "gid://gitlab/User/2";
    private static final String NOTE_URL = "https://gitlab.example.com/group/project/-/merge_requests/42#note_987654";
    private static final String DISCUSSIONS = "/api/v4/projects/group%2Fproject/merge_requests/42/discussions";
    private static final String THREAD = "6a9c1750b37d513a43987b574953fceb50b03ce7";
    private static final String NEW_JAVA = GitLabDiffPositionsTest.NEW_JAVA;
    // The reviewed version was read completely, and its diff does not contain the cited line.
    private static final GitLabDiffPositions.Version WITHOUT_CITED_LINE = new GitLabDiffPositions.Version(
            1,
            GitLabDiffPositionsTest.VERSION.base(),
            GitLabDiffPositionsTest.VERSION.start(),
            GitLabDiffPositionsTest.VERSION.head(),
            List.of());

    @Mock
    private GitLabGraphQlClientProvider gitLabProvider;

    @Mock
    private GitLabTokenService tokenService;

    @Mock
    private OutboundEgressGuard egressGuard;

    @Mock
    private GitLabDiffVersionReader diffVersions;

    private final ScriptedGitLabRest rest = new ScriptedGitLabRest();
    private GitLabLineNoteWriter channel;
    private HttpGraphQlClient client;
    private final RecordingFence fence = new RecordingFence();

    @BeforeEach
    void setUp() {
        channel = new GitLabLineNoteWriter(
                gitLabProvider,
                rest.builder(),
                tokenService,
                egressGuard,
                ScmFeedbackFixtures.resolver(),
                diffVersions);
        client = mock(HttpGraphQlClient.class);
        lenient().when(tokenService.resolveServerUrl(1L)).thenReturn(SERVER);
        lenient().when(tokenService.getAccessToken(1L)).thenReturn("glpat-test");
    }

    @Test
    void shouldKeepTheBudgetReportedByRestAndStopTheNextPackageWhenItIsCritical() {
        var meters = new SimpleMeterRegistry();
        try {
            var tracker = new GitLabRateLimitTracker(meters);
            var trackedProvider = spy(new GitLabGraphQlClientProvider(
                    client,
                    tokenService,
                    mock(CircuitBreaker.class),
                    tracker,
                    mock(SilentModeGraphQlClientFactory.class),
                    mock(GitLabCredentialHealthFilter.class)));
            doReturn(client).when(trackedProvider).forScope(1L);
            channel = new GitLabLineNoteWriter(
                    trackedProvider,
                    rest.builder(),
                    tokenService,
                    egressGuard,
                    ScmFeedbackFixtures.resolver(),
                    diffVersions);
            stubDiscussions(List.of(), BOT);
            when(diffVersions.read(eq(1L), any(), any())).thenReturn(GitLabDiffPositionsTest.VERSION);
            rest.answer(call -> ScriptedGitLabRest.respond(
                            201, ScriptedGitLabRest.created(call, 987654, "DiffNote", 1, position -> position))
                    .map(response -> response.mutate()
                            .header("RateLimit-Limit", "100")
                            .header("RateLimit-Remaining", "0")
                            .build()));
            var selected = ScmFeedbackFixtures.line(
                    new FeedbackAnchor("new.java", null, 21, CitationSide.NEW), "fix", MARKER, "selected");

            var first = channel.postImmutablePackage(target(), List.of(selected), fence);

            assertThat(first.receipts())
                    .singleElement()
                    .satisfies(copy -> assertThat(copy.disposition()).isEqualTo(CopyDisposition.POSTED));
            assertThat(tracker.isCritical(1L)).isTrue();
            var next = channel.postImmutablePackage(target(), List.of(selected), fence);
            assertThat(next.receipts()).noneMatch(copy -> copy.disposition() == CopyDisposition.POSTED);
            assertThat(rest.calls()).hasSize(1);
            assertThat(fence.attempts).hasSize(1);
        } finally {
            meters.close();
        }
    }

    // The single line and the range are the two text positions of GitLab's "Create a new thread in the diff".
    @Test
    void shouldPostASingleLineAsANativeDiffThreadWithTheDocumentedPosition() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(GitLabDiffPositionsTest.VERSION);
        rest.createAsAsked(987654, "DiffNote", 1);
        var selected = ScmFeedbackFixtures.line(
                new FeedbackAnchor("new.java", null, 21, CitationSide.NEW), "fix", MARKER, "selected");

        var result = channel.postImmutablePackage(target(), List.of(selected), fence);

        assertThat(result.receipts()).singleElement().satisfies(copy -> {
            assertThat(copy.disposition()).isEqualTo(CopyDisposition.POSTED);
            assertThat(copy.actualKind()).isEqualTo(CopyForm.LINE_NOTE);
            assertThat(copy.externalRef()).isEqualTo("gid://gitlab/DiffNote/987654");
            assertThat(copy.externalUrl()).isEqualTo(NOTE_URL);
        });
        assertThat(rest.calls()).singleElement().satisfies(call -> {
            assertThat(call.method()).isEqualTo(HttpMethod.POST);
            assertThat(call.path()).isEqualTo(DISCUSSIONS);
            assertThat(call.authorization()).isEqualTo("Bearer glpat-test");
            assertThat(call.json())
                    .isEqualTo(Map.of(
                            "body",
                            posted(selected),
                            "position",
                            Map.of(
                                    "position_type",
                                    "text",
                                    "base_sha",
                                    "a".repeat(40),
                                    "start_sha",
                                    "b".repeat(40),
                                    "head_sha",
                                    "c".repeat(40),
                                    "old_path",
                                    "old.java",
                                    "new_path",
                                    "new.java",
                                    "new_line",
                                    21)));
        });
        assertThat(fence.attempts).hasSize(1);
    }

    @Test
    void shouldPostARangeAsOneNativeDiffThreadWithBothEndsAndItsPlacement() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(GitLabDiffPositionsTest.VERSION);
        rest.createAsAsked(987654, "DiffNote", 1);
        var selected = ScmFeedbackFixtures.line(
                new FeedbackAnchor("new.java", 20, 21, CitationSide.NEW), "fix", MARKER, "selected");

        var result = channel.postImmutablePackage(target(), List.of(selected), fence);

        assertThat(result.receipts()).singleElement().satisfies(copy -> {
            assertThat(copy.disposition()).isEqualTo(CopyDisposition.POSTED);
            assertThat(copy.actualKind()).isEqualTo(CopyForm.LINE_NOTE);
            assertThat(copy.externalRef()).isEqualTo("gid://gitlab/DiffNote/987654");
        });
        assertThat(rest.calls())
                .singleElement()
                .satisfies(call -> assertThat(call.json())
                        .isEqualTo(Map.of(
                                "body",
                                placed(selected),
                                "position",
                                Map.of(
                                        "position_type",
                                        "text",
                                        "base_sha",
                                        "a".repeat(40),
                                        "start_sha",
                                        "b".repeat(40),
                                        "head_sha",
                                        "c".repeat(40),
                                        "old_path",
                                        "old.java",
                                        "new_path",
                                        "new.java",
                                        "new_line",
                                        21,
                                        "line_range",
                                        Map.of(
                                                "start",
                                                Map.of(
                                                        "line_code",
                                                        NEW_JAVA + "_10_20",
                                                        "type",
                                                        "old",
                                                        "old_line",
                                                        10,
                                                        "new_line",
                                                        20),
                                                "end",
                                                Map.of(
                                                        "line_code",
                                                        NEW_JAVA + "_12_21",
                                                        "type",
                                                        "new",
                                                        "new_line",
                                                        21))))));
    }

    // A created thread is this copy only when GitLab's answer holds exactly what was asked, both ends included.
    @Test
    void shouldLeaveACreatedThreadThatDoesNotHoldTheAskedCopyUnconfirmedWithoutASecondWrite() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(GitLabDiffPositionsTest.VERSION);
        List<Function<ScriptedGitLabRest.Call, Object>> answers = List.of(
                call -> ScriptedGitLabRest.created(call, 987654, "DiffNote", 1, position -> {
                    Objects.requireNonNull(position).remove("line_range");
                    return position;
                }),
                call -> ScriptedGitLabRest.created(call, 987654, "DiffNote", 1, position -> {
                    Objects.requireNonNull(position)
                            .put(
                                    "line_range",
                                    Map.of(
                                            "start",
                                            Map.of("line_code", NEW_JAVA + "_12_21", "type", "new", "new_line", 21),
                                            "end",
                                            Map.of("line_code", NEW_JAVA + "_12_21", "type", "new", "new_line", 21)));
                    return position;
                }),
                call -> ScriptedGitLabRest.created(call, 987654, "DiffNote", 2, position -> position),
                call -> ScriptedGitLabRest.created(call, 987654, "DiscussionNote", 1, position -> position),
                call -> ScriptedGitLabRest.created(call, 987654, "DiffNote", 1, position -> null),
                call -> firstNote(
                        ScriptedGitLabRest.created(call, 987654, "DiffNote", 1, position -> position),
                        "body",
                        "edited"),
                call -> firstNote(
                        ScriptedGitLabRest.created(call, 987654, "DiffNote", 1, position -> position), "id", "987654"),
                call -> firstNote(
                        ScriptedGitLabRest.created(call, 987654, "DiffNote", 1, position -> position), "id", 0),
                call -> "{\"id\": \"6a9c\", \"notes\": [",
                call -> "");
        var selected = ScmFeedbackFixtures.line(
                new FeedbackAnchor("new.java", 20, 21, CitationSide.NEW), "fix", MARKER, "selected");

        for (var answer : answers) {
            rest.answer(call -> ScriptedGitLabRest.respond(201, answer.apply(call)));
            assertThat(channel.postImmutablePackage(target(), List.of(selected), fence)
                            .receipts())
                    .singleElement()
                    .satisfies(copy -> assertThat(copy.unconfirmed()).isTrue());
        }

        assertThat(rest.calls())
                .hasSize(answers.size())
                .allSatisfy(call -> assertThat(call.json()).containsKey("position"));
    }

    @Test
    void shouldUseTheCopyFenceForAFallbackOnlyAfterAnExplicitPositionRejection() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(GitLabDiffPositionsTest.VERSION);
        rest.answer(
                400,
                Map.of(
                        "message",
                        "400 Bad request - Note {:line_code=>[\"can't be blank\", \"must be a valid line code\"]}"));
        rest.createAsAsked(2, "DiscussionNote", 1);
        var selected = ScmFeedbackFixtures.line(
                new FeedbackAnchor("new.java", null, 21, CitationSide.NEW), "fix", MARKER, "selected");

        assertThat(channel.postImmutablePackage(target(), List.of(selected), fence)
                        .receipts())
                .singleElement()
                .satisfies(copy -> {
                    assertThat(copy.actualKind()).isEqualTo(CopyForm.FILE_NOTE);
                    assertThat(copy.externalRef()).isEqualTo("gid://gitlab/DiscussionNote/2");
                });
        assertThat(fence.attempts).hasSize(1);
        verify(egressGuard, times(2)).requireDeliveryAllowed("gitlab.post-inline-feedback");
        assertThat(rest.calls().getLast().json())
                .isEqualTo(Map.of(
                        "body",
                        "**[View code at line 21](" + SERVER + "/group/project/-/blob/" + REVIEWED
                                + "/new.java#L21)**\n\n" + posted(selected)));
    }

    // Any answer but a position rejection may follow a created thread, so none of them starts the fallback.
    @Test
    void shouldKeepEveryOtherAnswerUnconfirmedWithoutAFallback() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(GitLabDiffPositionsTest.VERSION);
        var selected = ScmFeedbackFixtures.line(
                new FeedbackAnchor("new.java", null, 21, CitationSide.NEW), "fix", MARKER, "selected");
        List<Integer> statuses = List.of(400, 401, 403, 404, 409, 422, 500, 502, 503);
        for (int status : statuses) rest.answer(status, Map.of("message", status + " Bad request - body is invalid"));
        rest.fail(new IllegalStateException("Timeout on blocking read for 30000000000 NANOSECONDS"));
        rest.fail(new RuntimeException("Connection reset by peer"));

        for (int answer = 0; answer < statuses.size() + 2; answer++) {
            assertThat(channel.postImmutablePackage(target(), List.of(selected), fence)
                            .receipts())
                    .singleElement()
                    .satisfies(copy -> assertThat(copy.unconfirmed()).isTrue());
        }

        assertThat(rest.calls())
                .hasSize(statuses.size() + 2)
                .allSatisfy(call -> assertThat(call.json()).containsKey("position"));
    }

    // An unread diff version proves no position absent, so it must not become a plain comment.
    @Test
    void shouldCreateNothingWhenTheDiffVersionCannotBeRead() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any()))
                .thenThrow(new FeedbackDeliveryException("GitLab diff versions could not be read"));
        var selected = ScmFeedbackFixtures.line(
                new FeedbackAnchor("new.java", null, 21, CitationSide.NEW), "fix", MARKER, "selected");

        assertThatThrownBy(() -> channel.postImmutablePackage(target(), List.of(selected), fence))
                .isInstanceOf(FeedbackDeliveryException.class);

        assertThat(fence.attempts).isEmpty();
        verify(egressGuard, never()).requireDeliveryAllowed(anyString());
        assertThat(rest.calls()).isEmpty();
        assertThat(rest.calls()).isEmpty();
    }

    // A file-level copy is an overview thread: native replies and resolution, without a diff position.
    @Test
    void shouldPostAFileThreadWithoutReadingADiffVersion() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        rest.createAsAsked(987654, "DiscussionNote", 1);
        var file =
                ScmFeedbackFixtures.line(new FeedbackAnchor("src/Foo.java", null, null, null), "fix", MARKER, "file");

        var result = channel.postImmutablePackage(target(), List.of(file), fence);

        assertThat(result.receipts()).singleElement().satisfies(receipt -> {
            assertThat(receipt.disposition()).isEqualTo(CopyDisposition.POSTED);
            assertThat(receipt.actualKind()).isEqualTo(CopyForm.FILE_NOTE);
            assertThat(receipt.externalRef()).isEqualTo("gid://gitlab/DiscussionNote/987654");
        });
        assertThat(rest.calls())
                .singleElement()
                .satisfies(call -> assertThat(call.json())
                        .isEqualTo(Map.of(
                                "body",
                                "**[View file](" + SERVER + "/group/project/-/blob/" + REVIEWED + "/src/Foo.java)**\n\n"
                                        + posted(file))));
        verifyNoInteractions(diffVersions);
    }

    // A location thread is confirmed only as an unpositioned discussion note by this token.
    @Test
    void shouldLeaveALocationThreadUnconfirmedUnlessGitLabAnswersItAsAnOverviewThread() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        rest.createAsAsked(987654, "DiffNote", 1);
        rest.answer(call -> ScriptedGitLabRest.respond(
                201, ScriptedGitLabRest.created(call, 987654, "DiscussionNote", 1, position -> Map.of("new_line", 1))));
        var file =
                ScmFeedbackFixtures.line(new FeedbackAnchor("src/Foo.java", null, null, null), "fix", MARKER, "file");

        for (int answer = 0; answer < 2; answer++)
            assertThat(channel.postImmutablePackage(target(), List.of(file), fence)
                            .receipts())
                    .singleElement()
                    .satisfies(receipt -> assertThat(receipt.unconfirmed()).isTrue());
        assertThat(rest.calls()).hasSize(2);
    }

    @Test
    void shouldVerifyEachCopyUsingItsOwnMarker() {
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        var a = ScmFeedbackFixtures.line(
                ScmFeedbackFixtures.anchor("src/Foo.java", 10, null),
                "Quoted <!-- hephaestus-diff-note-ck=wrong -->",
                "<!-- copy-a -->",
                "copy-a");
        var b = ScmFeedbackFixtures.line(
                ScmFeedbackFixtures.anchor("src/Foo.java", 10, null), "Second note", "<!-- copy-b -->", "copy-b");
        stubDiscussions(
                List.of(
                        discussion("gid://Disc/A", List.of(note("gid://gitlab/Note/A", located(a), BOT))),
                        discussion("gid://Disc/B", List.of(note("gid://gitlab/Note/B", located(b), BOT)))),
                BOT);
        assertThat(channel.findPosted(target(), List.of(a, b)))
                .extracting(CopyReceipt::externalRef)
                .containsExactlyInAnyOrder("gid://gitlab/Note/A", "gid://gitlab/Note/B");
        assertThat(rest.calls()).isEmpty();
    }

    @Test
    void shouldRequestNothingForAnEmptyPackage() {
        assertThat(channel.postImmutablePackage(target(), List.of(), fence).receipts())
                .isEmpty();
        assertThat(fence.attempts).isEmpty();
    }

    @Test
    void shouldRequestNothingWhenTheScanCannotBeRead() {
        when(gitLabProvider.forScope(1L)).thenThrow(new RuntimeException("provider unavailable"));

        assertThatThrownBy(() -> channel.postImmutablePackage(target(), List.of(item("fix", "result:a:0")), fence))
                .isInstanceOf(FeedbackDeliveryException.class);
        assertThat(fence.attempts).isEmpty();
    }

    @Test
    void shouldRequestNothingWhenTheScanEndsOnItsPageBudgetWithMorePages() {
        stubResolvedMr();
        GraphQlClient.RequestSpec spec = discussionsSpec();
        AtomicInteger pages = new AtomicInteger();
        when(spec.execute())
                .thenAnswer(invocation ->
                        Mono.just(discussionsResponse(List.of(), true, "page-" + pages.incrementAndGet(), BOT)));

        assertThatThrownBy(() -> channel.postImmutablePackage(target(), List.of(item("fix", "result:a:0")), fence))
                .isInstanceOf(FeedbackDeliveryException.class);
        verify(spec, times(50)).execute();
        assertThat(rest.calls()).isEmpty();
        assertThat(fence.attempts).isEmpty();
    }

    @Test
    void shouldRequestNothingWhenAPageLeavesItsEndOrIdentityUnanswered() {
        stubResolvedMr();
        GraphQlClient.RequestSpec spec = discussionsSpec();
        ClientGraphQlResponse missingPage = discussionsResponse(List.of(), null, null, BOT);
        ClientGraphQlResponse missingIdentity = discussionsResponse(List.of(), false, null, null);
        when(spec.execute()).thenReturn(Mono.just(missingPage)).thenReturn(Mono.just(missingIdentity));

        for (int incompletePage = 0; incompletePage < 2; incompletePage++) {
            assertThatThrownBy(() -> channel.postImmutablePackage(target(), List.of(item("fix", "result:a:0")), fence))
                    .isInstanceOf(FeedbackDeliveryException.class);
        }
        assertThat(fence.attempts).isEmpty();
        assertThat(rest.calls()).isEmpty();
    }

    @Test
    void shouldReportEveryNoteUnsentWithoutRequestingWhenTheRateLimitIsCritical() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(true);

        CopyWriteResult result = channel.postImmutablePackage(target(), List.of(item("fix", "result:a:0")), fence);

        assertThat(result.receipts()).singleElement().satisfies(receipt -> {
            assertThat(receipt.acknowledged()).isFalse();
            assertThat(receipt.writeMayHaveStarted()).isFalse();
        });
        assertThat(fence.attempts).isEmpty();
    }

    @Test
    void shouldPostEachNoteAsAnOverviewThreadLinkingToTheReviewedCommitFencedBeforeItsCreate() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(WITHOUT_CITED_LINE);
        rest.createAsAsked(987654, "DiscussionNote", 1);
        rest.createAsAsked(987654, "DiscussionNote", 1);
        LineNote second = item("second", "observation:b:0");

        CopyWriteResult result = channel.postImmutablePackage(
                target(), List.of(item("/approve once fixed", "result:a:0"), second), fence);

        assertThat(fence.attempts)
                .containsExactly(
                        List.of(ScmFeedbackFixtures.copyId("result:a:0").toString()),
                        List.of(ScmFeedbackFixtures.copyId("observation:b:0").toString()));
        assertThat(fence.completedSeen.get(1)).singleElement().satisfies(receipt -> {
            assertThat(receipt.copyId()).isEqualTo(ScmFeedbackFixtures.copyId("result:a:0"));
            assertThat(receipt.externalRef()).isEqualTo("gid://gitlab/DiscussionNote/987654");
        });
        assertThat(result.receipts()).allSatisfy(receipt -> {
            assertThat(receipt.disposition()).isEqualTo(CopyDisposition.POSTED);
            assertThat(receipt.actualKind()).isEqualTo(CopyForm.FILE_NOTE);
            assertThat(receipt.externalUrl()).isEqualTo(NOTE_URL);
        });
        assertThat(rest.calls()).allSatisfy(call -> assertThat(call.json()).doesNotContainKey("position"));
        assertThat(bodies().getFirst()).contains("`/approve`", MARKER, "hephaestus-diff-note-ck=result:a:0");
        assertThat(bodies().getLast())
                .isEqualTo("**[View code at line 10]("
                        + SERVER
                        + "/group/project/-/blob/"
                        + REVIEWED
                        + "/src/Foo.java#L10)**\n\n"
                        + posted(second));
    }

    @Test
    void shouldLinkARangeInANestedNamespaceAtTheReviewedCommitWithItsPathEncoded() {
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(WITHOUT_CITED_LINE);
        rest.createAsAsked(987654, "DiscussionNote", 1);
        LineNote ranged = ScmFeedbackFixtures.line(
                ScmFeedbackFixtures.range("docs/My Notes (draft).md", 3, 7), "fix", MARKER, "result:a:0");

        channel.postImmutablePackage(
                ScmFeedbackFixtures.target(
                        new IntegrationRef(IntegrationKind.GITLAB, 1L, null), "group/sub.group/project!42", REVIEWED),
                List.of(ranged),
                fence);

        assertThat(rest.calls()).singleElement().satisfies(call -> {
            assertThat(call.path())
                    .isEqualTo("/api/v4/projects/group%2Fsub.group%2Fproject/merge_requests/42/discussions");
            assertThat(call.json().get("body"))
                    .isEqualTo("**[View code at lines 3–7]("
                            + SERVER
                            + "/group/sub.group/project/-/blob/"
                            + REVIEWED
                            + "/docs/My%20Notes%20%28draft%29.md#L3-7)**\n\n"
                            + posted(ranged));
        });
    }

    @Test
    void shouldKeepAHostileFileNameInsideTheEncodedAddressAndOutOfTheCommentText() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(WITHOUT_CITED_LINE);
        rest.createAsAsked(987654, "DiscussionNote", 1);
        LineNote hostile = ScmFeedbackFixtures.line(
                ScmFeedbackFixtures.anchor("docs/`a]b`\n\n@all.md", 10, null), "fix", MARKER, "result:a:0");

        CopyWriteResult result = channel.postImmutablePackage(target(), List.of(hostile), fence);

        assertThat(bodies())
                .containsExactly("**[View code at line 10]("
                        + SERVER
                        + "/group/project/-/blob/"
                        + REVIEWED
                        + "/docs/%60a%5Db%60%0A%0A%40all.md#L10)**\n\n"
                        + posted(hostile));
        assertThat(result.receipts())
                .singleElement()
                .satisfies(receipt -> assertThat(receipt.acknowledged()).isTrue());
    }

    @Test
    void shouldLinkOldSideNotesToTheirCapturedBase() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(WITHOUT_CITED_LINE);
        rest.createAsAsked(11, "DiscussionNote", 1);
        rest.createAsAsked(12, "DiscussionNote", 1);
        List<LineNote> oldSide = List.of(
                ScmFeedbackFixtures.line(
                        new FeedbackAnchor("src/Foo.java", null, 10, CitationSide.OLD), "fix", MARKER, "result:a:0"),
                ScmFeedbackFixtures.line(
                        new FeedbackAnchor("src/Foo.java", 11, 12, CitationSide.OLD),
                        "fix",
                        MARKER,
                        "observation:b:0"));

        CopyWriteResult result = channel.postImmutablePackage(target(), oldSide, fence);

        assertThat(result.receipts()).hasSize(2).allSatisfy(receipt -> {
            assertThat(receipt.acknowledged()).isTrue();
            assertThat(receipt.writeMayHaveStarted()).isTrue();
        });
        assertThat(bodies())
                .hasSize(2)
                .allSatisfy(body -> assertThat(body)
                        .contains("/-/blob/" + "a".repeat(40) + "/src/Foo.java")
                        .doesNotContain("/-/blob/" + REVIEWED));
        assertThat(fence.attempts).hasSize(2);
    }

    @Test
    void shouldRequestNothingWithoutAReviewedCommitToLinkTo() {
        CopyWriteResult unpinned = postAt(null);
        assertThatThrownBy(() -> postAt("main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Git object id");

        assertThat(List.of(unpinned))
                .allSatisfy(result -> assertThat(result.receipts())
                        .singleElement()
                        .satisfies(receipt ->
                                assertThat(receipt.writeMayHaveStarted()).isFalse()));
        assertThat(fence.attempts).isEmpty();
    }

    private CopyWriteResult postAt(@Nullable String revision) {
        return channel.postImmutablePackage(
                ScmFeedbackFixtures.target(
                        new IntegrationRef(IntegrationKind.GITLAB, 1L, null), "group/project!42", revision),
                List.of(item("fix", "result:a:0")),
                fence);
    }

    @Test
    void shouldReadItsOwnLocationCommentBackWithItsPlacementAndUrlWithoutPostingAgain() {
        stubResolvedMr();
        LineNote kept = item("fix", "result:a:0");
        stubDiscussions(
                List.of(discussion("gid://Disc/A", List.of(note("gid://gitlab/Note/A", located(kept), BOT)))), BOT);

        List<CopyReceipt> found = Objects.requireNonNull(channel.findPosted(target(), List.of(kept)));
        CopyWriteResult result = channel.postImmutablePackage(target(), List.of(kept), fence);

        assertThat(found).singleElement().satisfies(receipt -> {
            assertThat(receipt.disposition()).isEqualTo(CopyDisposition.PRESERVED_EXISTING);
            assertThat(receipt.actualKind()).isEqualTo(CopyForm.FILE_NOTE);
            assertThat(receipt.externalRef()).isEqualTo("gid://gitlab/Note/A");
            assertThat(receipt.externalUrl()).isEqualTo(NOTE_URL);
        });
        assertThat(result.receipts()).containsExactlyElementsOf(found);
        assertThat(fence.attempts).isEmpty();
        assertThat(rest.calls()).isEmpty();
    }

    @Test
    void shouldNeitherAcknowledgeNorCreateNextToItsOwnCommentWhoseAnswerLeftOutThePosition() {
        stubResolvedMr();
        LineNote kept = item("fix", "result:a:0");
        Map<String, Object> partial = note("gid://gitlab/Note/A", located(kept), BOT);
        partial.remove("position");
        stubDiscussions(List.of(discussion("gid://Disc/A", List.of(partial))), BOT);

        List<CopyReceipt> found = channel.findPosted(target(), List.of(kept));
        CopyWriteResult result = channel.postImmutablePackage(target(), List.of(kept), fence);

        assertThat(found).isEmpty();
        assertThat(result.receipts())
                .singleElement()
                .satisfies(receipt -> assertThat(receipt.acknowledged()).isFalse());
        assertThat(fence.attempts).isEmpty();
        assertThat(rest.calls()).isEmpty();
    }

    @Test
    void shouldKeepAKnownCopyButRequestNothingNewOnceTheFenceRefuses() {
        stubResolvedMr();
        LineNote kept = item("fix-a", "result:a:0");
        LineNote owed = item("fix-b", "observation:b:0");
        stubDiscussions(
                List.of(discussion("gid://Disc/A", List.of(note("gid://gitlab/Note/A", located(kept), BOT)))), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(WITHOUT_CITED_LINE);
        fence.acceptOnly = 0;

        CopyWriteResult result = channel.postImmutablePackage(target(), List.of(owed, kept), fence);

        assertThat(fence.attempts)
                .containsExactly(
                        List.of(ScmFeedbackFixtures.copyId("observation:b:0").toString()));
        assertThat(result.receipts())
                .extracting(CopyReceipt::copyId, CopyReceipt::acknowledged, CopyReceipt::writeMayHaveStarted)
                .containsExactly(
                        tuple(ScmFeedbackFixtures.copyId("observation:b:0"), false, false),
                        tuple(ScmFeedbackFixtures.copyId("result:a:0"), true, null));
        assertThat(rest.calls()).isEmpty();
    }

    @Test
    void shouldPreserveAnEarlierNativeDiffNoteThatIsResolvedOrRepliedToWithoutEverWritingToIt() {
        stubResolvedMr();
        LineNote kept = item("fix", "result:a:0");
        Map<String, Object> disc = discussion(
                "gid://Disc/OLD",
                List.of(
                        diffNote("gid://Note/OLD", posted(kept), BOT, 10, REVIEWED),
                        note("gid://Note/H", "Done!", PERSON)));
        disc.put("resolved", true);
        stubDiscussions(List.of(disc), BOT);

        CopyWriteResult result = channel.postImmutablePackage(target(), List.of(kept), fence);

        assertThat(result.receipts()).singleElement().satisfies(receipt -> {
            assertThat(receipt.disposition()).isEqualTo(CopyDisposition.PRESERVED_EXISTING);
            assertThat(receipt.actualKind()).isEqualTo(CopyForm.LINE_NOTE);
            assertThat(receipt.externalRef()).isEqualTo("gid://Note/OLD");
            assertThat(receipt.externalUrl()).isEqualTo(NOTE_URL);
        });
        assertThat(fence.attempts).isEmpty();
        assertThat(rest.calls()).isEmpty();
    }

    @Test
    void shouldReadAnEarlierFallbackCommentBackAsALocationComment() {
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        LineNote kept = item("fix", "result:a:0");
        stubDiscussions(
                List.of(discussion(
                        "gid://Disc/F",
                        List.of(note("gid://Note/F", "**`src/Foo.java:10`**\n\n" + posted(kept), BOT)))),
                BOT);

        assertThat(channel.findPosted(target(), List.of(kept))).singleElement().satisfies(receipt -> {
            assertThat(receipt.externalRef()).isEqualTo("gid://Note/F");
            assertThat(receipt.actualKind()).isEqualTo(CopyForm.FILE_NOTE);
        });
    }

    @Test
    void shouldNeitherAcknowledgeNorCreateNextToACopyThatIsNotExactlyItsOwn() {
        stubResolvedMr();
        LineNote a = item("fix-a", "result:a:0");
        LineNote b = item("fix-b", "observation:b:0");
        LineNote c = item("fix-c", "observation:c:0");
        LineNote d = item("fix-d", "observation:d:0");
        LineNote e = item("fix-e", "observation:e:0");
        LineNote f = item("fix-f", "observation:f:0");
        stubDiscussions(
                List.of(
                        discussion("gid://Disc/1", List.of(diffNote("gid://Note/1", posted(a), PERSON, 10, REVIEWED))),
                        discussion(
                                "gid://Disc/2",
                                List.of(diffNote("gid://Note/2", posted(b) + " edited", BOT, 10, REVIEWED))),
                        discussion("gid://Disc/3", List.of(diffNote("gid://Note/3", posted(c), BOT, 11, REVIEWED))),
                        discussion("gid://Disc/4", List.of(diffNote("gid://Note/4", posted(d), BOT, 10, "older"))),
                        discussion(
                                "gid://Disc/5",
                                List.of(note("gid://Note/5", located(e).replace(REVIEWED, "older"), BOT))),
                        discussion("gid://Disc/6", List.of(note("gid://Note/6", located(f), PERSON)))),
                BOT);

        CopyWriteResult result = channel.postImmutablePackage(target(), List.of(a, b, c, d, e, f), fence);

        assertThat(result.receipts())
                .allSatisfy(receipt -> assertThat(receipt.acknowledged()).isFalse());
        assertThat(fence.attempts).isEmpty();
        assertThat(rest.calls()).isEmpty();
        assertThat(channel.findPosted(target(), List.of(a, b, c, d, e, f))).isEmpty();
    }

    @Test
    void shouldChooseTheAuthoredOriginalOverAHumanReplyCopyingItsMarker() {
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        LineNote wanted = item("fix", "result:a:0");
        stubDiscussions(
                List.of(discussion(
                        "gid://Disc/A",
                        List.of(
                                note("gid://Note/A", located(wanted), BOT),
                                note("gid://Note/COPY", located(wanted), PERSON)))),
                BOT);

        assertThat(channel.findPosted(target(), List.of(wanted)))
                .singleElement()
                .satisfies(receipt -> assertThat(receipt.externalRef()).isEqualTo("gid://Note/A"));
    }

    @Test
    void shouldReadOnlyTheBotAuthoredCopyWithItsExpectedMarkerAndKey() {
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        LineNote expectedCopy = ScmFeedbackFixtures.line(
                ScmFeedbackFixtures.anchor("src/Foo.java", 10, null),
                "fix",
                "<!-- hephaestus-diff-note -->",
                "result:a:0");
        stubDiscussions(
                List.of(discussion(
                        "gid://Disc/1",
                        List.of(
                                diffNote("gid://Note/HUMAN", posted(expectedCopy), PERSON, 10, REVIEWED),
                                diffNote("gid://Note/7", posted(expectedCopy), BOT, 10, REVIEWED)))),
                BOT);

        assertThat(channel.findPosted(target(), List.of(expectedCopy)))
                .singleElement()
                .satisfies(receipt -> assertThat(receipt.externalRef()).isEqualTo("gid://Note/7"));
    }

    // The address and credential are resolved before the copy is fenced, so failing to resolve them requests nothing.
    @Test
    void shouldRequestNothingWhenTheAddressOrCredentialCannotBeResolvedLocally() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(GitLabDiffPositionsTest.VERSION);
        var selected = ScmFeedbackFixtures.line(
                new FeedbackAnchor("new.java", null, 21, CitationSide.NEW), "fix", MARKER, "selected");

        when(tokenService.getAccessToken(1L))
                .thenThrow(new IllegalStateException("The GitLab token could not be refreshed"));
        var unrefreshed = channel.postImmutablePackage(target(), List.of(selected), fence);
        // A valid credential is never reached: the address is resolved first.
        lenient().doReturn("glpat-test").when(tokenService).getAccessToken(1L);
        when(tokenService.resolveServerUrl(1L)).thenReturn("https://gitlab example.com");
        var unaddressable = channel.postImmutablePackage(target(), List.of(selected), fence);

        assertThat(List.of(unrefreshed, unaddressable))
                .allSatisfy(result -> assertThat(result.receipts())
                        .singleElement()
                        .satisfies(receipt -> {
                            assertThat(receipt.acknowledged()).isFalse();
                            assertThat(receipt.writeMayHaveStarted()).isFalse();
                        }));
        assertThat(fence.attempts).isEmpty();
        assertThat(rest.calls()).isEmpty();
    }

    @Test
    void shouldKeepAnEarlierPostedCopyWhenALaterCredentialCannotBeResolved() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(WITHOUT_CITED_LINE);
        when(tokenService.getAccessToken(1L))
                .thenReturn("glpat-test")
                .thenThrow(new IllegalStateException("The GitLab token could not be refreshed"));
        rest.createAsAsked(987654, "DiscussionNote", 1);

        CopyWriteResult result = channel.postImmutablePackage(
                target(), List.of(item("fix-a", "result:a:0"), item("fix-b", "observation:b:0")), fence);

        assertThat(result.receipts()).hasSize(2);
        assertThat(result.receipts().getFirst()).satisfies(receipt -> {
            assertThat(receipt.disposition()).isEqualTo(CopyDisposition.POSTED);
            assertThat(receipt.externalRef()).isEqualTo("gid://gitlab/DiscussionNote/987654");
        });
        assertThat(result.receipts().getLast().writeMayHaveStarted()).isFalse();
        assertThat(fence.attempts)
                .containsExactly(
                        List.of(ScmFeedbackFixtures.copyId("result:a:0").toString()));
        assertThat(rest.calls()).hasSize(1);
    }

    @Test
    void shouldReportALostCreateAsUnconfirmedAndStopAtARateLimitLeavingTheRestUnsent() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(WITHOUT_CITED_LINE);
        rest.answer(429, Map.of("message", "429 Too Many Requests"));

        CopyWriteResult result = channel.postImmutablePackage(
                target(), List.of(item("fix-a", "result:a:0"), item("fix-b", "observation:b:0")), fence);

        assertThat(fence.attempts)
                .containsExactly(
                        List.of(ScmFeedbackFixtures.copyId("result:a:0").toString()));
        assertThat(result.receipts())
                .extracting(CopyReceipt::writeMayHaveStarted)
                .containsExactly(true, false);
        assertThat(rest.calls()).hasSize(1);
    }

    @Test
    void shouldReportACreateAnsweredWithoutANoteAsUnconfirmed() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(WITHOUT_CITED_LINE);
        rest.answer(201, Map.of("id", THREAD, "notes", List.of()));

        CopyWriteResult result = channel.postImmutablePackage(target(), List.of(item("fix", "result:a:0")), fence);

        assertThat(result.receipts())
                .singleElement()
                .satisfies(receipt -> assertThat(receipt.unconfirmed()).isTrue());
        assertThat(fence.attempts).hasSize(1);
    }

    // A placed range verifies only through GitLab's record of both ends, beside replies and resolution.
    @Test
    void shouldPreserveAPlacedRangeOnlyWhenGitLabHoldsBothOfItsEnds() {
        stubResolvedMr();
        LineNote ranged =
                ScmFeedbackFixtures.line(ScmFeedbackFixtures.range("src/Foo.java", 8, 10), "fix", MARKER, "result:a:0");
        Map<String, Object> thread = discussion(
                "gid://gitlab/Discussion/" + THREAD,
                List.of(
                        diffNote("gid://gitlab/DiffNote/987654", placed(ranged), BOT, 10, REVIEWED),
                        note("gid://gitlab/DiffNote/987655", "Thanks, fixing it.", PERSON)));
        thread.put("resolved", true);
        stubDiscussions(List.of(thread), BOT);
        rest.answer(200, restThread(range(8, 10)));

        var result = channel.postImmutablePackage(target(), List.of(ranged), fence);

        assertThat(result.receipts()).singleElement().satisfies(receipt -> {
            assertThat(receipt.disposition()).isEqualTo(CopyDisposition.PRESERVED_EXISTING);
            assertThat(receipt.externalRef()).isEqualTo("gid://gitlab/DiffNote/987654");
            assertThat(receipt.actualKind()).isEqualTo(CopyForm.LINE_NOTE);
        });
        assertThat(rest.calls()).singleElement().satisfies(call -> {
            assertThat(call.method()).isEqualTo(HttpMethod.GET);
            assertThat(call.path()).isEqualTo(DISCUSSIONS + "/" + THREAD);
        });
        assertThat(fence.attempts).isEmpty();
    }

    // A newly placed range that GitLab does not hold is never verified, and nothing is written next to it.
    @Test
    void shouldNeitherVerifyNorRecreateAPlacedRangeGitLabDoesNotHold() {
        stubResolvedMr();
        LineNote ranged =
                ScmFeedbackFixtures.line(ScmFeedbackFixtures.range("src/Foo.java", 8, 10), "fix", MARKER, "result:a:0");
        stubDiscussions(
                List.of(discussion(
                        "gid://gitlab/Discussion/" + THREAD,
                        List.of(diffNote("gid://gitlab/DiffNote/987654", placed(ranged), BOT, 10, REVIEWED)))),
                BOT);
        rest.answer(200, restThread(null));
        rest.answer(200, restThread(range(9, 10)));

        for (int answer = 0; answer < 2; answer++)
            assertThat(channel.postImmutablePackage(target(), List.of(ranged), fence)
                            .receipts())
                    .singleElement()
                    .satisfies(receipt -> {
                        assertThat(receipt.acknowledged()).isFalse();
                        assertThat(receipt.writeMayHaveStarted()).isFalse();
                    });

        assertThat(rest.calls())
                .hasSize(2)
                .allSatisfy(call -> assertThat(call.method()).isEqualTo(HttpMethod.GET));
        assertThat(fence.attempts).isEmpty();
    }

    @Test
    void shouldStopBeforeAnyWriteWhenAPlacedRangeCannotBeRead() {
        stubResolvedMr();
        LineNote ranged =
                ScmFeedbackFixtures.line(ScmFeedbackFixtures.range("src/Foo.java", 8, 10), "fix", MARKER, "result:a:0");
        stubDiscussions(
                List.of(discussion(
                        "gid://gitlab/Discussion/" + THREAD,
                        List.of(diffNote("gid://gitlab/DiffNote/987654", placed(ranged), BOT, 10, REVIEWED)))),
                BOT);
        rest.answer(500, Map.of("message", "500 Internal Server Error"));
        rest.answer(200, Map.of("id", THREAD, "notes", List.of()));

        assertThatThrownBy(() -> channel.postImmutablePackage(target(), List.of(ranged), fence))
                .isInstanceOf(FeedbackDeliveryException.class);
        assertThat(channel.findPosted(target(), List.of(ranged))).isNull();
        assertThat(fence.attempts).isEmpty();
        assertThat(rest.calls())
                .hasSize(2)
                .allSatisfy(call -> assertThat(call.method()).isEqualTo(HttpMethod.GET));
    }

    // Its exact pre-range body proves an earlier end-line copy, so it is kept and never posted again.
    @Test
    void shouldPreserveAnEarlierEndLineCopyOfARangeWithoutReadingItsRange() {
        stubResolvedMr();
        LineNote ranged =
                ScmFeedbackFixtures.line(ScmFeedbackFixtures.range("src/Foo.java", 8, 10), "fix", MARKER, "result:a:0");
        stubDiscussions(
                List.of(discussion(
                        "gid://gitlab/Discussion/" + THREAD,
                        List.of(diffNote("gid://gitlab/DiffNote/987654", posted(ranged), BOT, 10, REVIEWED)))),
                BOT);

        assertThat(channel.postImmutablePackage(target(), List.of(ranged), fence)
                        .receipts())
                .singleElement()
                .satisfies(receipt -> {
                    assertThat(receipt.disposition()).isEqualTo(CopyDisposition.PRESERVED_EXISTING);
                    assertThat(receipt.actualKind()).isEqualTo(CopyForm.LINE_NOTE);
                });
        assertThat(rest.calls()).isEmpty();
        assertThat(fence.attempts).isEmpty();
    }

    @Test
    void shouldRequestNothingWhenTheFenceRefusesOrEgressIsSilenced() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        when(diffVersions.read(eq(1L), any(), any())).thenReturn(WITHOUT_CITED_LINE);
        fence.acceptOnly = 0;

        CopyWriteResult refused = channel.postImmutablePackage(target(), List.of(item("fix", "result:a:0")), fence);

        assertThat(refused.receipts())
                .singleElement()
                .satisfies(receipt -> assertThat(receipt.writeMayHaveStarted()).isFalse());
        doThrow(new OutboundEgressSuppressedException("gitlab.post-inline-feedback"))
                .when(egressGuard)
                .requireDeliveryAllowed(anyString());
        CopyWriteResult silenced =
                channel.postImmutablePackage(target(), List.of(item("fix", "observation:b:0")), fence);

        assertThat(silenced.withheldCopyIds()).isNotEmpty();
        assertThat(silenced.withheldCopyIds()).containsExactly(ScmFeedbackFixtures.copyId("observation:b:0"));
        assertThat(rest.calls()).isEmpty();
    }

    @Test
    void findPostedIsInconclusiveWhenADiscussionHasMoreNotesThanOneLookupReads() {
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        Map<String, Object> crowded = discussion("gid://gitlab/Discussion/1", List.of());
        crowded.put("notes", Map.of("nodes", List.of(), "pageInfo", Map.of("hasNextPage", true)));
        stubDiscussions(List.of(crowded), BOT);

        assertThat(channel.findPosted(target(), List.of(item("fix", "result:a:0"))))
                .isNull();
    }

    private static LineNote item(String body, String key) {
        return ScmFeedbackFixtures.line(ScmFeedbackFixtures.anchor("src/Foo.java", 10, null), body, MARKER, key);
    }

    private static String posted(LineNote item) {
        return item.body() + "\n" + item.writeMarker() + "\n<!-- hephaestus-diff-note-ck=" + item.writeKey() + " -->";
    }

    private static String placed(LineNote item) {
        return item.body() + "\n" + item.writeMarker() + "\n" + GitLabLineNoteWriter.RANGE_PLACEMENT
                + "\n<!-- hephaestus-diff-note-ck=" + item.writeKey() + " -->";
    }

    private List<String> bodies() {
        return rest.calls().stream()
                .map(call -> (String) call.json().get("body"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstNote(Map<String, Object> discussion, String field, Object value) {
        ((List<Map<String, Object>>) Objects.requireNonNull(discussion.get("notes")))
                .getFirst()
                .put(field, value);
        return discussion;
    }

    private static Map<String, Object> range(int start, int end) {
        Map<String, Object> first = new HashMap<>();
        first.put("line_code", "3f587915889f8427143aeb5ce3b65cbba4a42d01_" + start + "_" + start);
        first.put("type", "new");
        first.put("old_line", null);
        first.put("new_line", start);
        Map<String, Object> last = new HashMap<>(first);
        last.put("line_code", "3f587915889f8427143aeb5ce3b65cbba4a42d01_" + end + "_" + end);
        last.put("new_line", end);
        return Map.of("start", first, "end", last);
    }

    // GitLab's own record of the thread, as GET .../discussions/:id answers it, with a human reply below.
    private static Map<String, Object> restThread(@Nullable Map<String, Object> range) {
        Map<String, Object> position = new HashMap<>();
        position.put("position_type", "text");
        position.put("new_path", "src/Foo.java");
        position.put("new_line", 10);
        position.put("line_range", range);
        Map<String, Object> bot = new HashMap<>();
        bot.put("id", 987654);
        bot.put("type", "DiffNote");
        bot.put("position", position);
        bot.put("resolved", true);
        return Map.of(
                "id",
                THREAD,
                "notes",
                List.of(
                        bot,
                        Map.of("id", 987655, "type", "DiffNote", "body", "Thanks, fixing it.", "position", position)));
    }

    private static String located(LineNote item) {
        return "**[View code at line 10]("
                + SERVER
                + "/group/project/-/blob/"
                + REVIEWED
                + "/src/Foo.java#L10)**\n\n"
                + posted(item);
    }

    private void stubResolvedMr() {
        when(gitLabProvider.forScope(1L)).thenReturn(client);
    }

    private GraphQlClient.RequestSpec discussionsSpec() {
        GraphQlClient.RequestSpec spec = mock(GraphQlClient.RequestSpec.class);
        when(client.documentName("GetMergeRequestDiscussions")).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);
        return spec;
    }

    private void stubDiscussions(List<Map<String, Object>> discussions, @Nullable String currentUserId) {
        GraphQlClient.RequestSpec spec = discussionsSpec();
        ClientGraphQlResponse response = discussionsResponse(discussions, false, null, currentUserId);
        when(spec.execute()).thenReturn(Mono.just(response));
    }

    private static ClientGraphQlResponse discussionsResponse(
            List<Map<String, Object>> discussions,
            @Nullable Boolean hasNextPage,
            @Nullable String endCursor,
            @Nullable String currentUserId) {
        Map<String, Object> pageInfo = new HashMap<>();
        pageInfo.put("hasNextPage", hasNextPage);
        pageInfo.put("endCursor", endCursor);
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        stubField(
                response,
                "GetMergeRequestDiscussions",
                "project.mergeRequest.discussions",
                Map.of("nodes", discussions, "pageInfo", pageInfo));
        ClientResponseField userField = mock(ClientResponseField.class);
        lenient().when(response.field("currentUser.id")).thenReturn(userField);
        lenient().when(userField.getValue()).thenReturn(currentUserId);
        return response;
    }

    private static void stubField(ClientGraphQlResponse response, String document, String path, Object value) {
        assertVendorCouldReturn(GITLAB, document, path, value);
        ClientResponseField field = mock(ClientResponseField.class);
        lenient().when(response.field(path)).thenReturn(field);
        lenient().when(field.getValue()).thenReturn(value);
    }

    private static Map<String, Object> note(String id, String body, String authorId) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", id);
        n.put("url", NOTE_URL);
        n.put("body", body);
        n.put("system", false);
        n.put("author", Map.of("id", authorId));
        // An ordinary comment, as the response answers it: the selected position is explicitly
        // null.
        n.put("position", null);
        return n;
    }

    private static Map<String, Object> diffNote(String id, String body, String authorId, int line, String headSha) {
        Map<String, Object> n = note(id, body, authorId);
        n.put(
                "position",
                Map.of(
                        "newPath",
                        "src/Foo.java",
                        "newLine",
                        line,
                        "diffRefs",
                        Map.of("baseSha", "a".repeat(40), "headSha", headSha, "startSha", "b".repeat(40))));
        return n;
    }

    private static Map<String, Object> discussion(String id, List<Map<String, Object>> notes) {
        Map<String, Object> disc = new HashMap<>();
        disc.put("id", id);
        disc.put("notes", Map.of("nodes", new ArrayList<>(notes), "pageInfo", Map.of("hasNextPage", false)));
        return disc;
    }

    private static FeedbackTarget target() {
        return ScmFeedbackFixtures.target(
                new IntegrationRef(IntegrationKind.GITLAB, 1L, null), "group/project!42", REVIEWED);
    }

    private static final class RecordingFence implements CopyWriteFence {

        final List<List<String>> attempts = new ArrayList<>();
        final List<List<CopyReceipt>> completedSeen = new ArrayList<>();
        int acceptOnly = Integer.MAX_VALUE;

        @Override
        public boolean beforeCreate(List<UUID> attempting, List<CopyReceipt> completed) {
            attempts.add(attempting.stream().map(String::valueOf).toList());
            completedSeen.add(List.copyOf(completed));
            return attempts.size() <= acceptOnly;
        }
    }
}
