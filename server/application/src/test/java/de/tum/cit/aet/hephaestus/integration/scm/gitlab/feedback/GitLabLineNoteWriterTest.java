package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static de.tum.cit.aet.hephaestus.integration.scm.GraphQlResponseStubValidator.Vendor.GITLAB;
import static de.tum.cit.aet.hephaestus.integration.scm.GraphQlResponseStubValidator.assertVendorCouldReturn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGuard;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressSuppressedException;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyDisposition;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyForm;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyReadback;
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
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabTokenService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitLabMrResolver.MrInfo;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

class GitLabLineNoteWriterTest extends BaseUnitTest {

    private static final String MARKER = "<!-- hephaestus-review-package:job-1 -->";
    private static final String REVIEWED = "3f2b9c1d0e8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c";
    private static final String SERVER = "https://gitlab.example.com";
    private static final String BOT = "gid://gitlab/User/1";
    private static final String PERSON = "gid://gitlab/User/2";
    private static final String NOTE_URL = "https://gitlab.example.com/group/project/-/merge_requests/42#note_987654";

    @Mock
    private GitLabGraphQlClientProvider gitLabProvider;

    @Mock
    private GitLabMrResolver mrResolver;

    @Mock
    private GitLabTokenService tokenService;

    @Mock
    private OutboundEgressGuard egressGuard;

    private GitLabLineNoteWriter channel;
    private HttpGraphQlClient client;
    private final RecordingFence fence = new RecordingFence();

    @BeforeEach
    void setUp() {
        channel = new GitLabLineNoteWriter(
                gitLabProvider, mrResolver, tokenService, egressGuard, ScmFeedbackFixtures.resolver());
        client = mock(HttpGraphQlClient.class);
        lenient().when(tokenService.resolveServerUrl(1L)).thenReturn(SERVER);
    }

    @Test
    void shouldRequestNothingForAnEmptyPackage() {
        assertThat(channel.postImmutablePackage(target(), List.of(), CopyReadback.AUTHORED, fence)
                        .receipts())
                .isEmpty();
        assertThat(fence.attempts).isEmpty();
    }

    @Test
    void shouldRequestNothingWhenTheScanCannotBeRead() {
        when(mrResolver.resolve(1L, "group/project", 42))
                .thenReturn(new MrInfo("gid://gitlab/MR/42", "base", "head", "start"));
        when(gitLabProvider.forScope(1L)).thenThrow(new RuntimeException("provider unavailable"));

        assertThatThrownBy(() -> channel.postImmutablePackage(
                        target(), List.of(item("fix", "observation:a:0")), CopyReadback.AUTHORED, fence))
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

        assertThatThrownBy(() -> channel.postImmutablePackage(
                        target(), List.of(item("fix", "observation:a:0")), CopyReadback.AUTHORED, fence))
                .isInstanceOf(FeedbackDeliveryException.class);
        verify(spec, times(50)).execute();
        verify(client, never()).documentName("CreateMergeRequestNote");
        assertThat(fence.attempts).isEmpty();
    }

    @Test
    void shouldRequestNothingWhenAPageLeavesItsEndOrIdentityUnanswered() {
        stubResolvedMr();
        GraphQlClient.RequestSpec spec = discussionsSpec();
        ClientGraphQlResponse missingPage = discussionsResponse(List.of(), null, null, BOT);
        ClientGraphQlResponse missingIdentity = discussionsResponse(List.of(), false, null, null);
        when(spec.execute()).thenReturn(Mono.just(missingPage)).thenReturn(Mono.just(missingIdentity));

        for (CopyReadback readback : List.of(CopyReadback.AUTHORED, CopyReadback.SHARED)) {
            assertThatThrownBy(() -> channel.postImmutablePackage(
                            target(), List.of(item("fix", "observation:a:0")), readback, fence))
                    .isInstanceOf(FeedbackDeliveryException.class);
        }
        assertThat(fence.attempts).isEmpty();
        verify(client, never()).documentName("CreateMergeRequestNote");
    }

    @Test
    void shouldReportEveryNoteUnsentWithoutRequestingWhenTheRateLimitIsCritical() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(true);

