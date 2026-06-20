package de.tum.cit.aet.hephaestus.integration.scm.github.feedback;

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
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

class GithubInlineFindingChannelTest extends BaseUnitTest {

    private static final String CK_PREFIX = "<!-- hephaestus-diff-note-ck=";

    @Mock
    private GitHubGraphQlClientProvider gitHubProvider;

    @Mock
    private GithubPrNodeIdResolver prNodeIdResolver;

    @Mock
    private HttpGraphQlClient client;

    private GithubInlineFindingChannel channel;

    @BeforeEach
    void setUp() {
        channel = new GithubInlineFindingChannel(gitHubProvider, prNodeIdResolver);
    }

    @Test
    void emptyFindingsReturnsZero() {
        FeedbackTarget target = githubTarget();
        assertThat(channel.postInlineFindings(target, List.of())).isEqualTo(InlineResult.counts(0, 0));
    }

    @Test
    void postsDiffAnchorsAsBatchAndCapturesNodeIds() {
        FeedbackTarget target = githubTarget();
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        when(prNodeIdResolver.resolve(1L, "owner", "repo", 42)).thenReturn("PR_node123");

        // No prior threads on the PR.
        stubReviewThreads(List.of());
        // Mutation returns the two posted comment node ids keyed by path:line.
        stubAddReview(
            "REVIEW_1",
            List.of(comment("RC_foo", "src/Foo.java", 10), comment("RC_bar", "src/Bar.java", 20))
        );

        InlineResult result = channel.postInlineFindings(
            target,
            List.of(
                new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix1", "marker", "ck-foo"),
                new InlineFinding(new DiffAnchor("src/Bar.java", 20, null), "fix2", "marker", "ck-bar")
            )
        );

        assertThat(result.posted()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.signals()).hasSize(2);
        DeliveredSignal foo = signalForKey(result, "ck-foo");
        assertThat(foo.disposition()).isEqualTo(Disposition.POSTED);
        assertThat(foo.externalRef()).isEqualTo("RC_foo");
        assertThat(foo.threadExternalRef()).isEqualTo("REVIEW_1");
        DeliveredSignal bar = signalForKey(result, "ck-bar");
        assertThat(bar.externalRef()).isEqualTo("RC_bar");
        assertThat(bar.threadExternalRef()).isEqualTo("REVIEW_1");
    }

