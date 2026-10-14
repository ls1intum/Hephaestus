package de.tum.cit.aet.hephaestus.integration.scm.github.feedback;

import static de.tum.cit.aet.hephaestus.integration.scm.github.feedback.GitHubPrNodeIdResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGateway;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGuard;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressSuppressedException;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyDisposition;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyForm;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyReadback;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyReceipt;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyUpdateKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyUpdateReceipt;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyWriteFence;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyWriteResult;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackNotSentException;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNote;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNoteWriter;
import de.tum.cit.aet.hephaestus.integration.core.spi.LookupPresence;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryContent;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryHandle;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryLookup;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmVersion;
import de.tum.cit.aet.hephaestus.integration.scm.feedback.ScmFeedbackFormatter;
import de.tum.cit.aet.hephaestus.integration.scm.feedback.ScmFeedbackTargetResolver;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.github.feedback.GitHubSummaryWriter.PrCoordinates;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.stereotype.Component;

/**
 * GitHub adapter for {@link LineNoteWriter}. Posts the new threads of a package as one {@code
 * addPullRequestReview} mutation: one notification per review, all-or-nothing for the batch.
 *
 * <p>Each thread's first comment carries the package marker and a hidden correlation tag with its
 * delivery key. Before any create, the pull request's review threads are read completely and the
 * copies of the package are indexed by key; an item with a copy is never posted again, and no copy
 * is ever minimized or recreated, outdated or resolved alike. The whole batch is fenced before the
 * one request, so a lost response leaves every thread in it unconfirmed rather than absent.
 *
 * <p>The review is anchored to {@link FeedbackTarget#version}, and a copy is verified at its
 * original commit and lines, so an outdated or resolved thread still counts as the copy it is.
 */
@Component
@ConditionalOnServerRole
@OutboundEgressGateway
public class GitHubLineNoteWriter implements LineNoteWriter {

    private static final Logger log = LoggerFactory.getLogger(GitHubLineNoteWriter.class);

    /**
     * GitHub caps {@code reviewThreads(first:)} at 100; we page through with the connection cursor.
     */
    private static final int THREADS_PAGE_SIZE = 100;

    /** Hard cap on pagination; a scan that reaches it with more pages proves nothing. */
    private static final int MAX_THREAD_PAGES = 20;

    /**
     * Hidden per-delivery correlation tag embedded in a thread body so a copy can be matched back
     * to the feedback that produced it. The key is alnum/dash/underscore/colon, so no escaping is
     * needed.
     */
    private static final Pattern CK_TAG = Pattern.compile("<!-- hephaestus-diff-note-ck=([A-Za-z0-9_:-]+) -->");

    private final GitHubGraphQlClientProvider gitHubProvider;
    private final GitHubPrNodeIdResolver prNodeIdResolver;
    private final OutboundEgressGuard egressGuard;
    private final ScmFeedbackTargetResolver targets;
    private final GitHubSummaryWriter summaries;

