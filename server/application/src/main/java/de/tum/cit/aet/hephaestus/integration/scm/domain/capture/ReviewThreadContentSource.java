package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import static de.tum.cit.aet.hephaestus.integration.scm.domain.capture.ScmCaptureContext.OUTPUT_PREFIX;

import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.context.WorkspaceScmProjection;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnCaptureRole
@Order(200)
public class ReviewThreadContentSource {

    public static final SourceKind KIND = new SourceKind("scm.review-threads");

    public Set<SourceKind> sourceKinds() {
        return Set.of(KIND);
    }

    public SourceKind sourceKindFor(String path) {
        var recordSource = ScmCitableTexts.recordSource(path);
        if (recordSource != null && sourceKinds().contains(recordSource)) return recordSource;
        return KIND;
    }

    private static final Logger log = LoggerFactory.getLogger(ReviewThreadContentSource.class);

    static final String FILE_NAME = "review_threads.json";

    private final ObjectMapper objectMapper;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewThreadRepository threadRepository;
    private final PullRequestReviewRepository reviewRepository;

    public ReviewThreadContentSource(
            ObjectMapper objectMapper,
            PullRequestRepository pullRequestRepository,
            PullRequestReviewThreadRepository threadRepository,
            PullRequestReviewRepository reviewRepository) {
        this.objectMapper = objectMapper;
        this.pullRequestRepository = pullRequestRepository;
        this.threadRepository = threadRepository;
        this.reviewRepository = reviewRepository;
    }

    public boolean supports(ScmCaptureContext request) {
        return request.pullRequest();
    }

    public boolean required() {
        return false;
    }

