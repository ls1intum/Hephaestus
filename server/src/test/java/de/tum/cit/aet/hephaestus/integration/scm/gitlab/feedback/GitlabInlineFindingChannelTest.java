package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.FindingAnchor.DiffAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.DeliveredSignal;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.Disposition;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.InlineFinding;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.InlineResult;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.graphql.GitLabPageInfo;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.MrInfo;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

class GitlabInlineFindingChannelTest extends BaseUnitTest {

    private static final String MARKER = "<!-- hephaestus-diff-note -->";

    @Mock
    private GitLabGraphQlClientProvider gitLabProvider;

    @Mock
    private GitlabMrResolver mrResolver;

    private GitlabInlineFindingChannel channel;
    private HttpGraphQlClient client;

    @BeforeEach
    void setUp() {
        channel = new GitlabInlineFindingChannel(gitLabProvider, mrResolver);
        client = mock(HttpGraphQlClient.class);
    }

    @Test
    void emptyFindings() {
        assertThat(channel.postInlineFindings(gitlabTarget(), List.of())).isEqualTo(InlineResult.counts(0, 0));
    }

    @Test
    void rateLimitCriticalShortCircuits() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(true);
        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix", MARKER, "ck-1"))
        );
        assertThat(result.posted()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void missingDiffRefsSkips() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(mrResolver.resolve(1L, "group/project", 42)).thenReturn(
            new MrInfo("gid://gitlab/MR/42", null, null, null)
        );

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix", MARKER, "ck-1"))
        );
        assertThat(result.posted()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    /** No prior thread for the key → a fresh CreateDiffNote thread, POSTED, with note + discussion ids captured. */
    @Test
    void createsFreshThreadWhenNoPriorMatch() {
        stubResolvedMr();
        stubDiscussionsReturning(List.of()); // no prior notes at all
        ArgumentCaptor<String> bodyCaptor = stubCreateDiffNoteSuccess("gid://Note/NEW", "gid://Disc/NEW");

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix-this", MARKER, "ck-new"))
        );

        assertThat(result.posted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.signals())
            .singleElement()
            .satisfies(s -> {
                assertThat(s.recurrenceKey()).isEqualTo("ck-new");
                assertThat(s.disposition()).isEqualTo(Disposition.POSTED);
                assertThat(s.externalRef()).isEqualTo("gid://Note/NEW");
                assertThat(s.threadExternalRef()).isEqualTo("gid://Disc/NEW");
            });
        // The correlation key must be embedded in the posted body so the next run can match it.
        assertThat(bodyCaptor.getValue()).contains("hephaestus-diff-note-ck=ck-new").contains(MARKER);
    }

    /** A prior bot thread with the SAME key and no human reply → UpdateNote in place; no new thread created. */
    @Test
    void editsInPlaceWhenKeyMatchesPriorBotThread() {
        stubResolvedMr();
        Map<String, Object> botNote = note(
            "gid://Note/OLD",
            "stale finding " + MARKER + "\n" + ckTag("ck-stable"),
            false
        );
        Map<String, Object> disc = discussion("gid://Disc/OLD", List.of(botNote));
        stubDiscussionsReturning(List.of(disc));

        HttpGraphQlClient.RequestSpec updateSpec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("UpdateNote")).thenReturn(updateSpec);
        when(updateSpec.variable(any(), any())).thenReturn(updateSpec);
        ClientGraphQlResponse updateResponse = emptyErrors("updateNote.errors");
        when(updateSpec.execute()).thenReturn(Mono.just(updateResponse));

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fresh text", MARKER, "ck-stable"))
        );

        verify(updateSpec).variable("id", "gid://Note/OLD");
        assertThat(result.posted()).isEqualTo(1);
        assertThat(result.signals())
            .singleElement()
            .satisfies(s -> {
                assertThat(s.disposition()).isEqualTo(Disposition.POSTED);
                assertThat(s.externalRef()).isEqualTo("gid://Note/OLD");
                assertThat(s.threadExternalRef()).isEqualTo("gid://Disc/OLD");
            });
        // A matched key must NOT create a fresh thread.
        verify(client, never()).documentName("CreateDiffNote");
    }

    /** A prior thread the developer replied to is PRESERVED — neither edited nor deleted. */
    @Test
    void preservesHumanRepliedThread() {
        stubResolvedMr();
        Map<String, Object> botNote = note("gid://Note/B", "issue " + MARKER + "\n" + ckTag("ck-human"), false);
        Map<String, Object> humanReply = note("gid://Note/H", "Thanks, fixed it!", false);
        Map<String, Object> disc = discussion("gid://Disc/B", List.of(botNote, humanReply));
        stubDiscussionsReturning(List.of(disc));

        // Both paths are stubbed leniently only to assert they are NEVER taken for a human-replied thread.
        HttpGraphQlClient.RequestSpec updateSpec = mock(HttpGraphQlClient.RequestSpec.class);
        lenient().when(client.documentName("UpdateNote")).thenReturn(updateSpec);
        HttpGraphQlClient.RequestSpec destroySpec = mock(HttpGraphQlClient.RequestSpec.class);
        lenient().when(client.documentName("DestroyNote")).thenReturn(destroySpec);

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "re-detected", MARKER, "ck-human"))
        );

        verify(updateSpec, never()).execute();
        verify(destroySpec, never()).execute();
        assertThat(result.signals())
            .singleElement()
            .satisfies(s -> {
                assertThat(s.disposition()).isEqualTo(Disposition.PRESERVED_EXISTING);
                assertThat(s.externalRef()).isEqualTo("gid://Note/B");
            });
    }

    /** A prior bot thread whose key is gone this run AND has no human reply → DestroyNote. Human-replied gone → kept. */
    @Test
    void deletesVanishedHumanFreeThreadsOnly() {
        stubResolvedMr();
        // Gone-A: pure bot, key not emitted this run → delete.
        Map<String, Object> goneA = discussion(
            "gid://Disc/A",
            List.of(note("gid://Note/A", "old " + MARKER + "\n" + ckTag("ck-gone-A"), false))
        );
        // Gone-B: bot + human reply, key not emitted → preserve (never destroy human work).
        Map<String, Object> goneB = discussion(
            "gid://Disc/Bb",
            List.of(
                note("gid://Note/Bb", "old " + MARKER + "\n" + ckTag("ck-gone-B"), false),
                note("gid://Note/Hb", "I disagree", false)
            )
        );
        stubDiscussionsReturning(List.of(goneA, goneB));
        // This run posts a single, different finding.
        stubCreateDiffNoteSuccess("gid://Note/NEW", "gid://Disc/NEW");
        HttpGraphQlClient.RequestSpec destroySpec = stubDestroy();

        channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "current", MARKER, "ck-current"))
        );

        verify(destroySpec).variable("noteId", "gid://Note/A");
        verify(destroySpec, never()).variable("noteId", "gid://Note/Bb");
    }

    /** A diff note whose line is outside the diff hunk falls back to a plain MR comment (FELL_BACK). */
    @Test
    void fallsBackToMrCommentWhenLineOutsideHunk() {
        stubResolvedMr();
        stubDiscussionsReturning(List.of());

        // CreateDiffNote returns a "line_code" error → the channel must fall back to CreateMergeRequestNote.
        HttpGraphQlClient.RequestSpec diffSpec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("CreateDiffNote")).thenReturn(diffSpec);
        when(diffSpec.variable(any(), any())).thenReturn(diffSpec);
        ClientGraphQlResponse diffResponse = mock(ClientGraphQlResponse.class);
        stubField(diffResponse, "createDiffNote.errors", List.of("line_code is invalid"));
        when(diffSpec.execute()).thenReturn(Mono.just(diffResponse));

        HttpGraphQlClient.RequestSpec noteSpec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("CreateMergeRequestNote")).thenReturn(noteSpec);
        when(noteSpec.variable(any(), any())).thenReturn(noteSpec);
        ClientGraphQlResponse noteResponse = mock(ClientGraphQlResponse.class);
        stubField(noteResponse, "createNote.errors", List.of());
        stubField(noteResponse, "createNote.note.id", "gid://Note/FALLBACK");
        when(noteSpec.execute()).thenReturn(Mono.just(noteResponse));

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 999, null), "fix", MARKER, "ck-fb"))
        );

        assertThat(result.posted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.signals())
            .singleElement()
            .satisfies(s -> {
                assertThat(s.disposition()).isEqualTo(Disposition.FELL_BACK);
                assertThat(s.externalRef()).isEqualTo("gid://Note/FALLBACK");
            });
    }

    /** A rate-limit error mid-batch stops the run: the offending finding fails and the remaining ones are not posted. */
    @Test
    void rateLimitMidBatchStopsAndCountsRemainingAsFailed() {
        stubResolvedMr();
        stubDiscussionsReturning(List.of());

        // CreateDiffNote throws a rate-limit error on the FIRST call → the channel must stop the whole batch.
        HttpGraphQlClient.RequestSpec diffSpec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("CreateDiffNote")).thenReturn(diffSpec);
        when(diffSpec.variable(any(), any())).thenReturn(diffSpec);
        when(diffSpec.execute()).thenReturn(Mono.error(new RuntimeException("429 Too Many Requests")));

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(
                new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix-a", MARKER, "ck-a"),
                new InlineFinding(new DiffAnchor("src/Bar.java", 20, null), "fix-b", MARKER, "ck-b")
            )
        );

        // The batch stops at the first finding; both are reported failed (the one that hit the limit + the
        // unattempted remainder), and nothing is posted.
        assertThat(result.posted()).isZero();
        assertThat(result.failed()).isEqualTo(2);
        verify(client, never()).documentName("CreateMergeRequestNote");
    }

    /**
     * A mid-batch rate limit must NOT reap still-current threads. The un-processed remainder never
     * registered its key in seenKeys, so destroyVanishedThreads would see a still-current finding as
     * "vanished" and delete its live thread; the destroy is skipped entirely on a rate-limited run.
     */
    @Test
    void rateLimitMidBatchDoesNotDeleteStillCurrentThreads() {
        stubResolvedMr();
        // Prior bot thread for ck-keep — STILL CURRENT (the batch below still contains ck-keep).
        Map<String, Object> keepDisc = discussion(
            "gid://Disc/KEEP",
            List.of(note("gid://Note/KEEP", "kept finding " + MARKER + "\n" + ckTag("ck-keep"), false))
        );
        stubDiscussionsReturning(List.of(keepDisc));

        // CreateDiffNote throws a rate-limit error on the FIRST finding (ck-a) → the batch stops before ck-keep
        // is processed, so ck-keep never reaches seenKeys.
        HttpGraphQlClient.RequestSpec diffSpec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("CreateDiffNote")).thenReturn(diffSpec);
        when(diffSpec.variable(any(), any())).thenReturn(diffSpec);
        when(diffSpec.execute()).thenReturn(Mono.error(new RuntimeException("429 Too Many Requests")));

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(
                new InlineFinding(new DiffAnchor("src/A.java", 10, null), "fix-a", MARKER, "ck-a"),
                new InlineFinding(new DiffAnchor("src/Keep.java", 20, null), "fix-keep", MARKER, "ck-keep")
            )
        );

        // The rate-limited run must skip the destroy ENTIRELY (no DestroyNote document is even requested), so the
        // still-current ck-keep thread cannot be reaped despite never reaching seenKeys this run.
        verify(client, never()).documentName("DestroyNote");
        assertThat(result.failed()).isEqualTo(2);
    }

    @Test
    void clearStalePreservesHumanRepliedThreads() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(gitLabProvider.forScope(1L)).thenReturn(client);

        Map<String, Object> botNoteA = note("gid://Note/A", "Issue here MARKER", false);
        Map<String, Object> discA = discussion(null, List.of(botNoteA));
        Map<String, Object> botNoteB = note("gid://Note/B", "Another issue MARKER", false);
        Map<String, Object> humanReply = note("gid://Note/H", "Thanks, fixed it!", false);
        Map<String, Object> discB = discussion(null, List.of(botNoteB, humanReply));
        stubDiscussionsReturning(List.of(discA, discB));
        HttpGraphQlClient.RequestSpec destroySpec = stubDestroy();

        channel.clearStaleFindings(gitlabTarget(), "MARKER");

        verify(destroySpec).variable("noteId", "gid://Note/A");
        verify(destroySpec, never()).variable("noteId", "gid://Note/B");
    }

    /**
     * Two findings in one batch carrying the SAME non-null recurrenceKey (a fingerprint that escaped upstream
     * dedup): only the first creates a thread; the twin is skipped so no orphan thread is left behind.
     */
    @Test
    void skipsDuplicateRecurrenceKeyWithinBatch() {
        stubResolvedMr();
        stubDiscussionsReturning(List.of()); // no prior threads
        stubCreateDiffNoteSuccess("gid://Note/NEW", "gid://Disc/NEW");

        InlineResult result = channel.postInlineFindings(
            gitlabTarget(),
            List.of(
                new InlineFinding(new DiffAnchor("src/A.java", 10, null), "first", MARKER, "ck-dup"),
                new InlineFinding(new DiffAnchor("src/B.java", 20, null), "twin", MARKER, "ck-dup")
            )
        );

        // Exactly one thread is created and exactly one signal emitted; the twin is dropped.
        verify(client).documentName("CreateDiffNote");
        assertThat(result.posted()).isEqualTo(1);
        assertThat(result.signals())
            .singleElement()
            .satisfies(s -> assertThat(s.recurrenceKey()).isEqualTo("ck-dup"));
    }

    // --- stubbing helpers ----------------------------------------------------------------------------------

    private void stubResolvedMr() {
        when(gitLabProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(gitLabProvider.forScope(1L)).thenReturn(client);
        when(mrResolver.resolve(1L, "group/project", 42)).thenReturn(
            new MrInfo("gid://gitlab/MR/42", "base", "head", "start")
        );
    }

    /** Stubs a single page of discussions (pageInfo.hasNextPage=false), the common case. */
    private void stubDiscussionsReturning(List<Map<String, Object>> discussions) {
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("GetMergeRequestDiscussions")).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);
        ClientGraphQlResponse response = discussionsResponse(discussions, new GitLabPageInfo(false, null));
        when(spec.execute()).thenReturn(Mono.just(response));
    }

    /** Builds a discussions GraphQL response carrying the given nodes + pageInfo. */
    private static ClientGraphQlResponse discussionsResponse(
        List<Map<String, Object>> discussions,
        GitLabPageInfo pageInfo
    ) {
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ClientResponseField nodesField = mock(ClientResponseField.class);
        when(response.field("project.mergeRequest.discussions.nodes")).thenReturn(nodesField);
        when(nodesField.getValue()).thenReturn(discussions);
        ClientResponseField pageInfoField = mock(ClientResponseField.class);
        when(response.field("project.mergeRequest.discussions.pageInfo")).thenReturn(pageInfoField);
        when(pageInfoField.toEntity(GitLabPageInfo.class)).thenReturn(pageInfo);
        return response;
    }

    /** Stubs a successful CreateDiffNote returning the given note + discussion ids; captures the posted body. */
    private ArgumentCaptor<String> stubCreateDiffNoteSuccess(String noteId, String discussionId) {
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("CreateDiffNote")).thenReturn(spec);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(spec.variable(any(), any())).thenReturn(spec);
        when(spec.variable(eq("body"), bodyCaptor.capture())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        stubField(response, "createDiffNote.errors", List.of());
        stubField(response, "createDiffNote.note.id", noteId);
        stubField(response, "createDiffNote.note.discussion.id", discussionId);
        when(spec.execute()).thenReturn(Mono.just(response));
        return bodyCaptor;
    }

    private HttpGraphQlClient.RequestSpec stubDestroy() {
        HttpGraphQlClient.RequestSpec destroySpec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("DestroyNote")).thenReturn(destroySpec);
        when(destroySpec.variable(eq("noteId"), any())).thenReturn(destroySpec);
        ClientGraphQlResponse destroyResponse = emptyErrors("destroyNote.errors");
        when(destroySpec.execute()).thenReturn(Mono.just(destroyResponse));
        return destroySpec;
    }

    private static ClientGraphQlResponse emptyErrors(String errorsPath) {
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        stubField(response, errorsPath, List.of());
        return response;
    }

    private static void stubField(ClientGraphQlResponse response, String path, Object value) {
        ClientResponseField field = mock(ClientResponseField.class);
        when(response.field(path)).thenReturn(field);
        when(field.getValue()).thenReturn(value);
    }

    private static Map<String, Object> note(String id, String body, boolean system) {
        // HashMap-backed (not Map.of) so null discussion ids in tests don't throw.
        Map<String, Object> n = new java.util.HashMap<>();
        n.put("id", id);
        n.put("body", body);
        n.put("system", system);
        return n;
    }

    private static Map<String, Object> discussion(String id, List<Map<String, Object>> notes) {
        Map<String, Object> disc = new java.util.HashMap<>();
        disc.put("id", id);
        disc.put("notes", Map.of("nodes", new ArrayList<>(notes)));
        return disc;
    }

    private static String ckTag(String key) {
        return "<!-- hephaestus-diff-note-ck=" + key + " -->";
    }

    private static FeedbackTarget gitlabTarget() {
        return new FeedbackTarget(new IntegrationRef(IntegrationKind.GITLAB, 1L, null), "group/project!42", null);
    }
}