    public GitHubLineNoteWriter(
            GitHubGraphQlClientProvider gitHubProvider,
            GitHubPrNodeIdResolver prNodeIdResolver,
            OutboundEgressGuard egressGuard,
            ScmFeedbackTargetResolver targets,
            GitHubSummaryWriter summaries) {
        this.gitHubProvider = gitHubProvider;
        this.prNodeIdResolver = prNodeIdResolver;
        this.egressGuard = egressGuard;
        this.targets = targets;
        this.summaries = summaries;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public CopyWriteResult postImmutablePackage(
            FeedbackTarget target, List<LineNote> feedbackItems, CopyReadback readback, CopyWriteFence fence) {
        if (feedbackItems.isEmpty()) {
            return CopyWriteResult.of(List.of());
        }
        long scopeId = target.integration().workspaceId();
        if (gitHubProvider.isRateLimitCritical(scopeId)) {
            log.warn(
                    "GitHub rate limit critical — not requesting {} pieces of inline feedback:" + " workspaceId={}",
                    feedbackItems.size(),
                    scopeId);
            return CopyWriteResult.of(notSent(feedbackItems));
        }
        PrCoordinates pr = GitHubSummaryWriter.parseSubjectExternalId(targets.subject(target, IntegrationKind.GITHUB));
        String commitOid = commitOid(target);
        if (commitOid == null) {
            return CopyWriteResult.of(notSent(feedbackItems));
        }
        var lines = feedbackItems.stream()
                .filter(note -> note.anchor().endLine() != null)
                .toList();
        Map<String, Copy> copies = lines.isEmpty() ? Map.of() : indexCopies(scopeId, pr, lines, readback, commitOid);
        Map<UUID, SummaryLookup> fileCopies = new HashMap<>();
        for (LineNote note : feedbackItems)
            if (note.anchor().endLine() == null)
                fileCopies.put(
                        note.copyId(),
                        summaries.findExistingSummary(target, fileContent(target, note, readback, commitOid)));

        List<CopyReceipt> signals = new ArrayList<>(feedbackItems.size());
        List<LineNote> candidates = new ArrayList<>(feedbackItems.size());
        Set<String> processedKeys = new HashSet<>();
        for (LineNote item : feedbackItems) {
            String key = item.writeKey();
            if (key != null && !processedKeys.add(key)) {
                continue; // one copy per delivery key
            }
            FeedbackAnchor diff = item.anchor();
            if (item.body().isBlank() || (diff.endLine() != null && diff.side() == null)) {
                signals.add(CopyReceipt.notSent(item.copyId()));
                continue;
            }
            var fileCopy = fileCopies.get(item.copyId());
            if (fileCopy != null && fileCopy.presence() != LookupPresence.ABSENT) {
                var handle = fileCopy.handle();
                signals.add(
                        fileCopy.presence() == LookupPresence.FOUND && handle != null
                                ? new CopyReceipt(
                                        item.copyId(),
                                        CopyDisposition.PRESERVED_EXISTING,
                                        handle.externalRef(),
                                        handle.url(),
                                        null,
                                        CopyForm.FILE_NOTE)
                                : CopyReceipt.notSent(item.copyId()));
                continue;
            }
            Copy copy = copies.get(key);
            if (copy != null) {
                // A copy that does not verify is not proof of absence either: nothing is created
                // next to it.
                signals.add(copy.verified() ? copy.preserved(item) : CopyReceipt.notSent(item.copyId()));
                continue;
            }
            candidates.add(item);
        }
        if (candidates.isEmpty()) {
            return CopyWriteResult.of(signals);
        }

        if (candidates.stream().anyMatch(note -> note.anchor().endLine() == null))
            return postMixed(target, pr, commitOid, candidates, readback, fence, signals);
        String prNodeId = prNodeIdResolver.resolve(scopeId, pr.owner(), pr.name(), pr.number());
        try {
            egressGuard.requireDeliveryAllowed("github.post-inline-feedback");
        } catch (OutboundEgressSuppressedException e) {
            signals.addAll(notSent(candidates));
            return CopyWriteResult.withheld(signals, deliveryKeys(candidates));
        }
        if (!fence.beforeCreate(candidates.stream().map(LineNote::copyId).toList(), List.copyOf(signals))) {
            signals.addAll(notSent(candidates));
            return CopyWriteResult.of(signals);
        }
        signals.addAll(postBatch(scopeId, prNodeId, commitOid, candidates, readback));
        return CopyWriteResult.of(signals);
    }

    private SummaryContent fileContent(FeedbackTarget target, LineNote note, CopyReadback readback, String commit) {
        var pr = GitHubSummaryWriter.parseSubjectExternalId(targets.subject(target, kind()));
        String origin = targets.serverUrl(target, kind());
        String anchoredCommit = note.anchor().side() == CitationSide.OLD
                ? Objects.requireNonNull(target.version().baseVersion())
                : commit;
        String encodedPath = Arrays.stream(note.anchor().path().split("/", -1))
                .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
        String body = "**[View file]("
                + origin.replaceAll("/+$", "")
                + "/"
                + pr.owner()
                + "/"
                + pr.name()
                + "/blob/"
                + anchoredCommit
                + "/"
                + encodedPath
                + ")**\n\n"
                + postedBody(note, readback);
        return new SummaryContent(
                note.copyId(), body, note.writeKey(), "<!-- hephaestus-diff-note-ck=" + note.writeKey() + " -->");
    }

    private CopyWriteResult postMixed(
            FeedbackTarget target,
            PrCoordinates pr,
            String commit,
            List<LineNote> notes,
            CopyReadback readback,
            CopyWriteFence fence,
            List<CopyReceipt> completed) {
        long workspace = target.item().workspaceId();
        for (int index = 0; index < notes.size(); index++) {
            var note = notes.get(index);
            SummaryContent content;
            String nodeId;
            try {
                content = note.anchor().endLine() == null ? fileContent(target, note, readback, commit) : null;
                nodeId = content == null
                        ? prNodeIdResolver.resolve(workspace, pr.owner(), pr.name(), pr.number())
                        : null;
            } catch (RuntimeException beforeWrite) {
                completed.add(CopyReceipt.notSent(note.copyId()));
                continue;
            }
            try {
                egressGuard.requireDeliveryAllowed("github.post-feedback-copy");
            } catch (OutboundEgressSuppressedException e) {
                var rest = notes.subList(index, notes.size());
                completed.addAll(notSent(rest));
                return CopyWriteResult.withheld(completed, deliveryKeys(rest));
            }
            if (!fence.beforeCreate(List.of(note.copyId()), List.copyOf(completed))) {
                completed.addAll(notSent(notes.subList(index, notes.size())));
                break;
            }
            CopyReceipt receipt;
            try {
                if (content != null) {
                    var handle = summaries.postSummary(target, content);
                    receipt = new CopyReceipt(
                            note.copyId(),
                            CopyDisposition.POSTED,
                            handle.externalRef(),
                            handle.url(),
                            true,
                            CopyForm.FILE_NOTE);
                } else {
                    receipt = postBatch(workspace, Objects.requireNonNull(nodeId), commit, List.of(note), readback)
                            .getFirst();
                }
            } catch (FeedbackNotSentException beforeWrite) {
                receipt = CopyReceipt.notSent(note.copyId());
            } catch (RuntimeException uncertain) {
                receipt = CopyReceipt.attempted(note.copyId());
            }
            completed.add(receipt);
            if (receipt.unconfirmed()) {
                completed.addAll(notSent(notes.subList(index + 1, notes.size())));
                break;
            }
        }
        return CopyWriteResult.of(completed);
    }

    @Override
    public @Nullable List<CopyReceipt> findPosted(
            FeedbackTarget target, List<LineNote> feedbackItems, CopyReadback readback) {
        if (feedbackItems.isEmpty()) {
            return List.of();
        }
        long scopeId = target.integration().workspaceId();
        if (commitOid(target) == null
                || gitHubProvider.isRateLimitCritical(scopeId)
                || feedbackItems.stream().anyMatch(item -> item.writeKey() == null)) {
            return null;
        }
        try {
            PrCoordinates pr =
                    GitHubSummaryWriter.parseSubjectExternalId(targets.subject(target, IntegrationKind.GITHUB));
            List<CopyReceipt> files = new ArrayList<>();
            var lines = feedbackItems.stream()
                    .filter(note -> note.anchor().endLine() != null)
                    .toList();
            for (LineNote note : feedbackItems)
                if (note.anchor().endLine() == null) {
                    var lookup = summaries.findExistingSummary(
                            target, fileContent(target, note, readback, Objects.requireNonNull(commitOid(target))));
                    if (lookup.presence() == LookupPresence.UNKNOWN) return null;
                    var handle = lookup.handle();
                    if (lookup.presence() == LookupPresence.FOUND && handle != null)
                        files.add(new CopyReceipt(
                                note.copyId(),
                                CopyDisposition.PRESERVED_EXISTING,
                                handle.externalRef(),
                                handle.url(),
                                null,
                                CopyForm.FILE_NOTE));
                }
            Map<String, Copy> copies =
                    lines.isEmpty() ? Map.of() : indexCopies(scopeId, pr, lines, readback, commitOid(target));
            List<CopyReceipt> found = new ArrayList<>(files);
            for (LineNote item : feedbackItems) {
                Copy copy = copies.get(item.writeKey());
                if (copy != null && copy.verified()) {
                    found.add(copy.preserved(item));
                }
            }
            return found;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The one review mutation for every candidate. Only a returned comment id acknowledges a
     * thread; any other outcome — no response, errors, an exception or a missing id — may still
     * have created it.
     */
    private List<CopyReceipt> postBatch(
            long scopeId,
            String prNodeId,
            @Nullable String commitOid,
            List<LineNote> candidates,
            CopyReadback readback) {
        List<Map<String, Object>> threads = new ArrayList<>(candidates.size());
        for (LineNote item : candidates) {
            threads.add(buildThread(item.anchor(), postedBody(item, readback)));
        }
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
                log.warn("GitHub review mutation returned no response: workspaceId={}", scopeId);
                return attempted(candidates);
            }
            gitHubProvider.trackRateLimit(scopeId, response);
            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                log.warn(
                        "GitHub review mutation returned errors: workspaceId={}, errors={}," + " threadCount={}",
                        scopeId,
                        response.getErrors(),
                        threads.size());
                return attempted(candidates);
            }
            List<Map<String, Object>> comments = response.field("addPullRequestReview.pullRequestReview.comments.nodes")
                    .getValue();
            log.info(
                    "Posted {} GitHub inline threads as single review: workspaceId={}, prNodeId={}",
                    threads.size(),
                    scopeId,
                    prNodeId);
            return postedSignals(comments, candidates);
        } catch (Exception e) {
            log.warn(
                    "GitHub review mutation outcome unknown: workspaceId={}, threadCount={}",
                    scopeId,
                    threads.size(),
                    e);
            return attempted(candidates);
        }
    }

