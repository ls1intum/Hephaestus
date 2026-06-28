package de.tum.cit.aet.hephaestus.integration.scm.github.feedback;

import static de.tum.cit.aet.hephaestus.integration.scm.github.feedback.GithubPrNodeIdResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.FindingAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.DeliveredSignal;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.Disposition;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.github.feedback.GithubFeedbackChannel.PrCoordinates;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.stereotype.Component;

/**
 * GitHub adapter for {@link InlineFindingChannel}. Posts all inline findings as a
 * single atomic {@code addPullRequestReview} mutation with embedded threads — one
 * notification per review, all-or-nothing semantics for the batch.
 *
 * <p>Reconciliation under GitHub's append-only review model differs from GitLab's edit-in-place: a review
 * thread cannot be deleted or edited, only minimized. So each finding's stable
 * {@link de.tum.cit.aet.hephaestus.practices.observation.ObservationFingerprint} is embedded in the thread body as a
 * hidden HTML tag, and before posting we read the PR's existing review threads
 * ({@code GetPullRequestReviewThreads}) and index this reviewer's own prior threads by that key. A finding
 * whose key already has a live (non-outdated) bot thread is PRESERVED rather than re-posted, so a stable
 * finding does not accrue a duplicate thread on every re-run. After posting, the created comment node ids are
 * read back from the mutation payload and matched to findings by {@code path:line} so each
 * {@link DeliveredSignal} carries the durable comment + review handles.
 *
 * <p>Both the post path and the {@link #clearStaleFindings} override retire (minimize as {@code OUTDATED}) the
 * prior bot threads whose finding the current run no longer emits — the GitHub analogue of GitLab's
 * delete-stale-notes path. {@code clearStaleFindings} covers the zero-note re-run; {@code postInlineFindings}
 * covers the partial-vanish case (some findings still hold, others went away).
 *
 * <p>Non-{@link FindingAnchor.DiffAnchor} anchors are counted as failed and logged;
 * GitHub has no analogue for document/channel/issue anchors on a PR review.
 *
 * <p>The commit SHA the review is anchored to is read from the {@link FeedbackChannel.FeedbackTarget#resourceUrl}
 * field — the agent layer encodes the head commit there so the channel doesn't need
 * to re-resolve PR metadata.
 */
@Component
public class GithubInlineFindingChannel implements InlineFindingChannel {

    private static final Logger log = LoggerFactory.getLogger(GithubInlineFindingChannel.class);

    /** GitHub caps {@code reviewThreads(first:)} at 100; we page through with the connection cursor. */
    private static final int THREADS_PAGE_SIZE = 100;

    /** Hard cap on pagination so a pathological PR with thousands of threads cannot hang reconciliation. */
    private static final int MAX_THREAD_PAGES = 20;

    /** GitHub {@code minimizeComment} classifier for a thread whose finding is no longer reported. */
    private static final String OUTDATED_CLASSIFIER = "OUTDATED";

    /**
     * Hidden per-finding correlation tag embedded in a thread body so a prior thread can be matched back to the
     * finding that produced it across re-runs. Humans never type this HTML comment, so its presence in a thread's
     * first comment marks the thread as one of ours. The key is alnum/dash/underscore (a
     * {@link de.tum.cit.aet.hephaestus.practices.observation.ObservationFingerprint} digest), so no escaping is needed.
     */
    private static final Pattern CK_TAG = Pattern.compile("<!-- hephaestus-diff-note-ck=([A-Za-z0-9_-]+) -->");

    private final GitHubGraphQlClientProvider gitHubProvider;
    private final GithubPrNodeIdResolver prNodeIdResolver;

