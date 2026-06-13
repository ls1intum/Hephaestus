package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cross-context, best-effort provider that de-blinds a PR review by materialising the
 * <em>review-decision and thread-resolution state</em> into {@code
 * inputs/context/review_threads.json}.
 *
 * <p>The battle-test found two costly CONTEXT-BLIND misses where the signal lives in the review
 * decision + merge state, never in the diff and never in the inline comments:
 *
 * <ul>
 *   <li>MR 577 — the author merged past <b>two unresolved {@code CHANGES_REQUESTED} reviews</b> via
 *       author auto-merge. {@code comments.json} is empty (no inline notes), so the review
 *       practices correctly read it as "no reviewer input" and abstain — yet the gate WAS jammed.
 *       The signal is the review <em>decision</em>, not an inline comment.
 *   <li>MR 575 — a "Looks good" rubber-stamp approval on hard-coded thresholds. The substance of
 *       the review lives in the review-decision row + the (un)resolved thread, not in a
 *       diff-anchored inline comment.
 * </ul>
 *
 * <p>Both facts ARE already persisted: {@link PullRequestReview#getState()} carries the decision
 * (APPROVED / CHANGES_REQUESTED / …) and {@link PullRequestReviewThread#getState()} carries
 * UNRESOLVED / RESOLVED plus {@code resolvedBy} and {@code outdated}. This provider reads them by
 * pull-request id and emits a compact, judgement-free fact sheet (telescope, not cage):
 *
 * <pre>
 * {
 *   "threads":[{"path":..,"line":..,"state":"UNRESOLVED|RESOLVED","resolvedBy":..,"author":..,"outdated":..}],
 *   "unresolvedCount": N,
 *   "reviewDecisions":[{"state":"CHANGES_REQUESTED","author":..}],
 *   "changesRequestedUnaddressed": M,   // CHANGES_REQUESTED reviews not later superseded by the same
 *                                       // reviewer's APPROVED — a SIGNAL the gate may have been bypassed
 *   "mergeState": "MERGED|CLOSED|OPEN"
 * }
 * </pre>
 *
 * <p>No comment bodies are materialised here — {@code comments.json} already carries inline notes;
 * this provider adds only the DECISION + RESOLUTION layer the diff and the inline thread cannot
 * express.
 *
 * <p>Best-effort ({@link #required()} == {@code false}): a missing PR id, absent rows, or any
 * failure degrades to writing nothing and NEVER aborts the job. When there is no review decision
 * AND no thread at all, the file is omitted (the review practices keep their existing empty-context
 * behaviour).
 */
@Component
@Order(200)
public class ReviewThreadContentProvider implements ContentProvider {

    private static final Logger log = LoggerFactory.getLogger(ReviewThreadContentProvider.class);

    /** Output filename under {@link ContentProvider#OUTPUT_PREFIX}. */
    static final String FILE_NAME = "review_threads.json";

    /** Cap on threads materialised — keeps the artefact a few KB even on a noisy PR. */
    static final int MAX_THREADS = 40;

    /** Cap on review-decision rows materialised. */
    static final int MAX_DECISIONS = 30;

    private final ObjectMapper objectMapper;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewThreadRepository threadRepository;
    private final PullRequestReviewRepository reviewRepository;

    public ReviewThreadContentProvider(
        ObjectMapper objectMapper,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewThreadRepository threadRepository,
        PullRequestReviewRepository reviewRepository
    ) {
        this.objectMapper = objectMapper;
        this.pullRequestRepository = pullRequestRepository;
        this.threadRepository = threadRepository;
        this.reviewRepository = reviewRepository;
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof ContextRequest.PracticeReviewRequest;
    }

    /** Cross-context enrichment: never abort the job if decision/thread state cannot be resolved. */
    @Override
    public boolean required() {
        return false;
    }

    @Override
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        if (!(request instanceof ContextRequest.PracticeReviewRequest pr)) {
            return;
        }
        try {
            AgentJob job = pr.job();
            JsonNode m = job.getMetadata();
            if (m == null || m.isNull() || m.isMissingNode()) {
                return;
            }

            Long pullRequestId = MetaJson.optLong(m, "pull_request_id");
            if (pullRequestId == null) {
                return;
            }

            List<PullRequestReviewThread> threads = threadRepository.findAllByPullRequestIdWithResolvedBy(
                pullRequestId
            );
            List<PullRequestReview> reviews = reviewRepository.findAllByPullRequestIdWithAuthor(pullRequestId);

            if ((threads == null || threads.isEmpty()) && (reviews == null || reviews.isEmpty())) {
                // No decision state and no thread state at all — emit nothing so the review practices
                // keep their existing empty-context abstention semantics.
                return;
            }

            PullRequest pullRequest = pullRequestRepository.findByIdWithAllForGate(pullRequestId).orElse(null);

            ObjectNode root = objectMapper.createObjectNode();

            // --- Threads ---
            ArrayNode threadArray = objectMapper.createArrayNode();
            int unresolved = 0;
            int emittedThreads = 0;
            if (threads != null) {
                for (PullRequestReviewThread t : threads) {
                    if (t == null) {
                        continue;
                    }
                    boolean isUnresolved = t.getState() == PullRequestReviewThread.State.UNRESOLVED;
                    if (isUnresolved) {
                        unresolved++;
                    }
                    if (emittedThreads >= MAX_THREADS) {
                        continue;
                    }
                    threadArray.add(toThread(t));
                    emittedThreads++;
                }
            }
            root.set("threads", threadArray);
            root.put("unresolvedCount", unresolved);

            // --- Review decisions ---
            ArrayNode decisionArray = objectMapper.createArrayNode();
            int changesRequestedUnaddressed = countUnaddressedChangesRequested(reviews);
            if (reviews != null) {
                int emitted = 0;
                for (PullRequestReview review : reviews) {
                    if (review == null || review.getState() == null) {
                        continue;
                    }
                    if (emitted >= MAX_DECISIONS) {
                        break;
                    }
                    decisionArray.add(toDecision(review));
                    emitted++;
                }
            }
            root.set("reviewDecisions", decisionArray);
            root.put("changesRequestedUnaddressed", changesRequestedUnaddressed);

            // --- Merge state (observable fact, no judgement) ---
            root.put("mergeState", mergeState(pullRequest));

            files.put(OUTPUT_PREFIX + FILE_NAME, objectMapper.writeValueAsBytes(root));
            log.info(
                "ReviewThreads: prId={} threads={} unresolved={} decisions={} changesRequestedUnaddressed={} mergeState={}",
                pullRequestId,
                emittedThreads,
                unresolved,
                decisionArray.size(),
                changesRequestedUnaddressed,
                root.get("mergeState").asString()
            );
        } catch (Exception e) {
            // Best-effort: cross-context enrichment must never fail the job.
            log.warn("ReviewThreadContentProvider failed, continuing without review-thread state: {}", e.getMessage());
        }
    }

    private ObjectNode toThread(PullRequestReviewThread t) {
        ObjectNode node = objectMapper.createObjectNode();
        if (t.getPath() != null) {
            node.put("path", t.getPath());
        }
        if (t.getLine() != null) {
            node.put("line", t.getLine());
        }
        node.put("state", t.getState() == null ? "UNRESOLVED" : t.getState().name());
        String resolver = login(t.getResolvedBy());
        if (resolver != null) {
            node.put("resolvedBy", resolver);
        }
        if (t.getOutdated() != null) {
            node.put("outdated", t.getOutdated());
        }
        return node;
    }

    private ObjectNode toDecision(PullRequestReview review) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("state", review.getState().name());
        if (review.isDismissed()) {
            node.put("dismissed", true);
        }
        String author = login(review.getAuthor());
        if (author != null) {
            node.put("author", author);
        }
        return node;
    }

    /**
     * Counts CHANGES_REQUESTED reviews that no later APPROVED review by the SAME reviewer supersedes
     * — an observable SIGNAL that the change-request gate was still standing. This is a heuristic
     * hint, not a verdict: a maintainer may have dismissed the review out-of-band (captured
     * separately by {@code dismissed}). The agent decides; we only surface the count.
     */
    private static int countUnaddressedChangesRequested(List<PullRequestReview> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return 0;
        }
        // Latest decision per reviewer wins; a reviewer who later APPROVED no longer blocks.
        java.util.Map<String, PullRequestReview> latestByReviewer = new java.util.LinkedHashMap<>();
        for (PullRequestReview review : reviews) {
            if (review == null || review.getState() == null || review.getSubmittedAt() == null) {
                continue;
            }
            if (
                review.getState() != PullRequestReview.State.CHANGES_REQUESTED &&
                review.getState() != PullRequestReview.State.APPROVED
            ) {
                continue;
            }
            String key = login(review.getAuthor());
            if (key == null) {
                key = "review#" + review.getId();
            }
            PullRequestReview prev = latestByReviewer.get(key);
            if (prev == null || review.getSubmittedAt().isAfter(prev.getSubmittedAt())) {
                latestByReviewer.put(key, review);
            }
        }
        int count = 0;
        for (PullRequestReview review : latestByReviewer.values()) {
            if (review.getState() == PullRequestReview.State.CHANGES_REQUESTED && !review.isDismissed()) {
                count++;
            }
        }
        return count;
    }

    private static String mergeState(PullRequest pullRequest) {
        if (pullRequest == null) {
            return "UNKNOWN";
        }
        if (pullRequest.isMerged()) {
            return "MERGED";
        }
        if (pullRequest.getState() != null) {
            // Issue.State: OPEN / CLOSED / MERGED.
            return pullRequest.getState().name();
        }
        return "UNKNOWN";
    }

    private static String login(User user) {
        if (user == null) {
            return null;
        }
        String login = user.getLogin();
        return (login != null && !login.isBlank()) ? login : null;
    }
}