    /**
     * Matches returned comment ids back to their feedback by the exact correlation tag in each
     * body: two pieces of feedback can anchor to one line, so coordinates alone cannot bind a
     * comment to its feedback.
     */
    private static List<CopyReceipt> postedSignals(
            @Nullable List<Map<String, Object>> comments, List<LineNote> candidates) {
        Map<String, SummaryHandle> commentByCk = new HashMap<>();
        if (comments != null) {
            for (Map<String, Object> comment : comments) {
                String id = (String) comment.get("id");
                String body = (String) comment.get("body");
                String ck = body == null ? null : parseDeliveryKey(body);
                if (id != null && !id.isBlank() && ck != null) {
                    commentByCk.putIfAbsent(ck, new SummaryHandle(id, (String) comment.get("url")));
                }
            }
        }
        List<CopyReceipt> signals = new ArrayList<>(candidates.size());
        for (LineNote item : candidates) {
            SummaryHandle comment = item.writeKey() == null ? null : commentByCk.get(item.writeKey());
            signals.add(
                    comment == null
                            ? CopyReceipt.attempted(item.copyId())
                            : new CopyReceipt(
                                    item.copyId(),
                                    CopyDisposition.POSTED,
                                    comment.externalRef(),
                                    comment.url(),
                                    true,
                                    CopyForm.LINE_NOTE));
        }
        return signals;
    }

