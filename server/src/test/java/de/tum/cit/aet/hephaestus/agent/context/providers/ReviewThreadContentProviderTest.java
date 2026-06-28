package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ReviewThreadContentProviderTest extends BaseUnitTest {

    private static final String FILE_KEY = "inputs/context/review_threads.json";
    private static final Long PR_ID = 456L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private PullRequestReviewThreadRepository threadRepository;

    @Mock
    private PullRequestReviewRepository reviewRepository;

    private ReviewThreadContentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ReviewThreadContentProvider(
            objectMapper,
            pullRequestRepository,
            threadRepository,
            reviewRepository
        );
        lenient().when(pullRequestRepository.findByIdWithAllForGate(any())).thenReturn(java.util.Optional.empty());
        lenient().when(threadRepository.findAllByPullRequestIdWithResolvedBy(any())).thenReturn(List.of());
        lenient().when(reviewRepository.findAllByPullRequestIdWithAuthor(any())).thenReturn(List.of());
    }

    private ObjectNode metadataWithPr() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("repository_id", 123L);
        metadata.put("pull_request_id", PR_ID);
        return metadata;
    }

    private ContextRequest.PracticeReviewRequest request(ObjectNode metadata) {
        AgentJob job = new AgentJob();
        job.setMetadata(metadata);
        Workspace workspace = new Workspace();
        workspace.setId(99L);
        job.setWorkspace(workspace);
        return new ContextRequest.PracticeReviewRequest(job);
    }

    private User user(String login) {
        User u = new User();
        u.setLogin(login);
        return u;
    }

    private PullRequestReview review(PullRequestReview.State state, String author, Instant submittedAt) {
        PullRequestReview r = new PullRequestReview();
        r.setState(state);
        r.setAuthor(user(author));
        r.setSubmittedAt(submittedAt);
        return r;
    }

    private PullRequestReviewThread thread(
        PullRequestReviewThread.State state,
        String path,
        Integer line,
        User resolvedBy
    ) {
        PullRequestReviewThread t = new PullRequestReviewThread();
        t.setState(state);
        t.setPath(path);
        t.setLine(line);
        t.setResolvedBy(resolvedBy);
        return t;
    }

    private de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewComment comment(
        String login,
        String body
    ) {
        var c =
            new de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewComment();
        c.setBody(body);
        if (login != null) {
            c.setAuthor(user(login));
        }
        return c;
    }

    private PullRequest mergedPr() {
        PullRequest pr = new PullRequest();
        pr.setMerged(true);
        pr.setState(Issue.State.MERGED);
        return pr;
    }

    @Test
    void contribute_noPrId_writesNothing() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("repository_id", 123L);

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadata), files);

        assertThat(files).doesNotContainKey(FILE_KEY);
    }

    @Test
    void contribute_noThreadsNoReviews_writesNothing() {
        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        assertThat(files).doesNotContainKey(FILE_KEY);
    }

    @Test
    void contribute_changesRequestedReview_emittedAsRawDecisionRow() throws Exception {
        when(reviewRepository.findAllByPullRequestIdWithAuthor(PR_ID)).thenReturn(
            List.of(
                review(PullRequestReview.State.CHANGES_REQUESTED, "reviewer-a", Instant.parse("2025-06-01T10:00:00Z"))
            )
        );
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(java.util.Optional.of(mergedPr()));

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        assertThat(files).containsKey(FILE_KEY);
        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        assertThat(out.get("mergeState").asString()).isEqualTo("MERGED");
        JsonNode decision = out.get("reviewDecisions").get(0);
        assertThat(decision.get("state").asString()).isEqualTo("CHANGES_REQUESTED");
        assertThat(decision.get("author").asString()).isEqualTo("reviewer-a");
        // submittedAt is emitted raw so the agent (not this connector) can compute supersession.
        assertThat(decision.get("submittedAt").asString()).isEqualTo("2025-06-01T10:00:00Z");
    }

    @Test
    void contribute_changesRequestedThenApproved_emitsBothRowsWithTimestamps() throws Exception {
        when(reviewRepository.findAllByPullRequestIdWithAuthor(PR_ID)).thenReturn(
            List.of(
                review(PullRequestReview.State.CHANGES_REQUESTED, "reviewer-a", Instant.parse("2025-06-01T10:00:00Z")),
                review(PullRequestReview.State.APPROVED, "reviewer-a", Instant.parse("2025-06-01T12:00:00Z"))
            )
        );
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(java.util.Optional.of(mergedPr()));

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        // Both decisions are emitted losslessly with timestamps; supersession is the agent's to compute.
        JsonNode decisions = out.get("reviewDecisions");
        assertThat(decisions).hasSize(2);
        assertThat(decisions.get(0).get("submittedAt").asString()).isEqualTo("2025-06-01T10:00:00Z");
        assertThat(decisions.get(1).get("state").asString()).isEqualTo("APPROVED");
        assertThat(decisions.get(1).get("submittedAt").asString()).isEqualTo("2025-06-01T12:00:00Z");
        // ELT contract: this connector must NOT pre-compute the supersession observation — no derived aggregate,
        // no per-row "effective"/"superseded" flag. Raw rows only; the agent judges.
        assertThat(out.has("changesRequestedUnaddressed")).isFalse();
        assertThat(decisions.get(0).has("superseded")).isFalse();
    }

    @Test
    void contribute_moreDecisionsThanCap_keepsLatestApprove() throws Exception {
        // A7: the repository now returns decisions newest-first (ORDER BY submittedAt DESC, id DESC). With more
        // than MAX_DECISIONS rows, the consumer's truncation keeps the NEWEST — so a final superseding APPROVE
        // must survive, not be dropped behind older CHANGES_REQUESTED (which would fabricate a false
        // "merged past unresolved request-changes" finding).
        java.util.List<PullRequestReview> newestFirst = new java.util.ArrayList<>();
        // The latest decision: an APPROVE at the most recent timestamp.
        newestFirst.add(review(PullRequestReview.State.APPROVED, "reviewer-a", Instant.parse("2025-06-30T23:59:00Z")));
        // Followed by MAX_DECISIONS + 5 older CHANGES_REQUESTED rows (descending timestamps).
        for (int i = 0; i < ReviewThreadContentProvider.MAX_DECISIONS + 5; i++) {
            newestFirst.add(
                review(
                    PullRequestReview.State.CHANGES_REQUESTED,
                    "reviewer-a",
                    Instant.parse("2025-06-01T10:00:00Z").minusSeconds(i)
                )
            );
        }
        when(reviewRepository.findAllByPullRequestIdWithAuthor(PR_ID)).thenReturn(newestFirst);
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(java.util.Optional.of(mergedPr()));

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        JsonNode decisions = out.get("reviewDecisions");
        assertThat(decisions).hasSize(ReviewThreadContentProvider.MAX_DECISIONS);
        // The latest APPROVE is retained (it is the first row newest-first).
        assertThat(decisions.get(0).get("state").asString()).isEqualTo("APPROVED");
        assertThat(decisions.get(0).get("submittedAt").asString()).isEqualTo("2025-06-30T23:59:00Z");
    }

    @Test
    void contribute_hephaestusOwnThread_excludedFromCountAndEmit() throws Exception {
        // A thread whose comments are Hephaestus's own posted note (marker-bearing) must NOT count as a
        // reviewer thread — the rootComment FK is null in sync, so the comment set is the signal.
        PullRequestReviewThread botThread = thread(PullRequestReviewThread.State.UNRESOLVED, "src/Foo.swift", 10, null);
        botThread.setComments(
            java.util.Set.of(comment(null, "Add a unit test for encodeDepth.\n<!-- hephaestus-diff-note -->"))
        );

        PullRequestReviewThread humanThread = thread(
            PullRequestReviewThread.State.UNRESOLVED,
            "src/Bar.swift",
            5,
            null
        );
        humanThread.setComments(
            java.util.Set.of(comment("reviewer-a", "This force-unwrap will crash — can we guard it?"))
        );

        when(threadRepository.findAllByPullRequestIdWithResolvedBy(PR_ID)).thenReturn(List.of(botThread, humanThread));

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        // Only the human reviewer thread is counted; the Hephaestus note is dropped entirely. Assert by
        // CONTENT, not position, so the test does not encode an incidental ordering over the comment Set.
        assertThat(out.get("unresolvedCount").asInt()).isEqualTo(1);
        assertThat(out.get("threads")).hasSize(1);
        java.util.List<String> paths = new java.util.ArrayList<>();
        out.get("threads").forEach(node -> paths.add(node.get("path").asString()));
        assertThat(paths).containsExactly("src/Bar.swift").doesNotContain("src/Foo.swift");
    }

    @Test
    void contribute_unresolvedThread_countedAndEmitted() throws Exception {
        when(threadRepository.findAllByPullRequestIdWithResolvedBy(PR_ID)).thenReturn(
            List.of(
                thread(PullRequestReviewThread.State.UNRESOLVED, "src/Foo.swift", 12, null),
                thread(PullRequestReviewThread.State.RESOLVED, "src/Bar.swift", 5, user("reviewer-b"))
            )
        );

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        assertThat(out.get("unresolvedCount").asInt()).isEqualTo(1);
        assertThat(out.get("threads")).hasSize(2);
        JsonNode resolved = out.get("threads").get(1);
        assertThat(resolved.get("state").asString()).isEqualTo("RESOLVED");
        assertThat(resolved.get("resolvedBy").asString()).isEqualTo("reviewer-b");
    }

    @Test
    void contribute_pendingReview_excludedFromDecisions() throws Exception {
        // A PENDING review is an unsubmitted draft ("only visible to the author") with no submittedAt — it
        // must never reach the agent as a real decision, else it fabricates an outstanding-CHANGES signal.
        when(reviewRepository.findAllByPullRequestIdWithAuthor(PR_ID)).thenReturn(
            List.of(
                review(PullRequestReview.State.PENDING, "drafting-reviewer", Instant.parse("2025-05-01T12:00:00Z")),
                review(PullRequestReview.State.APPROVED, "reviewer-a", Instant.parse("2025-06-01T12:00:00Z"))
            )
        );
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(java.util.Optional.of(mergedPr()));

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        JsonNode decisions = out.get("reviewDecisions");
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).get("state").asString()).isEqualTo("APPROVED");
    }

    @Test
    void contribute_unknownReview_excludedFromDecisions() throws Exception {
        // UNKNOWN is the unmapped forward-compat fallback — not a genuine submitted decision.
        when(reviewRepository.findAllByPullRequestIdWithAuthor(PR_ID)).thenReturn(
            List.of(review(PullRequestReview.State.UNKNOWN, "reviewer-x", Instant.parse("2025-05-01T12:00:00Z")))
        );

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        assertThat(out.get("reviewDecisions")).isEmpty();
    }

    @Test
    void contribute_moreThreadsThanCap_emitsCapButCountsAll() throws Exception {
        // unresolvedCount must reflect the TRUE total even past the emit cap — the one subtle invariant: the
        // count is incremented before the MAX_THREADS truncation, so a noisy PR still reports the real backlog.
        java.util.List<PullRequestReviewThread> many = new java.util.ArrayList<>();
        int total = ReviewThreadContentProvider.MAX_THREADS + 7;
        for (int i = 0; i < total; i++) {
            many.add(thread(PullRequestReviewThread.State.UNRESOLVED, "src/File" + i + ".swift", i, null));
        }
        when(threadRepository.findAllByPullRequestIdWithResolvedBy(PR_ID)).thenReturn(many);

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        assertThat(out.get("threads")).hasSize(ReviewThreadContentProvider.MAX_THREADS);
        assertThat(out.get("unresolvedCount").asInt()).isEqualTo(total);
    }

    @Test
    void contribute_prLookupEmpty_mergeStateUnknown() throws Exception {
        // findByIdWithAllForGate returns empty (default stub) — mergeState degrades to UNKNOWN, never throws.
        when(reviewRepository.findAllByPullRequestIdWithAuthor(PR_ID)).thenReturn(
            List.of(review(PullRequestReview.State.APPROVED, "reviewer-a", Instant.parse("2025-06-01T12:00:00Z")))
        );

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        assertThat(out.get("mergeState").asString()).isEqualTo("UNKNOWN");
    }

    @Test
    void contribute_openUnmergedPr_mergeStateOpen() throws Exception {
        when(reviewRepository.findAllByPullRequestIdWithAuthor(PR_ID)).thenReturn(
            List.of(
                review(PullRequestReview.State.CHANGES_REQUESTED, "reviewer-a", Instant.parse("2025-06-01T10:00:00Z"))
            )
        );
        PullRequest openPr = new PullRequest();
        openPr.setMerged(false);
        openPr.setState(Issue.State.OPEN);
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(java.util.Optional.of(openPr));

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        assertThat(out.get("mergeState").asString()).isEqualTo("OPEN");
    }

    @Test
    void contribute_neverThrows_onRepositoryFailure() {
        when(reviewRepository.findAllByPullRequestIdWithAuthor(PR_ID)).thenThrow(new RuntimeException("db down"));

        Map<String, byte[]> files = new java.util.HashMap<>();
        assertThatCode(() -> provider.contribute(request(metadataWithPr()), files)).doesNotThrowAnyException();
        assertThat(files).doesNotContainKey(FILE_KEY);
    }

    @Test
    void required_isFalse_bestEffort() {
        assertThat(provider.required()).isFalse();
    }
}