    public GithubInlineFindingChannel(
        GitHubGraphQlClientProvider gitHubProvider,
        GithubPrNodeIdResolver prNodeIdResolver
    ) {
        this.gitHubProvider = gitHubProvider;
        this.prNodeIdResolver = prNodeIdResolver;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public InlineResult postInlineFindings(FeedbackChannel.FeedbackTarget target, List<InlineFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return InlineResult.counts(0, 0);
        }
        long scopeId = target.ref().workspaceId();
        if (gitHubProvider.isRateLimitCritical(scopeId)) {
            log.warn(
                "GitHub rate limit critical — skipping {} inline findings: workspaceId={}",
                findings.size(),
                scopeId
            );
            return InlineResult.counts(0, findings.size());
        }

        PrCoordinates pr = GithubFeedbackChannel.parseSubjectExternalId(target.subjectExternalId());

        // Index this reviewer's prior threads by correlation key so a finding that still holds is preserved
        // instead of re-posted (GitHub reviews are append-only — a duplicate cannot be edited away). Best-effort:
        // a failed read yields an empty index, degrading to a fresh post (still keyed) rather than blocking.
        Map<String, PriorThread> priorByKey = indexPriorThreads(scopeId, pr);

        // Partition findings: those whose key already has a live prior thread are preserved; the rest are posted.
        List<InlineFinding> toPost = new ArrayList<>(findings.size());
        List<DeliveredSignal> preservedSignals = new ArrayList<>();
        // Keys still backed by a finding this run (preserved now, posted below). Prior threads outside this set
        // belong to findings that vanished and must be retired.
        Set<String> seenKeys = new HashSet<>();
        int unsupportedAnchorCount = 0;
        for (InlineFinding finding : findings) {
            if (!(finding.anchor() instanceof FindingAnchor.DiffAnchor diff)) {
                log.warn("Skipping non-diff anchor on GitHub inline finding: anchor={}", finding.anchor());
                unsupportedAnchorCount++;
                continue;
            }
            if (finding.body() == null || finding.body().isBlank()) {
                continue;
            }
            String key = finding.recurrenceKey();
            PriorThread prior = key == null ? null : priorByKey.get(key);
            if (prior != null && !prior.outdated()) {
                // The finding still holds and already has a live thread — leave it, don't duplicate.
                preservedSignals.add(
                    new DeliveredSignal(key, diff, Disposition.PRESERVED_EXISTING, prior.commentId(), prior.threadId())
                );
                seenKeys.add(key);
                continue;
            }
            toPost.add(finding);
        }

        if (toPost.isEmpty()) {
            // Nothing new to post, but findings that vanished since the last run still have live bot threads —
            // retire them here too (this is the most common partial re-review: all survivors are preserved).
            int minimized = minimizeVanishedThreads(scopeId, priorByKey.values(), seenKeys);
            log.debug(
                "All GitHub inline findings preserved or skipped (none to post): workspaceId={}, preserved={}, minimized={}",
                scopeId,
                preservedSignals.size(),
                minimized
            );
            return new InlineResult(preservedSignals.size(), unsupportedAnchorCount, List.copyOf(preservedSignals));
        }

        String prNodeId = prNodeIdResolver.resolve(scopeId, pr.owner(), pr.name(), pr.number());
        String commitOid = target.resourceUrl(); // agent encodes head SHA here

        // Build the thread payloads, embedding each finding's correlation tag so the next run can index it back.
        List<Map<String, Object>> threads = new ArrayList<>(toPost.size());
        List<FindingAnchor.DiffAnchor> postedAnchors = new ArrayList<>(toPost.size());
        List<String> postedKeys = new ArrayList<>(toPost.size());
        for (InlineFinding finding : toPost) {
            FindingAnchor.DiffAnchor diff = (FindingAnchor.DiffAnchor) finding.anchor();
            threads.add(buildThread(diff, appendCorrelationTag(finding.body(), finding.recurrenceKey())));
            postedAnchors.add(diff);
            postedKeys.add(finding.recurrenceKey());
        }
        seenKeys.addAll(postedKeys);

        try {
            ClientGraphQlResponse response = gitHubProvider
                .forScope(scopeId)
                .documentName("AddPullRequestReviewWithThreads")
                .variable("pullRequestId", prNodeId)
                .variable("event", "COMMENT")
                .variable("commitOID", commitOid)
                .variable("threads", threads)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null) {
                throw new FeedbackDeliveryException("Null response from AddPullRequestReviewWithThreads");
            }
            gitHubProvider.trackRateLimit(scopeId, response);

            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                log.warn(
                    "GitHub addPullRequestReview with threads failed: workspaceId={}, errors={}, threadCount={}",
                    scopeId,
                    response.getErrors(),
                    threads.size()
                );
                List<DeliveredSignal> failed = failedSignals(postedAnchors, postedKeys);
                failed.addAll(preservedSignals);
                return new InlineResult(preservedSignals.size(), threads.size() + unsupportedAnchorCount, failed);
            }