    /**
     * The package's copies on the pull request, by delivery key, from a complete scan: a page
     * budget, a lost cursor, a missing connection or an unanswered {@code hasNextPage} fails the
     * scan instead of passing for absence. A key whose copies do not verify is kept as a conflict.
     */
    private Map<String, Copy> indexCopies(
            long scopeId, PrCoordinates pr, List<LineNote> items, CopyReadback readback, @Nullable String commitOid) {
        // Historical automatic threads carried only their key, so the marker cannot select them.
        String marker =
                readback == CopyReadback.SHARED ? null : items.getFirst().writeMarker();
        Map<String, LineNote> expected = new HashMap<>();
        for (LineNote item : items) {
            if (item.writeKey() != null) expected.putIfAbsent(item.writeKey(), item);
        }
        Map<String, Copy> copies = new LinkedHashMap<>();
        String after = null;
        try {
            for (int page = 1; ; page++) {
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

                if (response == null
                        || (response.getErrors() != null
                                && !response.getErrors().isEmpty())) {
                    throw new FeedbackDeliveryException("GitHub review-thread lookup returned no answer");
                }
                gitHubProvider.trackRateLimit(scopeId, response);

                Map<String, Object> connection =
                        response.field("repository.pullRequest.reviewThreads").getValue();
                if (connection == null
                        || !(connection.get("nodes") instanceof List<?> threads)
                        || !(connection.get("pageInfo") instanceof Map<?, ?> pageInfo)) {
                    throw new FeedbackDeliveryException("GitHub review-thread lookup was incomplete");
                }
                for (Object thread : threads) {
                    indexThread(thread, marker, expected, readback, commitOid, copies);
                }
                Object hasNextPage = pageInfo.get("hasNextPage");
                if (Boolean.FALSE.equals(hasNextPage)) {
                    return copies;
                }
                if (!Boolean.TRUE.equals(hasNextPage)
                        || page == MAX_THREAD_PAGES
                        || !(pageInfo.get("endCursor") instanceof String next)
                        || next.isBlank()
                        || next.equals(after)) {
                    throw new FeedbackDeliveryException("GitHub review-thread pagination did not reach its end");
                }
                after = next;
            }
        } catch (FeedbackDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new FeedbackDeliveryException("GitHub review-thread lookup was inconclusive", e);
        }
    }