    @Test
    void embedsCorrelationTagInPostedThreadBody() {
        FeedbackTarget target = githubTarget();
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        when(prNodeIdResolver.resolve(1L, "owner", "repo", 42)).thenReturn("PR_node123");
        stubReviewThreads(List.of());
        ThreadsCaptor captor = stubAddReviewCapturingThreads("REVIEW_1");

        channel.postInlineFindings(
            target,
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix1", "marker", "ck-foo"))
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> threads = (List<Map<String, Object>>) captor.value;
        assertThat(threads).hasSize(1);
        assertThat((String) threads.get(0).get("body")).contains(CK_PREFIX + "ck-foo -->");
    }

    @Test
    void preservesFindingWhoseKeyAlreadyHasLiveThread() {
        FeedbackTarget target = githubTarget();
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(gitHubProvider.forScope(1L)).thenReturn(client);

        // A prior, non-outdated bot thread already carries ck-foo.
        stubReviewThreads(
            List.of(thread("THREAD_foo", "RC_old_foo", "earlier finding\n" + ckTag("ck-foo"), false, false))
        );
        // Only ck-bar is genuinely new and should be posted.
        HttpGraphQlClient.RequestSpec addSpec = stubAddReview(
            "REVIEW_2",
            List.of(comment("RC_bar", "src/Bar.java", 20))
        );

        InlineResult result = channel.postInlineFindings(
            target,
            List.of(
                new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix1", "marker", "ck-foo"),
                new InlineFinding(new DiffAnchor("src/Bar.java", 20, null), "fix2", "marker", "ck-bar")
            )
        );

        // ck-foo preserved (reused existing thread), ck-bar posted fresh.
        DeliveredSignal foo = signalForKey(result, "ck-foo");
        assertThat(foo.disposition()).isEqualTo(Disposition.PRESERVED_EXISTING);
        assertThat(foo.externalRef()).isEqualTo("RC_old_foo");
        assertThat(foo.threadExternalRef()).isEqualTo("THREAD_foo");
        DeliveredSignal bar = signalForKey(result, "ck-bar");
        assertThat(bar.disposition()).isEqualTo(Disposition.POSTED);
        assertThat(result.posted()).isEqualTo(2);

        // The preserved finding must NOT have been re-posted — only one thread in the mutation payload.
        verify(prNodeIdResolver).resolve(1L, "owner", "repo", 42);
        verify(addSpec).execute();
    }

    @Test
    void allFindingsPreservedSkipsTheAddReviewMutationEntirely() {
        FeedbackTarget target = githubTarget();
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        stubReviewThreads(List.of(thread("THREAD_foo", "RC_old_foo", "earlier\n" + ckTag("ck-foo"), false, false)));

        InlineResult result = channel.postInlineFindings(
            target,
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix1", "marker", "ck-foo"))
        );

        assertThat(result.posted()).isEqualTo(1);
        assertThat(signalForKey(result, "ck-foo").disposition()).isEqualTo(Disposition.PRESERVED_EXISTING);
        // No node-id resolution and no mutation happened — nothing new to post.
        verify(prNodeIdResolver, never()).resolve(any(Long.class), any(), any(), any(Integer.class));
        verify(client, never()).documentName("AddPullRequestReviewWithThreads");
    }

    @Test
    void outdatedPriorThreadIsRepostedNotPreserved() {
        FeedbackTarget target = githubTarget();
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        when(prNodeIdResolver.resolve(1L, "owner", "repo", 42)).thenReturn("PR_node123");

        // Prior ck-foo thread is OUTDATED, so the finding still holds and must be re-posted fresh.
        stubReviewThreads(List.of(thread("THREAD_foo", "RC_old_foo", "stale\n" + ckTag("ck-foo"), true, false)));
        stubAddReview("REVIEW_3", List.of(comment("RC_new_foo", "src/Foo.java", 10)));

        InlineResult result = channel.postInlineFindings(
            target,
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix1", "marker", "ck-foo"))
        );

        DeliveredSignal foo = signalForKey(result, "ck-foo");
        assertThat(foo.disposition()).isEqualTo(Disposition.POSTED);
        assertThat(foo.externalRef()).isEqualTo("RC_new_foo");
    }

    @Test
    void rateLimitCriticalShortCircuits() {
        FeedbackTarget target = githubTarget();
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(true);

        InlineResult result = channel.postInlineFindings(
            target,
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix", "marker", "ck"))
        );

        assertThat(result.posted()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void clearStaleMinimizesGoneBotThreads() {
        FeedbackTarget target = githubTarget();
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(gitHubProvider.forScope(1L)).thenReturn(client);

        // One live bot thread and one already-outdated bot thread; clearStale must minimize only the live one.
        stubReviewThreads(
            List.of(
                thread("THREAD_a", "RC_a", "finding A\n" + ckTag("ck-a"), false, false),
                thread("THREAD_b", "RC_b", "finding B\n" + ckTag("ck-b"), true, false)
            )
        );
        HttpGraphQlClient.RequestSpec minimizeSpec = stubMinimize();

        channel.clearStaleFindings(target, "marker");

        verify(minimizeSpec).variable("subjectId", "RC_a");
        verify(minimizeSpec, never()).variable("subjectId", "RC_b");
    }

    @Test
    void clearStaleIgnoresNonBotThreads() {
        FeedbackTarget target = githubTarget();
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(gitHubProvider.forScope(1L)).thenReturn(client);

        // A human review thread (no ck tag) must never be minimized.
        stubReviewThreads(List.of(thread("THREAD_h", "RC_h", "please rename this variable", false, false)));
        HttpGraphQlClient.RequestSpec minimizeSpec = stubMinimize();

        channel.clearStaleFindings(target, "marker");

        verify(minimizeSpec, never()).variable(eq("subjectId"), any());
    }

    @Test
    void postPathMinimizesVanishedThreadWhenAllSurvivorsPreserved() {
        FeedbackTarget target = githubTarget();
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(gitHubProvider.forScope(1L)).thenReturn(client);

        // Prior run posted ck-a and ck-b; this run only still finds ck-a. ck-b vanished and must be retired
        // even though there is nothing new to post (the common partial re-review case — toPost is empty).
        stubReviewThreads(
            List.of(
                thread("THREAD_a", "RC_a", "finding A\n" + ckTag("ck-a"), false, false),
                thread("THREAD_b", "RC_b", "finding B\n" + ckTag("ck-b"), false, false)
            )
        );
        HttpGraphQlClient.RequestSpec minimizeSpec = stubMinimize();

        InlineResult result = channel.postInlineFindings(
            target,
            List.of(new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix", "marker", "ck-a"))
        );

        assertThat(signalForKey(result, "ck-a").disposition()).isEqualTo(Disposition.PRESERVED_EXISTING);
        verify(client, never()).documentName("AddPullRequestReviewWithThreads");
        verify(minimizeSpec).variable("subjectId", "RC_b");
        verify(minimizeSpec, never()).variable("subjectId", "RC_a");
    }

    @Test
    void postPathMinimizesVanishedThreadAlongsideNewPost() {
        FeedbackTarget target = githubTarget();
        when(gitHubProvider.isRateLimitCritical(1L)).thenReturn(false);
        when(gitHubProvider.forScope(1L)).thenReturn(client);
        when(prNodeIdResolver.resolve(1L, "owner", "repo", 42)).thenReturn("PR_node123");

        // ck-a still holds (preserved), ck-c is new (posted), ck-b vanished (must be minimized on the post path).
        stubReviewThreads(
            List.of(
                thread("THREAD_a", "RC_a", "finding A\n" + ckTag("ck-a"), false, false),
                thread("THREAD_b", "RC_b", "finding B\n" + ckTag("ck-b"), false, false)
            )
        );
        stubAddReview("REVIEW_9", List.of(comment("RC_c", "src/Baz.java", 30)));
        HttpGraphQlClient.RequestSpec minimizeSpec = stubMinimize();

        InlineResult result = channel.postInlineFindings(
            target,
            List.of(
                new InlineFinding(new DiffAnchor("src/Foo.java", 10, null), "fix-a", "marker", "ck-a"),
                new InlineFinding(new DiffAnchor("src/Baz.java", 30, null), "fix-c", "marker", "ck-c")
            )
        );

        assertThat(signalForKey(result, "ck-a").disposition()).isEqualTo(Disposition.PRESERVED_EXISTING);
        assertThat(signalForKey(result, "ck-c").disposition()).isEqualTo(Disposition.POSTED);
        verify(minimizeSpec).variable("subjectId", "RC_b");
        verify(minimizeSpec, never()).variable("subjectId", "RC_a");
        verify(minimizeSpec, never()).variable("subjectId", "RC_c");
    }

    // --- stubbing helpers ----------------------------------------------------------------------------------

    /** Stubs GetPullRequestReviewThreads to return a single page of the given thread nodes. */
    private void stubReviewThreads(List<Map<String, Object>> nodes) {
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("GetPullRequestReviewThreads")).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        lenient().when(response.getErrors()).thenReturn(List.of());
        stubField(response, "repository.pullRequest.reviewThreads.nodes", nodes);
        stubField(response, "repository.pullRequest.reviewThreads.pageInfo.hasNextPage", Boolean.FALSE);
        stubField(response, "repository.pullRequest.reviewThreads.pageInfo.endCursor", null);
        when(spec.execute()).thenReturn(Mono.just(response));
    }

    /** Stubs AddPullRequestReviewWithThreads to return the given review id + posted comment nodes. */
    private HttpGraphQlClient.RequestSpec stubAddReview(String reviewId, List<Map<String, Object>> commentNodes) {
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("AddPullRequestReviewWithThreads")).thenReturn(spec);
        when(spec.variable(any(), any())).thenReturn(spec);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        lenient().when(response.getErrors()).thenReturn(List.of());
        stubField(response, "addPullRequestReview.pullRequestReview.id", reviewId);
        stubField(response, "addPullRequestReview.pullRequestReview.comments.nodes", commentNodes);
        when(spec.execute()).thenReturn(Mono.just(response));
        return spec;
    }

    /** Like {@link #stubAddReview} but captures the {@code threads} variable so the body can be asserted. */
    private ThreadsCaptor stubAddReviewCapturingThreads(String reviewId) {
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName("AddPullRequestReviewWithThreads")).thenReturn(spec);
        ThreadsCaptor captor = new ThreadsCaptor();
        when(spec.variable(any(), any())).thenAnswer(inv -> {
            if ("threads".equals(inv.getArgument(0))) {
                captor.value = inv.getArgument(1);
            }
            return spec;
        });

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        lenient().when(response.getErrors()).thenReturn(List.of());
        stubField(response, "addPullRequestReview.pullRequestReview.id", reviewId);
        stubField(response, "addPullRequestReview.pullRequestReview.comments.nodes", List.of());
        when(spec.execute()).thenReturn(Mono.just(response));
        return captor;
    }

    private HttpGraphQlClient.RequestSpec stubMinimize() {
        HttpGraphQlClient.RequestSpec spec = mock(HttpGraphQlClient.RequestSpec.class);
        // lenient: the non-bot-thread test verifies minimize is NEVER called, so these stubs go unused there.
        lenient().when(client.documentName("MinimizeComment")).thenReturn(spec);
        lenient().when(spec.variable(any(), any())).thenReturn(spec);
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        lenient().when(response.getErrors()).thenReturn(List.of());
        lenient().when(spec.execute()).thenReturn(Mono.just(response));
        return spec;
    }

    private static void stubField(ClientGraphQlResponse response, String path, Object value) {
        ClientResponseField field = mock(ClientResponseField.class);
        lenient().when(response.field(path)).thenReturn(field);
        lenient().when(field.getValue()).thenReturn(value);
    }

    private static Map<String, Object> comment(String id, String path, int line) {
        Map<String, Object> c = new HashMap<>();
        c.put("id", id);
        c.put("path", path);
        c.put("line", line);
        return c;
    }

    /** Builds a reviewThreads node with a single first comment (the bot anchor). */
    private static Map<String, Object> thread(
        String threadId,
        String firstCommentId,
        String firstCommentBody,
        boolean outdated,
        boolean resolved
    ) {
        Map<String, Object> firstComment = new HashMap<>();
        firstComment.put("id", firstCommentId);
        firstComment.put("body", firstCommentBody);
        firstComment.put("author", Map.of("login", "hephaestus[bot]"));

        Map<String, Object> t = new HashMap<>();
        t.put("id", threadId);
        t.put("isOutdated", outdated);
        t.put("isResolved", resolved);
        t.put("path", "src/Foo.java");
        t.put("line", 10);
        t.put("comments", Map.of("nodes", new ArrayList<>(List.of(firstComment))));
        return t;
    }

    private static String ckTag(String key) {
        return CK_PREFIX + key + " -->";
    }

    private static DeliveredSignal signalForKey(InlineResult result, String key) {
        return result
            .signals()
            .stream()
            .filter(s -> key.equals(s.findingFingerprint()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no signal for key " + key));
    }

    private static FeedbackTarget githubTarget() {
        return new FeedbackTarget(
            new IntegrationRef(IntegrationKind.GITHUB, 1L, null),
            "owner/repo#42",
            "commit-sha-abc"
        );
    }

    /** Mutable holder for capturing the {@code threads} mutation variable. */
    private static final class ThreadsCaptor {

        private Object value;
    }
}