            String reviewId = response.field("addPullRequestReview.pullRequestReview.id").getValue();
            List<DeliveredSignal> postedSignals = buildPostedSignals(response, reviewId, postedAnchors, postedKeys);

            // Retire prior bot threads whose finding vanished this run (still-seen = posted ∪ preserved).
            int minimized = minimizeVanishedThreads(scopeId, priorByKey.values(), seenKeys);

            log.info(
                "Posted {} GitHub inline findings as single review: workspaceId={}, prNodeId={}, preserved={}, minimized={}",
                threads.size(),
                scopeId,
                prNodeId,
                preservedSignals.size(),
                minimized
            );
            List<DeliveredSignal> all = new ArrayList<>(postedSignals);
            all.addAll(preservedSignals);
            return new InlineResult(threads.size() + preservedSignals.size(), unsupportedAnchorCount, List.copyOf(all));
        } catch (FeedbackDeliveryException e) {
            throw e;
        } catch (Exception e) {
            log.warn("GitHub inline finding batch failed: workspaceId={}, threadCount={}", scopeId, threads.size(), e);
            List<DeliveredSignal> failed = failedSignals(postedAnchors, postedKeys);
            failed.addAll(preservedSignals);
            return new InlineResult(preservedSignals.size(), threads.size() + unsupportedAnchorCount, failed);
        }
    }

    /**
     * Minimizes (hides as {@code OUTDATED}) every prior bot thread on the PR — the GitHub analogue of GitLab's
     * stale-note delete. Called on a zero-note re-run so a PR re-reviewed into nothing-inline doesn't keep
     * findings on code no longer in the diff. GitHub reviews are append-only, so minimize is the only
     * non-destructive way to retire a thread.
     */
    @Override
    public void clearStaleFindings(FeedbackChannel.FeedbackTarget target, String marker) {
        long scopeId = target.ref().workspaceId();
        if (gitHubProvider.isRateLimitCritical(scopeId)) {
            log.warn("GitHub rate limit critical — skipping stale inline-thread minimize: workspaceId={}", scopeId);
            return;
        }
        PrCoordinates pr = GithubFeedbackChannel.parseSubjectExternalId(target.subjectExternalId());
        Map<String, PriorThread> priorByKey = indexPriorThreads(scopeId, pr);
        int minimized = minimizeVanishedThreads(scopeId, priorByKey.values(), Set.of());
        if (minimized > 0) {
            log.info("Minimized {} stale GitHub inline threads: workspaceId={}", minimized, scopeId);
        }
    }

    /**
     * Builds {@link DeliveredSignal}s for the posted threads, matching comment node ids back to findings.
     *
     * <p>Primary match is the per-finding correlation tag (ck-fingerprint) embedded in each posted comment body:
     * {@code path:line} is NOT unique — two findings can anchor to the same line — so a positional or path:line
     * index would hand the second finding the first's comment id (or none), corrupting its ledger external_ref.
     * Falls back to path:line only for a comment whose body carries no parseable tag (pre-correlation findings).
     */
    private static List<DeliveredSignal> buildPostedSignals(
        ClientGraphQlResponse response,
        @Nullable String reviewId,
        List<FindingAnchor.DiffAnchor> anchors,
        List<String> keys
    ) {
        Map<String, String> commentIdByCk = new HashMap<>();
        Map<String, String> commentIdByPathLine = new HashMap<>();
        List<Map<String, Object>> comments = response
            .field("addPullRequestReview.pullRequestReview.comments.nodes")
            .getValue();
        if (comments != null) {
            for (Map<String, Object> comment : comments) {
                String id = (String) comment.get("id");
                if (id == null) {
                    continue;
                }
                String body = (String) comment.get("body");
                String ck = body == null ? null : parseObservationFingerprint(body);
                if (ck != null) {
                    commentIdByCk.putIfAbsent(ck, id);
                }
                String path = (String) comment.get("path");
                Object line = comment.get("line");
                if (path != null && line != null) {
                    commentIdByPathLine.putIfAbsent(path + ":" + line, id);
                }
            }
        }

        List<DeliveredSignal> signals = new ArrayList<>(anchors.size());
        for (int i = 0; i < anchors.size(); i++) {
            FindingAnchor.DiffAnchor diff = anchors.get(i);
            String key = keys.get(i);
            String commentId = key == null ? null : commentIdByCk.get(key);
            if (commentId == null) {
                commentId = commentIdByPathLine.get(diff.filePath() + ":" + diff.newLineNumber());
            }
            signals.add(new DeliveredSignal(key, diff, Disposition.POSTED, commentId, reviewId));
        }
        return signals;
    }

    private static List<DeliveredSignal> failedSignals(List<FindingAnchor.DiffAnchor> anchors, List<String> keys) {
        List<DeliveredSignal> signals = new ArrayList<>(anchors.size());
        for (int i = 0; i < anchors.size(); i++) {
            signals.add(new DeliveredSignal(keys.get(i), anchors.get(i), Disposition.FAILED, null, null));
        }
        return signals;
    }

    /**
     * Reads the PR's review threads (paginated) and indexes this reviewer's own prior threads by the correlation
     * key embedded in each thread's first comment. Best-effort: any failure yields an empty index so delivery
     * degrades to fresh keyed posts rather than blocking.
     */
    private Map<String, PriorThread> indexPriorThreads(long scopeId, PrCoordinates pr) {
        Map<String, PriorThread> byKey = new LinkedHashMap<>();
        String after = null;
        try {
            for (int page = 0; page < MAX_THREAD_PAGES; page++) {
                ClientGraphQlResponse response = gitHubProvider
                    .forScope(scopeId)
                    .documentName("GetPullRequestReviewThreads")
                    .variable("owner", pr.owner())
                    .variable("name", pr.name())
                    .variable("number", pr.number())
                    .variable("first", THREADS_PAGE_SIZE)
                    .variable("after", after)
                    .execute()
                    .block(GRAPHQL_TIMEOUT);

                if (response == null) {
                    break;
                }
                gitHubProvider.trackRateLimit(scopeId, response);

                List<Map<String, Object>> nodes = response
                    .field("repository.pullRequest.reviewThreads.nodes")
                    .getValue();
                if (nodes != null) {
                    for (Map<String, Object> thread : nodes) {
                        indexThread(thread, byKey);
                    }
                }

                Boolean hasNext = response
                    .field("repository.pullRequest.reviewThreads.pageInfo.hasNextPage")
                    .getValue();
                if (!Boolean.TRUE.equals(hasNext)) {
                    break;
                }
                after = response.field("repository.pullRequest.reviewThreads.pageInfo.endCursor").getValue();
                if (after == null) {
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to read PR review threads for correlation reconcile: workspaceId={}", scopeId, e);
        }
        return byKey;
    }

    /** Indexes one review thread under the correlation key parsed from its first comment, if it is one of ours. */
    @SuppressWarnings("unchecked")
    private static void indexThread(Map<String, Object> thread, Map<String, PriorThread> byKey) {
        String threadId = (String) thread.get("id");
        if (threadId == null) {
            return;
        }
        Object commentsField = thread.get("comments");
        if (!(commentsField instanceof Map<?, ?> commentsMap)) {
            return;
        }
        Object nodes = commentsMap.get("nodes");
        if (!(nodes instanceof List) || ((List<?>) nodes).isEmpty()) {
            return;
        }
        Map<String, Object> firstComment = ((List<Map<String, Object>>) nodes).get(0);
        String body = (String) firstComment.get("body");
        String commentId = (String) firstComment.get("id");
        if (body == null || commentId == null) {
            return;
        }
        String key = parseObservationFingerprint(body);
        if (key == null) {
            return; // human thread or a legacy bot note posted before keys existed — not ours to reconcile
        }
        boolean outdated =
            Boolean.TRUE.equals(thread.get("isOutdated")) || Boolean.TRUE.equals(thread.get("isResolved"));
        byKey.put(key, new PriorThread(key, threadId, commentId, outdated));
    }

    /**
     * Minimizes the prior bot threads whose key is absent from {@code seenKeys} and that are not already
     * outdated/resolved — the findings that genuinely went away. Returns the number minimized. Best-effort.
     */
    private int minimizeVanishedThreads(long scopeId, Iterable<PriorThread> priorThreads, Set<String> seenKeys) {
        int minimized = 0;
        for (PriorThread prior : priorThreads) {
            if (seenKeys.contains(prior.key()) || prior.outdated()) {
                continue;
            }
            if (minimizeComment(scopeId, prior.commentId())) {
                minimized++;
            }
        }
        return minimized;
    }

    /** Minimizes a single review comment as {@code OUTDATED}; returns true on success. Best-effort. */
    private boolean minimizeComment(long scopeId, String commentId) {
        try {
            ClientGraphQlResponse response = gitHubProvider
                .forScope(scopeId)
                .documentName("MinimizeComment")
                .variable("subjectId", commentId)
                .variable("classifier", OUTDATED_CLASSIFIER)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null) {
                return false;
            }
            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                log.debug(
                    "Failed to minimize stale review comment: commentId={}, errors={}",
                    commentId,
                    response.getErrors()
                );
                return false;
            }
            return true;
        } catch (Exception e) {
            log.debug("Failed to minimize stale review comment: commentId={}", commentId, e);
            return false;
        }
    }

    @Nullable
    private static String parseObservationFingerprint(String body) {
        Matcher m = CK_TAG.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /** Appends the hidden per-finding correlation tag; a null key (pre-correlation finding) appends nothing. */
    private static String appendCorrelationTag(String body, @Nullable String recurrenceKey) {
        if (recurrenceKey == null || recurrenceKey.isBlank()) {
            return body;
        }
        return body + "\n<!-- hephaestus-diff-note-ck=" + recurrenceKey + " -->";
    }

    /** A prior review thread we posted, matched by the correlation key in its first comment. */
    private record PriorThread(String key, String threadId, String commentId, boolean outdated) {}

    /** Builds a GitHub review-thread payload from a {@link FindingAnchor.DiffAnchor}. */
    private static Map<String, Object> buildThread(FindingAnchor.DiffAnchor diff, String body) {
        Map<String, Object> thread = new HashMap<>();
        thread.put("path", diff.filePath());
        thread.put("body", body);

        Integer startLine = diff.startLine();
        boolean isMultiLine = startLine != null && startLine < diff.newLineNumber();
        if (isMultiLine) {
            thread.put("startLine", startLine);
            thread.put("line", diff.newLineNumber());
            thread.put("side", "RIGHT");
            thread.put("startSide", "RIGHT");
        } else {
            thread.put("line", diff.newLineNumber());
            thread.put("side", "RIGHT");
        }
        return thread;
    }
}