    /**
     * Indexes one review thread under the correlation key of its first comment, if it is a package
     * copy.
     */
    private static void indexThread(
            @Nullable Object node,
            @Nullable String marker,
            Map<String, LineNote> expected,
            CopyReadback readback,
            @Nullable String commitOid,
            Map<String, Copy> copies) {
        if (!(node instanceof Map<?, ?> thread)
                || !(thread.get("id") instanceof String threadId)
                || !(thread.get("comments") instanceof Map<?, ?> connection)
                || !(connection.get("nodes") instanceof List<?> comments)) {
            throw new FeedbackDeliveryException("A GitHub review thread was not read completely");
        }
        if (comments.isEmpty()
                || !(comments.getFirst() instanceof Map<?, ?> firstComment)
                || !(firstComment.get("body") instanceof String body)
                || !(firstComment.get("id") instanceof String commentId)
                || commentId.isBlank()
                || threadId.isBlank()) {
            throw new FeedbackDeliveryException("A GitHub review comment was not read completely");
        }
        if (marker != null && !body.contains(marker)) return;
        String key = parseDeliveryKey(body);
        if (key == null) {
            return; // human thread or a bot note that carries no delivery key
        }
        LineNote item = expected.get(key);
        boolean verified = item != null && sameCopy(thread, firstComment, body, item, readback, commitOid);
        String url = firstComment.get("url") instanceof String commentUrl ? commentUrl : null;
        Copy previous = copies.get(key);
        if (verified && (previous == null || !previous.verified())) {
            copies.put(key, new Copy(true, threadId, commentId, url));
        } else if (!verified && previous == null) {
            copies.put(key, new Copy(false, threadId, commentId, url));
        }
    }