        CopyWriteResult result = channel.postImmutablePackage(
                target(), List.of(item("fix", "observation:a:0")), CopyReadback.AUTHORED, fence);

        assertThat(result.receipts()).singleElement().satisfies(signal -> {
            assertThat(signal.acknowledged()).isFalse();
            assertThat(signal.writeMayHaveStarted()).isFalse();
        });
        assertThat(fence.attempts).isEmpty();
    }

    @Test
    void shouldPostEachNoteAsAnOrdinaryCommentLinkingToTheReviewedCommitFencedBeforeItsCreate() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        GraphQlClient.RequestSpec spec = createSpec();
        ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
        when(spec.variable(eq("body"), bodies.capture())).thenReturn(spec);
        ClientGraphQlResponse response = createResponse(created("gid://gitlab/Note/NEW"), List.of());
        when(spec.execute()).thenReturn(Mono.just(response));
        LineNote second = item("second", "observation:b:0");

        CopyWriteResult result = channel.postImmutablePackage(
                target(),
                List.of(item("/approve once fixed", "observation:a:0"), second),
                CopyReadback.AUTHORED,
                fence);

        assertThat(fence.attempts)
                .containsExactly(
                        List.of(ScmFeedbackFixtures.copyId("observation:a:0").toString()),
                        List.of(ScmFeedbackFixtures.copyId("observation:b:0").toString()));
        assertThat(fence.completedSeen.get(1)).singleElement().satisfies(signal -> {
            assertThat(signal.copyId()).isEqualTo(ScmFeedbackFixtures.copyId("observation:a:0"));
            assertThat(signal.externalRef()).isEqualTo("gid://gitlab/Note/NEW");
        });
        assertThat(result.receipts()).allSatisfy(signal -> {
            assertThat(signal.disposition()).isEqualTo(CopyDisposition.POSTED);
            assertThat(signal.actualKind()).isEqualTo(CopyForm.FILE_NOTE);

            assertThat(signal.externalUrl()).isEqualTo(NOTE_URL);
        });
        assertThat(bodies.getAllValues().getFirst())
                .contains("`/approve`", MARKER, "hephaestus-diff-note-ck=observation:a:0");
        assertThat(bodies.getAllValues().getLast())
                .isEqualTo("**[View code at line 10]("
                        + SERVER
                        + "/group/project/-/blob/"
                        + REVIEWED
                        + "/src/Foo.java#L10)**\n\n"
                        + posted(second));
        verify(spec, never()).variable(eq("position"), any());
        verify(client, never()).documentName("CreateDiffNote");
    }

    @Test
    void shouldLinkARangeInANestedNamespaceAtTheReviewedCommitWithItsPathEncoded() {
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(mrResolver.resolve(1L, "group/sub.group/project", 42))
                .thenReturn(new MrInfo("gid://gitlab/MR/42", "base", "head", "start"));
        stubDiscussions(List.of(), BOT);
        GraphQlClient.RequestSpec spec = createSpec();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        when(spec.variable(eq("body"), body.capture())).thenReturn(spec);
        ClientGraphQlResponse response = createResponse(created("gid://gitlab/Note/NEW"), List.of());
        when(spec.execute()).thenReturn(Mono.just(response));
        LineNote ranged = ScmFeedbackFixtures.line(
                ScmFeedbackFixtures.range("docs/My Notes (draft).md", 3, 7), "fix", MARKER, "observation:a:0");

        channel.postImmutablePackage(
                ScmFeedbackFixtures.target(
                        new IntegrationRef(IntegrationKind.GITLAB, 1L, null), "group/sub.group/project!42", REVIEWED),
                List.of(ranged),
                CopyReadback.AUTHORED,
                fence);

        assertThat(body.getValue())
                .isEqualTo("**[View code at lines 3–7]("
                        + SERVER
                        + "/group/sub.group/project/-/blob/"
                        + REVIEWED
                        + "/docs/My%20Notes%20%28draft%29.md#L3-7)**\n\n"
                        + posted(ranged));
    }

    @Test
    void shouldKeepAHostileFileNameInsideTheEncodedAddressAndOutOfTheCommentText() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        GraphQlClient.RequestSpec spec = createSpec();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        when(spec.variable(eq("body"), body.capture())).thenReturn(spec);
        ClientGraphQlResponse response = createResponse(created("gid://gitlab/Note/NEW"), List.of());
        when(spec.execute()).thenReturn(Mono.just(response));
        LineNote hostile = ScmFeedbackFixtures.line(
                ScmFeedbackFixtures.anchor("docs/`a]b`\n\n@all.md", 10, null), "fix", MARKER, "observation:a:0");

        CopyWriteResult result = channel.postImmutablePackage(target(), List.of(hostile), CopyReadback.AUTHORED, fence);

        assertThat(body.getValue())
                .isEqualTo("**[View code at line 10]("
                        + SERVER
                        + "/group/project/-/blob/"
                        + REVIEWED
                        + "/docs/%60a%5Db%60%0A%0A%40all.md#L10)**\n\n"
                        + posted(hostile));
        assertThat(result.receipts())
                .singleElement()
                .satisfies(signal -> assertThat(signal.acknowledged()).isTrue());
    }

    @Test
    void shouldLinkOldSideNotesToTheirCapturedBase() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        var spec = createSpec();
        var bodies = ArgumentCaptor.forClass(String.class);
        when(spec.variable(eq("body"), bodies.capture())).thenReturn(spec);
        var firstResponse = createResponse(created("gid://gitlab/Note/OLD_1"), List.of());
        var secondResponse = createResponse(created("gid://gitlab/Note/OLD_2"), List.of());
        when(spec.execute()).thenReturn(Mono.just(firstResponse)).thenReturn(Mono.just(secondResponse));
        List<LineNote> oldSide = List.of(
                ScmFeedbackFixtures.line(
                        new FeedbackAnchor("src/Foo.java", null, 10, CitationSide.OLD),
                        "fix",
                        MARKER,
                        "observation:a:0"),
                ScmFeedbackFixtures.line(
                        new FeedbackAnchor("src/Foo.java", 11, 12, CitationSide.OLD),
                        "fix",
                        MARKER,
                        "observation:b:0"));

        CopyWriteResult result = channel.postImmutablePackage(target(), oldSide, CopyReadback.AUTHORED, fence);

        assertThat(result.receipts()).hasSize(2).allSatisfy(signal -> {
            assertThat(signal.acknowledged()).isTrue();
            assertThat(signal.writeMayHaveStarted()).isTrue();
        });
        assertThat(bodies.getAllValues())
                .hasSize(2)
                .allSatisfy(body -> assertThat(body)
                        .contains("/-/blob/" + "a".repeat(40) + "/src/Foo.java")
                        .doesNotContain("/-/blob/" + REVIEWED));
        assertThat(fence.attempts).hasSize(2);
        verify(spec, times(2)).execute();
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
                        .satisfies(signal ->
                                assertThat(signal.writeMayHaveStarted()).isFalse()));
        assertThat(fence.attempts).isEmpty();
    }

    private CopyWriteResult postAt(@Nullable String revision) {
        return channel.postImmutablePackage(
                ScmFeedbackFixtures.target(
                        new IntegrationRef(IntegrationKind.GITLAB, 1L, null), "group/project!42", revision),
                List.of(item("fix", "observation:a:0")),
                CopyReadback.AUTHORED,
                fence);
    }

    @Test
    void shouldReadItsOwnLocationCommentBackWithItsPlacementAndUrlWithoutPostingAgain() {
        stubResolvedMr();
        LineNote kept = item("fix", "observation:a:0");
        stubDiscussions(
                List.of(discussion("gid://Disc/A", List.of(note("gid://gitlab/Note/A", located(kept), BOT)))), BOT);

        List<CopyReceipt> found =
                Objects.requireNonNull(channel.findPosted(target(), List.of(kept), CopyReadback.AUTHORED));
        CopyWriteResult result = channel.postImmutablePackage(target(), List.of(kept), CopyReadback.AUTHORED, fence);

        assertThat(found).singleElement().satisfies(signal -> {
            assertThat(signal.disposition()).isEqualTo(CopyDisposition.PRESERVED_EXISTING);
            assertThat(signal.actualKind()).isEqualTo(CopyForm.FILE_NOTE);
            assertThat(signal.externalRef()).isEqualTo("gid://gitlab/Note/A");
            assertThat(signal.externalUrl()).isEqualTo(NOTE_URL);
        });
        assertThat(result.receipts()).containsExactlyElementsOf(found);
        assertThat(fence.attempts).isEmpty();
        verify(client, never()).documentName("CreateMergeRequestNote");
    }

    @Test
    void shouldNeitherAcknowledgeNorCreateNextToItsOwnCommentWhoseAnswerLeftOutThePosition() {
        stubResolvedMr();
        LineNote kept = item("fix", "observation:a:0");
        Map<String, Object> partial = note("gid://gitlab/Note/A", located(kept), BOT);
        partial.remove("position");
        stubDiscussions(List.of(discussion("gid://Disc/A", List.of(partial))), BOT);

        List<CopyReceipt> found = channel.findPosted(target(), List.of(kept), CopyReadback.AUTHORED);
        CopyWriteResult result = channel.postImmutablePackage(target(), List.of(kept), CopyReadback.AUTHORED, fence);

        assertThat(found).isEmpty();
        assertThat(result.receipts())
                .singleElement()
                .satisfies(signal -> assertThat(signal.acknowledged()).isFalse());
        assertThat(fence.attempts).isEmpty();
        verify(client, never()).documentName("CreateMergeRequestNote");
    }

    @Test
    void shouldKeepAKnownCopyButRequestNothingNewOnceTheFenceRefuses() {
        stubResolvedMr();
        LineNote kept = item("fix-a", "observation:a:0");
        LineNote owed = item("fix-b", "observation:b:0");
        stubDiscussions(
                List.of(discussion("gid://Disc/A", List.of(note("gid://gitlab/Note/A", located(kept), BOT)))), BOT);
        fence.acceptOnly = 0;

        CopyWriteResult result =
                channel.postImmutablePackage(target(), List.of(owed, kept), CopyReadback.AUTHORED, fence);

        assertThat(fence.attempts)
                .containsExactly(
                        List.of(ScmFeedbackFixtures.copyId("observation:b:0").toString()));
        assertThat(result.receipts())
                .extracting(CopyReceipt::copyId, CopyReceipt::acknowledged, CopyReceipt::writeMayHaveStarted)
                .containsExactly(
                        tuple(ScmFeedbackFixtures.copyId("observation:b:0"), false, false),
                        tuple(ScmFeedbackFixtures.copyId("observation:a:0"), true, null));
        verify(client, never()).documentName("CreateMergeRequestNote");
    }

    @Test
    void shouldPreserveAnEarlierNativeDiffNoteThatIsResolvedOrRepliedToWithoutEverWritingToIt() {
        stubResolvedMr();
        LineNote kept = item("fix", "observation:a:0");
        Map<String, Object> disc = discussion(
                "gid://Disc/OLD",
                List.of(
                        diffNote("gid://Note/OLD", posted(kept), BOT, 10, REVIEWED),
                        note("gid://Note/H", "Done!", PERSON)));
        disc.put("resolved", true);
        stubDiscussions(List.of(disc), BOT);

        CopyWriteResult result = channel.postImmutablePackage(target(), List.of(kept), CopyReadback.AUTHORED, fence);

        assertThat(result.receipts()).singleElement().satisfies(signal -> {
            assertThat(signal.disposition()).isEqualTo(CopyDisposition.PRESERVED_EXISTING);
            assertThat(signal.actualKind()).isEqualTo(CopyForm.LINE_NOTE);
            assertThat(signal.externalRef()).isEqualTo("gid://Note/OLD");
            assertThat(signal.externalUrl()).isEqualTo(NOTE_URL);
        });
        assertThat(fence.attempts).isEmpty();
        verify(client, never()).documentName("CreateMergeRequestNote");
    }

    @Test
    void shouldReadAnEarlierFallbackCommentBackAsALocationComment() {
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        LineNote kept = item("fix", "observation:a:0");
        stubDiscussions(
                List.of(discussion(
                        "gid://Disc/F",
                        List.of(note("gid://Note/F", "**`src/Foo.java:10`**\n\n" + posted(kept), BOT)))),
                BOT);

        assertThat(channel.findPosted(target(), List.of(kept), CopyReadback.AUTHORED))
                .singleElement()
                .satisfies(signal -> {
                    assertThat(signal.externalRef()).isEqualTo("gid://Note/F");
                    assertThat(signal.actualKind()).isEqualTo(CopyForm.FILE_NOTE);
                });
    }

    @Test
    void shouldNeitherAcknowledgeNorCreateNextToACopyThatIsNotExactlyItsOwn() {
        stubResolvedMr();
        LineNote a = item("fix-a", "observation:a:0");
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

        CopyWriteResult result =
                channel.postImmutablePackage(target(), List.of(a, b, c, d, e, f), CopyReadback.AUTHORED, fence);

        assertThat(result.receipts())
                .allSatisfy(signal -> assertThat(signal.acknowledged()).isFalse());
        assertThat(fence.attempts).isEmpty();
        verify(client, never()).documentName("CreateMergeRequestNote");
        assertThat(channel.findPosted(target(), List.of(a, b, c, d, e, f), CopyReadback.AUTHORED))
                .isEmpty();
    }

    @Test
    void shouldChooseTheAuthoredOriginalOverAHumanReplyCopyingItsMarker() {
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        LineNote wanted = item("fix", "observation:a:0");
        stubDiscussions(
                List.of(discussion(
                        "gid://Disc/A",
                        List.of(
                                note("gid://Note/A", located(wanted), BOT),
                                note("gid://Note/COPY", located(wanted), PERSON)))),
                BOT);

        assertThat(channel.findPosted(target(), List.of(wanted), CopyReadback.AUTHORED))
                .singleElement()
                .satisfies(signal -> assertThat(signal.externalRef()).isEqualTo("gid://Note/A"));
    }

    @Test
    void shouldReadAHistoricalPackageBackOnlyByItsOwnAuthoredExactCopy() {
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        LineNote legacy = ScmFeedbackFixtures.line(
                ScmFeedbackFixtures.anchor("src/Foo.java", 10, null),
                "fix",
                "<!-- hephaestus-diff-note -->",
                "observation:a:0");
        stubDiscussions(
                List.of(discussion(
                        "gid://Disc/1",
                        List.of(
                                diffNote("gid://Note/HUMAN", posted(legacy), PERSON, 10, REVIEWED),
                                diffNote("gid://Note/7", posted(legacy), BOT, 10, REVIEWED)))),
                BOT);

        assertThat(channel.findPosted(target(), List.of(legacy), CopyReadback.SHARED))
                .singleElement()
                .satisfies(signal -> assertThat(signal.externalRef()).isEqualTo("gid://Note/7"));
    }

    @Test
    void shouldReportALostCreateAsUnconfirmedAndStopAtARateLimitLeavingTheRestUnsent() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        GraphQlClient.RequestSpec spec = createSpec();
        when(spec.execute()).thenReturn(Mono.error(new RuntimeException("429 Too Many Requests")));

        CopyWriteResult result = channel.postImmutablePackage(
                target(),
                List.of(item("fix-a", "observation:a:0"), item("fix-b", "observation:b:0")),
                CopyReadback.AUTHORED,
                fence);

        assertThat(fence.attempts)
                .containsExactly(
                        List.of(ScmFeedbackFixtures.copyId("observation:a:0").toString()));
        assertThat(result.receipts())
                .extracting(CopyReceipt::writeMayHaveStarted)
                .containsExactly(true, false);
    }

    @Test
    void shouldReportACreateAnsweredWithoutANoteAsUnconfirmed() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        GraphQlClient.RequestSpec spec = createSpec();
        ClientGraphQlResponse response = createResponse(null, List.of("validation failed"));
        when(spec.execute()).thenReturn(Mono.just(response));

        CopyWriteResult result = channel.postImmutablePackage(
                target(), List.of(item("fix", "observation:a:0")), CopyReadback.AUTHORED, fence);

        assertThat(result.receipts())
                .singleElement()
                .satisfies(signal -> assertThat(signal.unconfirmed()).isTrue());
        assertThat(fence.attempts).hasSize(1);
    }

    @Test
    void shouldRequestNothingWhenTheFenceRefusesOrEgressIsSilenced() {
        stubResolvedMr();
        stubDiscussions(List.of(), BOT);
        fence.acceptOnly = 0;

        CopyWriteResult refused = channel.postImmutablePackage(
                target(), List.of(item("fix", "observation:a:0")), CopyReadback.AUTHORED, fence);

        assertThat(refused.receipts())
                .singleElement()
                .satisfies(signal -> assertThat(signal.writeMayHaveStarted()).isFalse());
        doThrow(new OutboundEgressSuppressedException("gitlab.post-inline-feedback"))
                .when(egressGuard)
                .requireDeliveryAllowed(anyString());
        CopyWriteResult silenced = channel.postImmutablePackage(
                target(), List.of(item("fix", "observation:b:0")), CopyReadback.AUTHORED, fence);

        assertThat(silenced.withheldCopyIds()).isNotEmpty();
        assertThat(silenced.withheldCopyIds()).containsExactly(ScmFeedbackFixtures.copyId("observation:b:0"));
        verify(client, never()).documentName("CreateMergeRequestNote");
    }

    @Test
    void findPostedIsInconclusiveWhenADiscussionHasMoreNotesThanOneLookupReads() {
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        Map<String, Object> crowded = discussion("gid://gitlab/Discussion/1", List.of());
        crowded.put("notes", Map.of("nodes", List.of(), "pageInfo", Map.of("hasNextPage", true)));
        stubDiscussions(List.of(crowded), BOT);

        assertThat(channel.findPosted(target(), List.of(item("fix", "observation:a:0")), CopyReadback.AUTHORED))
                .isNull();
    }

    private static LineNote item(String body, String key) {
        return ScmFeedbackFixtures.line(ScmFeedbackFixtures.anchor("src/Foo.java", 10, null), body, MARKER, key);
    }

    /** The exact body the channel posts for {@code item}, as its own copy would read back. */
    private static String posted(LineNote item) {
        return item.body() + "\n" + item.writeMarker() + "\n<!-- hephaestus-diff-note-ck=" + item.writeKey() + " -->";
    }

    /**
     * The ordinary comment the channel posts for a single-line {@code item} on {@code
     * src/Foo.java}.
     */
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
        when(mrResolver.resolve(1L, "group/project", 42))
                .thenReturn(new MrInfo("gid://gitlab/MR/42", "base", "head", "start"));
    }

    private GraphQlClient.RequestSpec discussionsSpec() {
        GraphQlClient.RequestSpec spec = mock(GraphQlClient.RequestSpec.class);
        when(client.documentName("GetMergeRequestDiscussions")).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);
        return spec;
    }

    private GraphQlClient.RequestSpec createSpec() {
        GraphQlClient.RequestSpec spec = mock(GraphQlClient.RequestSpec.class);
        when(client.documentName("CreateMergeRequestNote")).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);
        return spec;
    }

    /** Stubs one complete page of discussions read by the identity {@code currentUserId}. */
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

    /** A createNote answer: the payload's {@code note}, explicitly null when {@code created} is. */
    private static ClientGraphQlResponse createResponse(@Nullable Map<String, Object> created, List<String> errors) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("note", created);
        payload.put("errors", errors);
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        stubField(response, "CreateMergeRequestNote", "createNote", payload);
        return response;
    }

    private static Map<String, Object> created(String noteId) {
        return Map.of("id", noteId, "url", NOTE_URL);
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
        n.put("position", Map.of("newPath", "src/Foo.java", "newLine", line, "diffRefs", Map.of("headSha", headSha)));
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

    /** Records what each create request carried; accepts the first {@code acceptOnly} records. */
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