    private ObjectNode collect(long pullRequestId, ScmCapturedWork metadata) {
        try {
            List<Long> threadIds =
                    new ArrayList<>(threadRepository.findRecentIdsByPullRequestId(pullRequestId, Pageable.unpaged()));
            boolean threadsTruncated = false;
            List<PullRequestReviewThread> threads =
                    threadIds.isEmpty() ? List.of() : threadRepository.findAllByIdWithResolvedBy(threadIds);
            List<PullRequestReview> reviews = new ArrayList<>(reviewRepository.findRecentByPullRequestIdWithAuthor(
                    pullRequestId,
                    Set.of(PullRequestReview.State.PENDING, PullRequestReview.State.UNKNOWN),
                    Pageable.unpaged()));
            boolean decisionsTruncated = false;
            reviews.sort(Comparator.comparing(
                    PullRequestReview::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder())));

            ObjectNode root = objectMapper.createObjectNode();
            if (metadata.reviewSourceId() != null) {
                Long reviewId = metadata.reviewSourceId();
                Long aboutUserId = metadata.reviewerUserId();
                for (PullRequestReview review : reviews) {
                    User author = review.getAuthor();
                    if (reviewId != null
                            && aboutUserId != null
                            && reviewId.equals(review.getId())
                            && author != null
                            && aboutUserId.equals(author.getId())) {
                        ObjectNode recipient = root.putObject("reviewRecipient");
                        recipient.put("author", author.getLogin());
                        recipient.put("authorId", author.getNativeId());
                        break;
                    }
                }
            }

            ArrayNode threadArray = objectMapper.createArrayNode();
            for (PullRequestReviewThread t : threads) {
                if (t == null || isHephaestusThread(t)) {
                    continue;
                }
                threadArray.add(toThread(t));
            }
            root.set("threads", threadArray);

            ArrayNode decisionArray = objectMapper.createArrayNode();
            for (PullRequestReview review : reviews) {
                if (review == null || review.getState() == null) {
                    continue;
                }
                if (review.getState() == PullRequestReview.State.PENDING
                        || review.getState() == PullRequestReview.State.UNKNOWN) {
                    continue;
                }
                // A COMMENTED review with no body is the container of inline comments — GitHub's
                // batch, or the row GitLab's sync makes per discussion and author — and its
                // substance is already in comments.json. Listing it here would be a decision that
                // decided nothing.
                if (review.getState() == PullRequestReview.State.COMMENTED && isBlank(review.getBody())) {
                    continue;
                }
                decisionArray.add(toDecision(review));
            }
            root.set("reviewDecisions", decisionArray);
            // A withdrawn or replaced decision may be gone, so this list is no event history.
            root.put("decisionHistoryComplete", false);
            root.put("truncated", threadsTruncated || decisionsTruncated);

            log.info(
                    "ReviewThreads: prId={} threads={} decisions={}",
                    pullRequestId,
                    threadArray.size(),
                    decisionArray.size());
            return root;
        } catch (JacksonException e) {
            throw new ItemCaptureException(ItemCaptureCode.CAPTURE_FAILED, "Review-thread collection failed", e);
        }
    }

    public CaptureContribution capture(ScmCaptureContext request, Set<SourceKind> selectedKinds) {
        if (!selectedKinds.contains(KIND) || !request.pullRequest()) {
            return new CaptureContribution(
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), null, Map.of(),
                    List.of(), List.of(), List.of());
        }
        var metadata = request.work();
        // Missing metadata is a malformed job, not a PR with no review threads — the two must not
        // collapse into the same "collection found nothing" fact.
        Long pullRequestId = metadata.sourceId();
        if (pullRequestId == null) {
            throw new ItemCaptureException(
                    ItemCaptureCode.CAPTURE_FAILED, "Review-thread collection has no pull_request_id");
        }
        var pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null);
        if (pullRequest == null || pullRequest.getDeletedAt() != null) {
            return CaptureContribution.unavailable(selectedKinds, SourceAbsenceReason.NOT_FOUND);
        }
        ObjectNode root = collect(pullRequestId, metadata);
        boolean empty =
                root.path("threads").isEmpty() && root.path("reviewDecisions").isEmpty();
        boolean truncated = root.path("truncated").asBoolean();
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(
                OUTPUT_PREFIX + FILE_NAME,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
        return new CaptureContribution(
                files,
                Map.of(KIND, truncated ? SourceCompleteness.PARTIAL : SourceCompleteness.COMPLETE),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(KIND, empty ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY),
                Map.of(),
                Map.of(),
                null,
                Map.of(),
                List.of(),
                List.of(),
                ScmCitableTexts.files(files, this::sourceKindFor, objectMapper));
    }

    // Filter posted feedback by its marker; bot logins cannot reliably distinguish it from human review.
    private static boolean isHephaestusThread(PullRequestReviewThread t) {
        var comments = t.getComments();
        if (comments == null || comments.isEmpty()) {
            return false;
        }
        for (var c : comments) {
            if (c == null) {
                continue;
            }
            String body = c.getBody();
            if (body != null && body.contains(WorkspaceScmProjection.HEPHAESTUS_MARKER)) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode toThread(PullRequestReviewThread t) {
        ObjectNode node = objectMapper.createObjectNode();
        if (t.getId() != null) {
            node.put("id", t.getId());
        }
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
        if (t.getResolvedAt() != null) {
            node.put("resolvedAt", t.getResolvedAt().toString());
        }
        if (t.getOutdated() != null) {
            node.put("outdated", t.getOutdated());
        }
        if (t.getCreatedAt() != null) {
            node.put("createdAt", t.getCreatedAt().toString());
        }
        return node;
    }

    private ObjectNode toDecision(PullRequestReview review) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("nativeId", review.getNativeId());
        node.put("state", review.getState().name());
        if (review.isDismissed()) {
            node.put("dismissed", true);
        }
        String author = login(review.getAuthor());
        if (author != null) {
            node.put("author", author);
            if (review.getAuthor() != null && review.getAuthor().getType() == User.Type.BOT) {
                node.put("bot", true);
            }
        }
        if (review.getAuthor() != null) {
            node.put("authorId", review.getAuthor().getNativeId());
        }
        if (review.getCommitId() != null) {
            node.put("commitId", review.getCommitId());
        }
        if (review.getUpdatedAt() != null) {
            node.put("updatedAt", review.getUpdatedAt().toString());
        }
        // Keep raw dates so the review can order each reviewer's decisions.
        if (review.getSubmittedAt() != null) {
            node.put("submittedAt", review.getSubmittedAt().toString());
        }
        if (!isBlank(review.getBody())) {
            node.put("body", review.getBody());
        }
        return node;
    }

    private static boolean isBlank(@Nullable String text) {
        return text == null || text.isBlank();
    }

    private static @Nullable String login(@Nullable User user) {
        if (user == null) {
            return null;
        }
        String login = user.getLogin();
        return (login != null && !login.isBlank()) ? login : null;
    }
}