    /**
     * The viewer's own comment, with the exact body this channel posts, at the item's original
     * lines and commit.
     */
    private static boolean sameCopy(
            Map<?, ?> thread,
            Map<?, ?> comment,
            String body,
            LineNote item,
            CopyReadback readback,
            @Nullable String commitOid) {
        FeedbackAnchor diff = item.anchor();
        if (diff.endLine() == null || diff.side() == null) return false;
        Integer startLine = diff.startLine();
        Integer expectedStart = startLine != null && startLine < diff.endLine() ? startLine : null;
        String expectedSide = diff.side() == CitationSide.OLD ? "LEFT" : "RIGHT";
        return Boolean.TRUE.equals(comment.get("viewerDidAuthor"))
                && body.equals(postedBody(item, readback))
                && expectedSide.equals(thread.get("diffSide"))
                && (expectedStart == null || expectedSide.equals(thread.get("startDiffSide")))
                && diff.path().equals(comment.get("path"))
                && comment.get("originalLine") instanceof Number line
                && line.intValue() == diff.endLine()
                && Objects.equals(expectedStart, intOrNull(comment.get("originalStartLine")))
                && commitOid != null
                && comment.get("originalCommit") instanceof Map<?, ?> commit
                && commitOid.equals(commit.get("oid"));
    }

