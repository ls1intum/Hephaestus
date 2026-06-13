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
        // ELT contract: this connector must NOT pre-compute the supersession verdict — no derived aggregate,
        // no per-row "effective"/"superseded" flag. Raw rows only; the agent judges.
        assertThat(out.has("changesRequestedUnaddressed")).isFalse();
        assertThat(decisions.get(0).has("superseded")).isFalse();
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