    private static @Nullable Integer intOrNull(@Nullable Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /**
     * The commit the reviewed work was captured at; blank means none, since an empty GitObjectID
     * fails the batch.
     */
    private static @Nullable String commitOid(FeedbackTarget target) {
        try {
            return ScmVersion.reviewedHead(target.version());
        } catch (IllegalArgumentException unavailable) {
            return null;
        }
    }

    private static List<CopyReceipt> notSent(List<LineNote> items) {
        return items.stream().map(item -> CopyReceipt.notSent(item.copyId())).toList();
    }

    private static List<CopyReceipt> attempted(List<LineNote> items) {
        return items.stream().map(item -> CopyReceipt.attempted(item.copyId())).toList();
    }

    private static List<UUID> deliveryKeys(List<LineNote> items) {
        return items.stream().map(LineNote::copyId).filter(Objects::nonNull).toList();
    }

    @Nullable
    private static String parseDeliveryKey(String body) {
        Matcher m = CK_TAG.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /**
     * The thread body: the sealed text, the package marker and the correlation tag. Historical
     * automatic threads were posted without the shared marker, and are rendered that way again.
     */
    private static String postedBody(LineNote item, CopyReadback readback) {
        String body = readback == CopyReadback.SHARED
                        || (item.writeMarker() == null || item.body().contains(item.writeMarker()))
                ? item.body()
                : item.body() + "\n\n" + item.writeMarker();
        String key = item.writeKey();
        return key == null || key.isBlank() ? body : body + "\n<!-- hephaestus-diff-note-ck=" + key + " -->";
    }

    /**
     * A copy of a package item; {@code verified} false when it carries the key but not the item.
     */
    private record Copy(
            boolean verified,
            String threadId,
            String commentId,
            @Nullable String url) {
        CopyReceipt preserved(LineNote item) {
            return new CopyReceipt(
                    item.copyId(), CopyDisposition.PRESERVED_EXISTING, commentId, url, null, CopyForm.LINE_NOTE);
        }
    }

    /** Builds a GitHub review-thread payload from a {@link FeedbackAnchor}. */
    private static Map<String, Object> buildThread(FeedbackAnchor diff, String body) {
        Map<String, Object> thread = new HashMap<>();
        thread.put("path", diff.path());
        thread.put("body", body);

        Integer startLine = diff.startLine();
        int endLine = Objects.requireNonNull(diff.endLine(), "A line note requires an end line");
        boolean isMultiLine = startLine != null && startLine < endLine;
        if (isMultiLine) {
            thread.put("startLine", startLine);
            thread.put("line", endLine);
            thread.put("side", diff.side() == CitationSide.OLD ? "LEFT" : "RIGHT");
            thread.put("startSide", diff.side() == CitationSide.OLD ? "LEFT" : "RIGHT");
        } else {
            thread.put("line", endLine);
            thread.put("side", diff.side() == CitationSide.OLD ? "LEFT" : "RIGHT");
        }
        return thread;
    }

    @Override
    public List<CopyUpdateReceipt> retract(FeedbackTarget target, List<CopyReceipt> copies) {
        targets.subject(target, kind());
        long workspaceId = target.item().workspaceId();
        List<CopyUpdateReceipt> outcomes = new ArrayList<>();
        for (CopyReceipt copy : copies) {
            String externalRef = copy.externalRef();
            if (externalRef == null || externalRef.isBlank()) {
                outcomes.add(
                        CopyUpdateReceipt.transientFailure(copy.copyId(), "Copy has no recorded provider reference"));
                continue;
            }
            try {
                ClientGraphQlResponse read = gitHubProvider
                        .forScope(workspaceId)
                        .document("query ReadPracticeCopy($id:ID!){node(id:$id){__typename"
                                + " ... on PullRequestReviewComment{id body"
                                + " viewerDidAuthor} ... on IssueComment{id body"
                                + " viewerDidAuthor}}}")
                        .variable("id", externalRef)
                        .execute()
                        .block(GRAPHQL_TIMEOUT);
                if (read == null || !read.getErrors().isEmpty()) {
                    outcomes.add(CopyUpdateReceipt.transientFailure(copy.copyId(), "Existing copy could not be read"));
                    continue;
                }
                Object node = read.field("node").getValue();
                if (node == null) {
                    outcomes.add(CopyUpdateReceipt.gone(copy.copyId(), "The provider copy no longer exists"));
                    continue;
                }
                if (!(node instanceof Map<?, ?> note)
                        || !externalRef.equals(note.get("id"))
                        || !("IssueComment".equals(note.get("__typename"))
                                || "PullRequestReviewComment".equals(note.get("__typename")))
                        || !(note.get("body") instanceof String original)
                        || !Boolean.TRUE.equals(note.get("viewerDidAuthor"))) {
                    outcomes.add(new CopyUpdateReceipt(
                            copy.copyId(),
                            CopyUpdateKind.UNSUPPORTED,
                            externalRef,
                            copy.externalUrl(),
                            "Existing copy authority could not be verified"));
                    continue;
                }
                egressGuard.requireDeliveryAllowed("github.correct-feedback-copy");
                boolean fileCopy = "IssueComment".equals(note.get("__typename"));
                String resultPath = fileCopy
                        ? "updateIssueComment.issueComment"
                        : "updatePullRequestReviewComment.pullRequestReviewComment";
                ClientGraphQlResponse response = gitHubProvider
                        .forScope(workspaceId)
                        .document(
                                fileCopy
                                        ? "mutation"
                                                + " CorrectPracticeFile($id:ID!,$body:String!){updateIssueComment(input:{id:$id,body:$body}){issueComment{id"
                                                + " url}}}"
                                        : "mutation"
                                                + " CorrectPracticeLine($id:ID!,$body:String!){updatePullRequestReviewComment(input:{pullRequestReviewCommentId:$id,body:$body}){pullRequestReviewComment{id"
                                                + " url}}}")
                        .variable("id", externalRef)
                        .variable("body", ScmFeedbackFormatter.withdrawnCopyBody(original))
                        .execute()
                        .block(GRAPHQL_TIMEOUT);
                if (response == null || !response.getErrors().isEmpty()) {
                    outcomes.add(CopyUpdateReceipt.transientFailure(
                            copy.copyId(), "Provider did not confirm the correction"));
                    continue;
                }
                String id = response.field(resultPath + ".id").getValue();
                String url = response.field(resultPath + ".url").getValue();
                outcomes.add(
                        id == null || !id.equals(externalRef)
                                ? CopyUpdateReceipt.transientFailure(
                                        copy.copyId(), "Provider did not confirm the existing copy")
                                : new CopyUpdateReceipt(copy.copyId(), CopyUpdateKind.EDITED, id, url, null));
            } catch (OutboundEgressSuppressedException e) {
                throw e;
            } catch (RuntimeException e) {
                outcomes.add(CopyUpdateReceipt.transientFailure(copy.copyId(), "Provider correction failed"));
            }
        }
        return List.copyOf(outcomes);
    }
}
